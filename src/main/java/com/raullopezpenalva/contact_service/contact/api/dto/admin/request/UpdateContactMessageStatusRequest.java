package com.raullopezpenalva.contact_service.contact.api.dto.admin.request;

import com.raullopezpenalva.contact_service.contact.domain.model.ContactMessageStatus;

import jakarta.validation.constraints.NotNull;

public class UpdateContactMessageStatusRequest {

    @NotNull
    private ContactMessageStatus status;
    
    private String adminNote;

    public UpdateContactMessageStatusRequest() {
    }

    public UpdateContactMessageStatusRequest(ContactMessageStatus status, String adminNote) {
        this.status = status;
        this.adminNote = adminNote;
    }

    // Getters and Setters

    public ContactMessageStatus getStatus() {
        return status;
    }

    public void setStatus(ContactMessageStatus status) {
        this.status = status;
    }

    public String getAdminNote() {
        return adminNote;
    }

    public void setAdminNote(String adminNote) {
        this.adminNote = adminNote;
    }
}
