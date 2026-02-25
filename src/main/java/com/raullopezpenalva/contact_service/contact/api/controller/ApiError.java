package com.raullopezpenalva.contact_service.contact.api.controller;

import java.util.List;

public record ApiError(
    String timestamp,
    int status,
    String error,
    String message,
    String path,
    List<FieldErrorItem> details

) {}
