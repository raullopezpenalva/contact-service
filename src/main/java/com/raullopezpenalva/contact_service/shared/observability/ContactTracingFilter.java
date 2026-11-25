package com.raullopezpenalva.contact_service.shared.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class ContactTracingFilter extends OncePerRequestFilter {
    private final ContactTracing tracing;
    public ContactTracingFilter(ContactTracing tracing) { this.tracing = tracing; }
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod()) || !"/api/v1/contact/messages".equals(request.getRequestURI());
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        // Never extract traceparent/tracestate/baggage, even for direct backend access.
        try (var scope = tracing.root()) {
            try { chain.doFilter(request, response); }
            catch (ServletException | IOException | RuntimeException | Error ex) { scope.failed(); throw ex; }
            finally { if (response.getStatus() >= 500) scope.failed(); }
        }
    }
}
