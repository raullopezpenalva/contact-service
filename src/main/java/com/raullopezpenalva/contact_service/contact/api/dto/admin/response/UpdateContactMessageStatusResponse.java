package com.raullopezpenalva.contact_service.contact.api.dto.admin.response;

import java.time.LocalDateTime;
import java.util.UUID;

import com.raullopezpenalva.contact_service.contact.domain.model.ContactMessageStatus;

public class UpdateContactMessageStatusResponse {
    private UUID id;
    private ContactMessageStatus status;
    private LocalDateTime updatedAt;

    public UpdateContactMessageStatusResponse() {
    }

    public UpdateContactMessageStatusResponse(UUID id, ContactMessageStatus status, LocalDateTime updatedAt) {
        this.id = id;
        this.status = status;
        this.updatedAt = updatedAt;
    }

    // Getters and Setters

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public ContactMessageStatus getStatus() {
        return status;
    }

    public void setStatus(ContactMessageStatus status) {
        this.status = status;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}