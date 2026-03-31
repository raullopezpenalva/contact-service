package com.raullopezpenalva.contact_service.modules.platform.notification.domain.model;

public enum NotificationStatus {

    PENDING,
    SENT,
    FAILED;

    // =========================
    // Domain helpers
    // =========================

    public boolean isFinal() {
        return this == SENT || this == FAILED;
    }

    public boolean canRetry() {
        return this == FAILED;
    }

    public boolean isSuccessful() {
        return this == SENT;
    }

    public boolean isPending() {
        return this == PENDING;
    }
}