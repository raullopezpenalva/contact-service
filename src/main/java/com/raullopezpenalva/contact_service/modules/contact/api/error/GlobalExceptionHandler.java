package com.raullopezpenalva.contact_service.modules.contact.api.error;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import com.raullopezpenalva.contact_service.modules.contact.application.exception.ResourceNotFoundException;
import com.raullopezpenalva.contact_service.modules.platform.websecurity.infrastructure.http.RequestMetadata;
import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final Set<String> FIELDS = Set.of("email", "subject", "content", "status", "adminNote");
    private static final Set<String> PARAMETERS = Set.of("id", "status", "page", "size");

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        var details = ex.getBindingResult().getFieldErrors().stream()
            .map(error -> new FieldErrorItem(FIELDS.contains(error.getField()) ? error.getField() : "requestBody",
                switch (error.getCode() == null ? "" : error.getCode()) {
                    case "NotNull", "NotBlank" -> "Field is required";
                    case "Size" -> "Invalid field length";
                    case "Email" -> "Invalid email address";
                    default -> "Invalid field value";
                })).distinct().toList();
        return error(ex, request, HttpStatus.BAD_REQUEST, "Validation failed for one or more fields", details);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleMalformedJson(HttpMessageNotReadableException ex, HttpServletRequest request) {
        return error(ex, request, HttpStatus.BAD_REQUEST, "Malformed JSON request", List.of());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        String name = PARAMETERS.contains(ex.getName()) ? ex.getName() : "requestParameter";
        return error(ex, request, HttpStatus.BAD_REQUEST, "Invalid request parameter",
            List.of(new FieldErrorItem(name, "Invalid parameter value")));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleResourceNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        return error(ex, request, HttpStatus.NOT_FOUND, "The specified resource was not found", List.of());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleMissingRoute(NoResourceFoundException ex, HttpServletRequest request) {
        return error(ex, request, HttpStatus.NOT_FOUND, "The specified resource was not found", List.of());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiError> handleUnsupportedMedia(HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {
        return error(ex, request, HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported media type", List.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGenericException(Exception ex, HttpServletRequest request) {
        return error(ex, request, HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", List.of());
    }

    private ResponseEntity<ApiError> error(Exception ex, HttpServletRequest request, HttpStatus status,
            String message, List<FieldErrorItem> details) {
        String id = RequestMetadata.requestId(request);
        // Exception messages/causes and rejected values may contain secrets or user data.
        if (status.is5xxServerError()) log.error("Request failed requestId={}, status={}, errorType={}", id, status.value(), ex.getClass().getSimpleName());
        else log.warn("Request rejected requestId={}, status={}, errorType={}", id, status.value(), ex.getClass().getSimpleName());
        return ResponseEntity.status(status).header("X-Request-ID", id).header("Cache-Control", "no-store")
            .body(new ApiError(Instant.now().toString(), status.value(), status.getReasonPhrase(),
                message, RequestMetadata.path(request), details, id));
    }
}
