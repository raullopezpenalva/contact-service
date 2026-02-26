package com.raullopezpenalva.contact_service.contact.api.error;

import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.raullopezpenalva.contact_service.contact.application.exception.ResourceNotFoundException;

import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // Handles validation errors for @Valid annotated request bodies
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(
        MethodArgumentNotValidException ex,
        HttpServletRequest request
    ) {
        var details = ex.getBindingResult().getFieldErrors().stream()
            .map(error -> new FieldErrorItem(
                error.getField(),
                error.getDefaultMessage()
            ))
            .toList();
        
        var body = new ApiError(
            Instant.now().toString(),
            HttpStatus.BAD_REQUEST.value(),
            HttpStatus.BAD_REQUEST.getReasonPhrase(),
            "Validation failed for one or more fields",
            request.getRequestURI(),
            details
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }
    
    // Handles malformed JSON request bodies
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleMalformedJson(
        HttpMessageNotReadableException ex,
        HttpServletRequest request
    ) {
        var details = List.of(new FieldErrorItem(
            "requestBody",
            "Malformed JSON: " + ex.getMostSpecificCause().getMessage()
        ));
        var body = new ApiError(
            Instant.now().toString(),
            HttpStatus.BAD_REQUEST.value(),
            HttpStatus.BAD_REQUEST.getReasonPhrase(),
            "Malformed JSON request",
            request.getRequestURI(),
            details
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    // Handles type mismatch errors for request parameters
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(
        MethodArgumentTypeMismatchException ex,
        HttpServletRequest request
    ) {
        var details = List.of(new FieldErrorItem(
            ex.getName(),
            ex.getName().equals("status")
                ? "Invalid status value: " + ex.getValue() + ". Expected values are: NEW, READ, ARCHIVED, SPAM, NOTIFICATION_FAILED"
                : (ex.getName().equals("page") || ex.getName().equals("size"))
                    ? "Invalid pagination parameter: " + ex.getValue() + ". Expected a non-negative integer."
                    : "Invalid value: " + ex.getValue()
        ));
        var body = new ApiError(
            Instant.now().toString(),
            HttpStatus.BAD_REQUEST.value(),
            HttpStatus.BAD_REQUEST.getReasonPhrase(),
            "Status Type mismatch error",
            request.getRequestURI(),
            details
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    // Handles resource not found exceptions
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleResourceNotFound(
        ResourceNotFoundException ex,
        HttpServletRequest request
    ) {
        var details = List.of(new FieldErrorItem(
            "id",
            "The specified resource was not found"
        ));
        ApiError body = new ApiError(
            Instant.now().toString(),
            HttpStatus.NOT_FOUND.value(),
            HttpStatus.NOT_FOUND.getReasonPhrase(),
            ex.getMessage(),
            request.getRequestURI(),
            details
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    // Handles all other uncaught exceptions
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGenericException(
        Exception ex,
        HttpServletRequest request
    ) {
        var details = List.of(new FieldErrorItem(
            "error",
            ex.getMessage()
        ));
        var body = new ApiError(
            Instant.now().toString(),
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
            "An unexpected error occurred",
            request.getRequestURI(),
            details
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}