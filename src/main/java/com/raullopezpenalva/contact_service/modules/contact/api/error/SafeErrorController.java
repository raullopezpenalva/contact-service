package com.raullopezpenalva.contact_service.modules.contact.api.error;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.raullopezpenalva.contact_service.modules.platform.websecurity.infrastructure.http.SecurityErrorWriter;

/** Sanitizes container error dispatches; direct /error requests remain denied. */
@RestController
public class SafeErrorController implements ErrorController {
    @RequestMapping("/error")
    public void error(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Object code = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        int status = code instanceof Integer value && value >= 400 && value <= 599 ? value : 500;
        // Only known statuses enter the response writer; never reflect container diagnostics.
        String message = switch (status) {
            case 400 -> "Invalid request";
            case 401 -> "Authentication required";
            case 403 -> "Access denied";
            case 404 -> "The specified resource was not found";
            case 405 -> "Method not allowed";
            case 413 -> "Request too large";
            case 415 -> "Unsupported media type";
            case 429 -> "Too many requests";
            default -> "An unexpected error occurred";
        };
        if (org.springframework.http.HttpStatus.resolve(status) == null) status = 500;
        SecurityErrorWriter.write(request, response, status, message);
    }
}
