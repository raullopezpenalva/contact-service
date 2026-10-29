package com.raullopezpenalva.contact_service.modules.platform.websecurity.infrastructure.http;

import java.util.UUID;
import jakarta.servlet.http.HttpServletRequest;

/** Server-generated correlation and bounded route templates, never caller diagnostics. */
public final class RequestMetadata {
    private static final String REQUEST_ID = RequestMetadata.class.getName() + ".id";
    private RequestMetadata() {}

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
                || path.equals("/actuator/health")) return path;
        if (path.matches("/api/v1/admin/contact/messages/[^/]+/status"))
            return "/api/v1/admin/contact/messages/{id}/status";
        if (path.matches("/api/v1/admin/contact/messages/[^/]+"))
            return "/api/v1/admin/contact/messages/{id}";
        return "/";
    }
}
