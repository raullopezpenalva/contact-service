package com.raullopezpenalva.contact_service.contact.model;

import jakarta.annotation.Nonnull;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "contact_messages")

public class ContactMessage {

    public ContactMessage() {
        this.status = ContactMessageStatus.NEW;
        this.notificationAttempts = 0;
    }

    @Id
    @GeneratedValue
    @Nonnull
    private UUID id;

    @NotBlank
    @Email
    @Column(nullable = false, unique = false)
    @Size(min = 5, max = 254)
    private String email;

    @NotBlank
    @Column(nullable = false, unique = false)
    @Size(min = 3, max = 120)
    private String subject;

    @NotBlank
    @Column(nullable = false, unique = false, columnDefinition = "TEXT")
    @Size(min = 10, max = 4000)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ContactMessageStatus status;

    @Column(nullable = true, columnDefinition = "TEXT")
    @Size(max = 4000)
    private String adminNote;

    @Column(nullable = true)
    private String sourceIP;

    @Column(nullable = true, columnDefinition = "TEXT")
    @Size(max = 4000)
    private String userAgent;

    @Column(nullable = false)
    private Integer notificationAttempts;

    @CreationTimestamp
    @Column(nullable = true)
    private LocalDateTime lastNotifiedAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime updatedAt;

    @Column(nullable = true)
    private String contentHash;

    // Getters and Setters

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getSubject() {
        return subject;
    }

    public String getContent() {
        return content;
    }

    public ContactMessageStatus getStatus() {
        return status;
    }

    public String getAdminNote() {
        return adminNote;
    }

    public String getSourceIP() {
        return sourceIP;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public Integer getNotificationAttempts() {
        return notificationAttempts;
    }

    public LocalDateTime getLastNotifiedAt() {
        return lastNotifiedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public String getContentHash() {
        return contentHash;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public void setStatus(ContactMessageStatus status) {
        this.status = status;
    }

    public void setAdminNote(String adminNote) {
        this.adminNote = adminNote;
    }

    public void setSourceIP(String sourceIP) {
        this.sourceIP = sourceIP;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public void setNotificationAttempts(Integer notificationAttempts) {
        this.notificationAttempts = notificationAttempts;
    }

    public void setLastNotifiedAt(LocalDateTime lastNotifiedAt) {
        this.lastNotifiedAt = lastNotifiedAt;
    }

    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }

}
