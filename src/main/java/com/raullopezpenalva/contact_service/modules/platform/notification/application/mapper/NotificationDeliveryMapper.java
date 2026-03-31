package com.raullopezpenalva.contact_service.modules.platform.notification.application.mapper;

import com.raullopezpenalva.contact_service.modules.contact.domain.events.ContactRequestCreatedEvent;
import com.raullopezpenalva.contact_service.modules.platform.notification.domain.model.NotificationChannel;
import com.raullopezpenalva.contact_service.modules.platform.notification.domain.model.NotificationDelivery;

public final class NotificationDeliveryMapper {

    private NotificationDeliveryMapper() {
    }

    public static NotificationDelivery fromEvent(ContactRequestCreatedEvent event) {

        String payload = buildPayloadSnapshot(event);

        return NotificationDelivery.createNew(
                event.eventId(),
                event.eventType(),
                NotificationChannel.TELEGRAM,
                payload
        );
    }

    private static String buildPayloadSnapshot(ContactRequestCreatedEvent event) {
        return String.format(
                "email=%s, subject=%s, message=%s",
                event.email(),
                event.subject(),
                event.message()
        );
    }
}