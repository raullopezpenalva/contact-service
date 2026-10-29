package com.raullopezpenalva.contact_service.modules.platform.websecurity.infrastructure.filter;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.autoconfigure.observation.ObservationProperties.Http;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.raullopezpenalva.contact_service.modules.platform.websecurity.application.RateLimitService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class ContactRateLimitFilter extends OncePerRequestFilter {
    
    private static final Logger log = LoggerFactory.getLogger(ContactRateLimitFilter.class);

    private static final String CONTACT_MESSAGES_PATH = "/api/v1/contact/messages";

    private final RateLimitService rateLimitService;

    public ContactRateLimitFilter(RateLimitService rateLimitService) {
        this.rateLimitService = rateLimitService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        return !HttpMethod.POST.matches(request.getMethod()) || !CONTACT_MESSAGES_PATH.equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {

        String clientIp = resolveClientIp(request);
        boolean allowed = rateLimitService.allowRequest(clientIp);

        if (!allowed) {
            log.warn(
                "Rate limit exceeded for ip={}, method={}, path={}",
                clientIp,
                request.getMethod(),
                request.getRequestURI()
            );
            
            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("""
                    {
                        "error": "Too many requests",
                        "message": "You have exceeded the maximum number of allowed requests. Please try again later."
                    }
            """);
            return;

        }
        filterChain.doFilter(request, response);
    }
    
    
    private String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");

        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
            
        }
        return request.getRemoteAddr();
    }
    
}
