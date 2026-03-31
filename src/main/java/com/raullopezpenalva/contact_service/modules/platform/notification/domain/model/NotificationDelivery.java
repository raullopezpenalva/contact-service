package com.raullopezpenalva.contact_service.modules.platform.notification.domain.model;

import com.raullopezpenalva.contact_service.modules.platform.notification.domain.model.NotificationChannel;
import com.raullopezpenalva.contact_service.modules.platform.notification.domain.model.NotificationStatus;

import java.time.Instant;
import java.util.UUID;

public class NotificationDelivery {
    
    private final UUID id;
    private final UUID eventId;
    private final String eventType;
    private final NotificationChannel channel;

    private NotificationStatus status;
    private int attempts;
    private String lastError;
    private final String payloadSnapshot;

    private final Instant createdAt;
    private Instant updatedAt;

    // Constructor (factory)

    private NotificationDelivery(
        UUID id,
        UUID eventId,
        String eventType,
        NotificationChannel channel,
        NotificationStatus status,
        int attempts,
        String lastError,
        String payloadSnapshot,
        Instant createdAt,
        Instant updatedAt
    ) {
        this.id = id;
        this.eventId = eventId;
        this.eventType = eventType;
        this.channel = channel;
        this.status = status;
        this.attempts = attempts;
        this.lastError = lastError;
        this.payloadSnapshot = payloadSnapshot;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // Factory method to create a new delivery
    public static NotificationDelivery createNew(UUID eventId, String eventType, NotificationChannel channel, String payloadSnapshot) {
        return new NotificationDelivery(
            UUID.randomUUID(),
            eventId,
            eventType,
            channel,
            NotificationStatus.PENDING,
            0,
            null,
            payloadSnapshot,
            Instant.now(),
            Instant.now()
        );

    }

    public static NotificationDelivery reconstitute(
        UUID id,
        UUID eventId,
        String eventType,
        NotificationChannel channel,
        NotificationStatus status,
        int attempts,
        String lastError,
        String payloadSnapshot,
        Instant createdAt,
        Instant updatedAt
    ) {
        return new NotificationDelivery(
            id,
            eventId,
            eventType,
            channel,
            status,
            attempts,
            lastError,
            payloadSnapshot,
            createdAt,
            updatedAt
        );
    }

    // Domain behavior methods

    public void markAsSent() {
    this.status = NotificationStatus.SENT;
    this.updatedAt = Instant.now();
    }

    public void markAsFailed(String error) {
        this.status = NotificationStatus.FAILED;
        this.lastError = error;
        this.updatedAt = Instant.now();
    }

    public void incrementAttempts() {
        this.attempts++;
        this.updatedAt = Instant.now();
    }

    // Business rules

    public boolean canRetry() {
        return this.status.canRetry() && this.attempts < 3;
    }

    public boolean isSent() {
        return this.status.isSuccessful();
    }

    // Getters (only, no setters to maintain invariants)

    public UUID getId() {
        return id;
    }

    public UUID getEventId() {
        return eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public NotificationChannel getChannel() {
        return channel;
    }

    public NotificationStatus getStatus() {
        return status;
    }

    public int getAttempts() {
        return attempts;
    }

    public String getLastError() {
        return lastError;
    }

    public String getPayloadSnapshot() {
        return payloadSnapshot;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

};
