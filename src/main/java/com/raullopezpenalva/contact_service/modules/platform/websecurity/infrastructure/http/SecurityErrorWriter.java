package com.raullopezpenalva.contact_service.modules.platform.websecurity.infrastructure.http;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;

/** Implements the public JSON contract without depending on contact-owned DTOs. */
public final class SecurityErrorWriter {
    private static final ObjectMapper JSON = new ObjectMapper();
    private SecurityErrorWriter() {}

    public static void write(HttpServletRequest request, HttpServletResponse response,
            int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("X-Request-ID", RequestMetadata.requestId(request));
        JSON.writeValue(response.getWriter(), new ErrorBody(Instant.now().toString(), status,
            HttpStatus.valueOf(status).getReasonPhrase(), message, RequestMetadata.path(request),
            List.of(), RequestMetadata.requestId(request)));
    }

    private record ErrorBody(String timestamp, int status, String error, String message,
            String path, List<Object> details, String requestId) {}
}
