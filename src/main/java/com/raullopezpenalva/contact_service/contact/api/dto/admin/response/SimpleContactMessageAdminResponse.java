package com.raullopezpenalva.contact_service.contact.api.dto.admin.response;

import java.time.LocalDateTime;
import java.util.UUID;

import com.raullopezpenalva.contact_service.contact.domain.model.ContactMessageStatus;

public class SimpleContactMessageAdminResponse {
    
    private UUID id;
    private LocalDateTime createdAt;
    private String email;
    private String subject;
    private ContactMessageStatus status;
    private String sourceIp;

    public SimpleContactMessageAdminResponse() {
    }

    // Constructor for SimpleContactMessageAdminResponse
    /**
     * Constructs a new {@code SimpleContactMessageAdminResponse} with the specified details.
     *
     * @param id        the unique identifier of the contact message
     * @param createdAt the timestamp when the contact message was created
     * @param email     the email address associated with the contact message
     * @param subject   the subject of the contact message
     * @param status    the status of the contact message
     * @param sourceIp  the source IP address from which the contact message was sent
     */
    public SimpleContactMessageAdminResponse(UUID id, LocalDateTime createdAt, String email, String subject, ContactMessageStatus status, String sourceIp) {
        this.id = id;
        this.createdAt = createdAt;
        this.email = email;
        this.subject = subject;
        this.status = status;
        this.sourceIp = sourceIp;
    }

    // Getters and Setters
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

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public ContactMessageStatus getStatus() {
        return status;
    }

    public void setStatus(ContactMessageStatus status) {
        this.status = status;
    }

    public String getSourceIp() {
        return sourceIp;
    }

    public void setSourceIp(String sourceIp) {
        this.sourceIp = sourceIp;
    }
}