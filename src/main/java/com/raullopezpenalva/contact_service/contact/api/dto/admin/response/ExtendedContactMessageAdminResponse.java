package com.raullopezpenalva.contact_service.contact.api.dto.admin.response;

import java.time.LocalDateTime;
import java.util.UUID;

import com.raullopezpenalva.contact_service.contact.domain.model.ContactMessageStatus;

public class ExtendedContactMessageAdminResponse {
    private UUID id;
    private String email;
    private String subject;
    private String content;
    private ContactMessageStatus status;
    private String adminNote;
    private String sourceIp;
    private String userAgent;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public ExtendedContactMessageAdminResponse() {
    }

    public ExtendedContactMessageAdminResponse(UUID id, String email, String subject, String content, ContactMessageStatus status, String adminNote, String sourceIp, String userAgent, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.email = email;
        this.subject = subject;
        this.content = content;
        this.status = status;
        this.adminNote = adminNote;
        this.sourceIp = sourceIp;
        this.userAgent = userAgent;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // Getters and Setters

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
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

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

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

    public String getSourceIp() {
        return sourceIp;
    }

    public void setSourceIp(String sourceIp) {
        this.sourceIp = sourceIp;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}