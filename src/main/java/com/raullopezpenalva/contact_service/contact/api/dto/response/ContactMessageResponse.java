package com.raullopezpenalva.contact_service.contact.api.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

public class ContactMessageResponse {
    
    private UUID id;
    private LocalDateTime createdAt;

    public ContactMessageResponse() {
    }

    public ContactMessageResponse(UUID id, LocalDateTime createdAt) {
        this.id = id;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
