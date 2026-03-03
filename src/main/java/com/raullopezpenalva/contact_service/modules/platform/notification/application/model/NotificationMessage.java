package com.raullopezpenalva.contact_service.modules.platform.notification.application.model;

import java.time.Instant;
import java.util.UUID;

public class NotificationMessage {
    
    private UUID eventId;
    private Instant ocurredAt;
    private String email;
    private String subject;
    private String message;

    public NotificationMessage() {
    }

    public NotificationMessage(UUID eventId, Instant ocurredAt, String email, String subject, String message) {
        this.eventId = eventId;
        this.ocurredAt = ocurredAt;
        this.email = email;
        this.subject = subject;
        this.message = message;
    }

    public UUID getEventId() {
        return eventId;
    }

    public Instant getOcurredAt() {
        return ocurredAt;
    }

    public String getEmail() {
        return email;
    }

    public String getSubject() {
        return subject;
    }

    public String getMessage() {
        return message;
    }

    public void setEventId(UUID eventId) {
        this.eventId = eventId;
    }

    public void setOcurredAt(Instant ocurredAt) {
        this.ocurredAt = ocurredAt;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
