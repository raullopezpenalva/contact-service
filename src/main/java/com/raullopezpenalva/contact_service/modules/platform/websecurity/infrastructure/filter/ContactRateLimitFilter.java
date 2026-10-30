package com.raullopezpenalva.contact_service.modules.platform.websecurity.infrastructure.filter;

import java.io.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import com.raullopezpenalva.contact_service.modules.platform.websecurity.application.RateLimitService;
import com.raullopezpenalva.contact_service.modules.platform.websecurity.infrastructure.http.RequestMetadata;
import com.raullopezpenalva.contact_service.modules.platform.websecurity.infrastructure.http.SecurityErrorWriter;
import com.raullopezpenalva.contact_service.modules.platform.websecurity.infrastructure.http.TrustedClientAddress;
import jakarta.servlet.*;
import jakarta.servlet.http.*;

@Component
public class ContactRateLimitFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(ContactRateLimitFilter.class);
    private final RateLimitService limiter;
    @Value("${app.security.jwt.issuer:}") private String issuer="";
    @Value("${app.security.rate-limit.login.max-requests:10}") private int loginLimit=10;
    @Value("${app.security.rate-limit.password.max-requests:5}") private int passwordLimit=5;
    @Value("${app.security.rate-limit.auth.max-requests:60}") private int authLimit=60;
    @Value("${app.security.rate-limit.contactrequest.max-requests:5}") private int publicLimit;
    @Value("${app.security.rate-limit.contactrequest.window-seconds:3600}") private long publicWindow;
    @Value("${app.security.rate-limit.admin.max-requests:60}") private int adminLimit;
    @Value("${app.security.rate-limit.admin.window-seconds:60}") private long adminWindow;
    @Value("${app.security.max-request-bytes:16384}") private int maxBytes;
    @Value("${app.security.trusted-proxies:}") private String trustedProxies;
    public ContactRateLimitFilter(RateLimitService limiter) { this.limiter = limiter; }

    @jakarta.annotation.PostConstruct
    void validateConfiguration() {
        if (publicLimit < 1 || adminLimit < 1 || loginLimit < 1 || passwordLimit < 1 || authLimit < 1 || publicWindow < 1 || adminWindow < 1
                || publicWindow > 31536000 || adminWindow > 31536000
                || maxBytes < 1 || maxBytes > 1048576) {
            throw new IllegalArgumentException("Invalid security limits: use positive quotas, windows up to one year and bodies up to 1 MiB");
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        String previousId = MDC.get("requestId");
        String id = RequestMetadata.requestId(request);
        response.setHeader("X-Request-ID", id);
        MDC.put("requestId", id);
        String client = TrustedClientAddress.resolve(request, trustedProxies);
        request.setAttribute(ContactRateLimitFilter.class.getName() + ".originalPeer", request.getRemoteAddr());
        HttpServletRequest resolved = new HttpServletRequestWrapper(request) {
            @Override public String getRemoteAddr() { return client; }
        };
        try { filterResolved(resolved, response, chain); }
        finally {
            if (previousId == null) MDC.remove("requestId");
            else MDC.put("requestId", previousId);
        }
    }

    private void filterResolved(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        boolean admin = request.getServletPath().startsWith("/api/v1/admin/")
            || request.getRequestURI().startsWith(request.getContextPath() + "/api/v1/admin/");
        boolean contact = "POST".equals(request.getMethod())
            && (request.getContextPath() + "/api/v1/contact/messages").equals(request.getRequestURI());
        String path=request.getRequestURI().substring(request.getContextPath().length());
        boolean auth=path.startsWith("/api/v1/auth/");
        if(auth) {response.setHeader("Cache-Control","no-store");response.setHeader("Pragma","no-cache");}
        if (!admin && !contact && !auth) { chain.doFilter(request, response); return; }
        String scope=admin?"admin":contact?"contact":path.equals("/api/v1/auth/login")?"login":
            path.equals("/api/v1/auth/change-password")?"password":"auth";
        int limit=admin?adminLimit:contact?publicLimit:scope.equals("login")?loginLimit:scope.equals("password")?passwordLimit:authLimit;
        long window=admin?adminWindow:contact?publicWindow:60;
        String ip = request.getRemoteAddr();
        if (!limiter.allowRequest(scope + ":" + ip, limit, window)) {
            log.warn("Rate limit exceeded requestId={}, scope={}", RequestMetadata.requestId(request), scope);
            response.setHeader("Retry-After", Long.toString(window));
            SecurityErrorWriter.write(request, response, 429, "Too many requests"); return;
        }
        if((admin || auth) && request.getHeader("Origin")!=null) {
            var origins=java.util.Collections.list(request.getHeaders("Origin"));
            String expected="";
            try {var uri=java.net.URI.create(issuer);expected=new java.net.URI("https",null,uri.getHost(),uri.getPort(),null,null,null).toString();}
            catch(Exception invalid) { /* Configuration is also validated at startup. */ }
            if(origins.size()!=1 || !origins.getFirst().equals(expected)) {
                SecurityErrorWriter.write(request,response,403,"Access denied");return;
            }
        }
        if (contact || auth) {
            int bodyLimit=auth?8192:maxBytes;
            if (request.getContentLengthLong() > bodyLimit) {
                SecurityErrorWriter.write(request, response, 413, "Request too large"); return;
            }
            byte[] body = request.getInputStream().readNBytes(bodyLimit + 1);
            if (body.length > bodyLimit) { SecurityErrorWriter.write(request, response, 413, "Request too large"); return; }
            ByteArrayInputStream input = new ByteArrayInputStream(body);
            HttpServletRequest wrapped = new HttpServletRequestWrapper(request) {
                @Override public ServletInputStream getInputStream() {
                    return new ServletInputStream() {
                        public int read() { return input.read(); }
                        public boolean isFinished() { return input.available() == 0; }
                        public boolean isReady() { return true; }
                        public void setReadListener(ReadListener listener) { throw new UnsupportedOperationException(); }
                    };
                }
                @Override public BufferedReader getReader() {
                    return new BufferedReader(new InputStreamReader(getInputStream(), java.nio.charset.StandardCharsets.UTF_8));
                }
            };
            chain.doFilter(wrapped, response);
        } else chain.doFilter(request, response);
    }
}
