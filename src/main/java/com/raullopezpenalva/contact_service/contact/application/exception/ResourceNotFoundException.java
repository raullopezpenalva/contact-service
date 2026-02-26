package com.raullopezpenalva.contact_service.contact.application.exception;

public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
    
}
