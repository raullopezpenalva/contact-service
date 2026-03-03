package com.raullopezpenalva.contact_service.modules.contact.application.mapper;

import com.raullopezpenalva.contact_service.modules.contact.domain.model.ContactMessage;
import com.raullopezpenalva.contact_service.shared.events.ContactRequestCreatedEvent;

import java.time.Instant;
import java.util.UUID;

public class ContactMessageCreatedEventMapper {
    
    public static ContactRequestCreatedEvent toEvent(ContactMessage entity) {
        return new ContactRequestCreatedEvent(
            UUID.randomUUID(),
            Instant.now(),
            ContactRequestCreatedEvent.TYPE,
            entity.getId(),
            entity.getEmail(),
            entity.getSubject(),
            truncate(entity.getContent()),
            null
        );
    }

    private static String truncate(String text) {
        if (text == null) return "";
        int max = 500;
        return text.length() > max ? text.substring(0, max) + "..." : text;
    }
}
