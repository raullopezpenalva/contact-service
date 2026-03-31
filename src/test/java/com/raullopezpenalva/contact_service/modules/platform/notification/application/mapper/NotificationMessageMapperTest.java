package com.raullopezpenalva.contact_service.modules.platform.notification.application.mapper;

import com.raullopezpenalva.contact_service.modules.contact.domain.events.ContactRequestCreatedEvent;
import com.raullopezpenalva.contact_service.modules.platform.notification.application.model.NotificationMessage;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class NotificationMessageMapperTest {
    
    @Test
    void shouldMapContactRequestCreatedEventToNotificationMessage() {
        // Arrange
        UUID eventId = UUID.randomUUID();
        Instant ocurredAt = Instant.parse("2026-03-03T12:00:00Z");

        UUID contactRequestId = UUID.randomUUID();
        String email = "user@example.com";
        String subject = "Test Subject";
        String message = "This is a test message.";
        String correlationId = "corr-123";

        ContactRequestCreatedEvent event = new ContactRequestCreatedEvent(
            eventId,
            ocurredAt,
            ContactRequestCreatedEvent.TYPE,
            contactRequestId,
            email,
            subject,
            message,
            correlationId
        );

        // Act
        NotificationMessage result = NotificationMessageMapper.toNotificationMessage(event);

        // Assert
        assertNotNull(result);

        assertEquals(eventId, result.getEventId());
        assertEquals(ocurredAt, result.getOcurredAt());
        assertEquals(email, result.getEmail());
        assertEquals(subject, result.getSubject());
        assertEquals(message, result.getMessage());
    }
}
