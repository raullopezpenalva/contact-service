package com.raullopezpenalva.contact_service.modules.platform.websecurity.infrastructure.http;

import java.util.UUID;
import jakarta.servlet.http.HttpServletRequest;

/** Server-generated correlation and bounded route templates, never caller diagnostics. */
public final class RequestMetadata {
    private static final String REQUEST_ID = RequestMetadata.class.getName() + ".id";
    private RequestMetadata() {}

    /** Only the immediate trusted proxy can supply nginx's generated 128-bit ID. */
    public static void initialize(HttpServletRequest request, String trustedPeers) {
        if (request.getAttribute(REQUEST_ID) != null) return;
        var values = java.util.Collections.list(request.getHeaders("X-Request-ID"));
        if (TrustedClientAddress.isTrusted(request, trustedPeers) && values.size() == 1
                && values.getFirst().matches("[a-f0-9]{32}")) {
            request.setAttribute(REQUEST_ID, values.getFirst());
        }
    }

    public static String requestId(HttpServletRequest request) {
        Object value = request.getAttribute(REQUEST_ID);
        if (value == null) {
            value = UUID.randomUUID().toString();
            request.setAttribute(REQUEST_ID, value);
        }
        return value.toString();
    }

    public static String path(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String path = uri.substring(Math.min(request.getContextPath().length(), uri.length()));
        if (path.equals("/api/v1/contact/messages") || path.equals("/api/v1/admin/contact/messages")
                || path.equals("/actuator/health")
                || java.util.Set.of("/api/v1/auth/login","/api/v1/auth/me","/api/v1/auth/logout","/api/v1/auth/change-password").contains(path)) return path;
        if (path.matches("/api/v1/admin/contact/messages/[^/]+/status"))
            return "/api/v1/admin/contact/messages/{id}/status";
        if (path.matches("/api/v1/admin/contact/messages/[^/]+"))
            return "/api/v1/admin/contact/messages/{id}";
        return "/";
    }
}
