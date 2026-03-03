package com.raullopezpenalva.contact_service.shared.events;

import java.time.Instant;
import java.util.UUID;

public interface DomainEvent {

    UUID eventId();

    Instant ocurredAt();

    String eventType();

    default String correlationId() {
        return null;
    }
    
}
