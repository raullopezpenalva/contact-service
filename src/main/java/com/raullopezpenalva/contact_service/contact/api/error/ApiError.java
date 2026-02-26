package com.raullopezpenalva.contact_service.contact.api.error;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "API Error Response", description = "Standard structure for API error responses")
public record ApiError(
    String timestamp,
    int status,
    String error,
    String message,
    String path,
    List<FieldErrorItem> details

) {}
