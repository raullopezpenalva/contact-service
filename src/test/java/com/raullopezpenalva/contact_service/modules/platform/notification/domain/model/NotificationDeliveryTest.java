package com.raullopezpenalva.contact_service.modules.platform.notification.domain.model;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class NotificationDeliveryTest {

    @Test
    void shouldCreatePendingNotificationDelivery() {
        NotificationDelivery delivery = NotificationDelivery.createNew(
                UUID.randomUUID(),
                "contact.request.created",
                NotificationChannel.TELEGRAM,
                "{\"email\":\"test@example.com\"}"
        );

        assertNotNull(delivery.getId());
        assertNotNull(delivery.getCreatedAt());
        assertNotNull(delivery.getUpdatedAt());
        assertEquals(NotificationStatus.PENDING, delivery.getStatus());
        assertEquals(0, delivery.getAttempts());
        assertNull(delivery.getLastError());
        assertEquals(NotificationChannel.TELEGRAM, delivery.getChannel());
    }

    @Test
    void shouldIncrementAttempts() {
        NotificationDelivery delivery = NotificationDelivery.createNew(
                UUID.randomUUID(),
                "contact.request.created",
                NotificationChannel.TELEGRAM,
                "{\"email\":\"test@example.com\"}"
        );

        delivery.incrementAttempts();

        assertEquals(1, delivery.getAttempts());
    }

    @Test
    void shouldMarkAsSent() {
        NotificationDelivery delivery = NotificationDelivery.createNew(
                UUID.randomUUID(),
                "contact.request.created",
                NotificationChannel.TELEGRAM,
                "{\"email\":\"test@example.com\"}"
        );

        delivery.markAsSent();

        assertEquals(NotificationStatus.SENT, delivery.getStatus());
        assertTrue(delivery.isSent());
    }

    @Test
    void shouldMarkAsFailed() {
        NotificationDelivery delivery = NotificationDelivery.createNew(
                UUID.randomUUID(),
                "contact.request.created",
                NotificationChannel.TELEGRAM,
                "{\"email\":\"test@example.com\"}"
        );

        delivery.markAsFailed("Telegram API error");

        assertEquals(NotificationStatus.FAILED, delivery.getStatus());
        assertEquals("Telegram API error", delivery.getLastError());
    }

    @Test
    void shouldAllowRetryWhenFailedAndAttemptsLessThanThree() {
        NotificationDelivery delivery = NotificationDelivery.createNew(
                UUID.randomUUID(),
                "contact.request.created",
                NotificationChannel.TELEGRAM,
                "{\"email\":\"test@example.com\"}"
        );

        delivery.markAsFailed("Temporary error");
        delivery.incrementAttempts();

        assertTrue(delivery.canRetry());
    }

    @Test
    void shouldNotAllowRetryWhenAttemptsReachedThree() {
        NotificationDelivery delivery = NotificationDelivery.createNew(
                UUID.randomUUID(),
                "contact.request.created",
                NotificationChannel.TELEGRAM,
                "{\"email\":\"test@example.com\"}"
        );

        delivery.markAsFailed("Temporary error");
        delivery.incrementAttempts();
        delivery.incrementAttempts();
        delivery.incrementAttempts();

        assertFalse(delivery.canRetry());
    }
}