package com.raullopezpenalva.contact_service.modules.contact.domain.events;

import java.time.Instant;
import java.util.UUID;

import com.raullopezpenalva.contact_service.shared.events.DomainEvent;

public record ContactRequestCreatedEvent(
    UUID eventId,
    Instant ocurredAt,
    String eventType,
    UUID contactRequestId,
    String email,
    String subject,
    String message,
    String correlationId
) implements DomainEvent {

    public static final String TYPE = "contact.request.created";

    public ContactRequestCreatedEvent {
        if (eventId == null) {
            throw new IllegalArgumentException("eventId cannot be null");
        }
        if (ocurredAt == null) {
            throw new IllegalArgumentException("ocurredAt cannot be null");
        }
    } 
    
    @Override
    public String eventType() {
        return TYPE;
    }

}
