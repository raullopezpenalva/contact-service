package com.raullopezpenalva.contact_service.modules.contact.application.mapper;

import com.raullopezpenalva.contact_service.modules.contact.domain.model.ContactMessage;
import com.raullopezpenalva.contact_service.shared.events.ContactRequestCreatedEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ContactMessageCreatedEventMapperTest {

    @Test
    void shouldMapContactMessageToContactRequestCreatedEvent() {
        // Arrange
        ContactMessage entity = new ContactMessage();
        entity.setId(UUID.randomUUID());
        entity.setEmail("test@example.com");
        entity.setSubject("Hello");
        entity.setContent("This is a test message.");

        // Act
        ContactRequestCreatedEvent event = ContactMessageCreatedEventMapper.toEvent(entity);

        // Assert - payload mapping
        assertNotNull(event);
        assertEquals(entity.getId(), event.contactRequestId());
        assertEquals(entity.getEmail(), event.email());
        assertEquals(entity.getSubject(), event.subject());
        assertEquals(entity.getContent(), event.message());
        // Assert - event metadata
        assertNotNull(event.eventId());
        assertNotNull(event.ocurredAt());
        assertEquals(ContactRequestCreatedEvent.TYPE, event.eventType());
        
        // sanity: ocurredAt should be recent
        assertTrue(event.ocurredAt().isBefore(Instant.now().plusSeconds(5)));
    }
}
