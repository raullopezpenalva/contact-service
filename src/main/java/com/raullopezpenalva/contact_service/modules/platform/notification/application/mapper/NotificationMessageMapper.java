package com.raullopezpenalva.contact_service.modules.platform.notification.application.mapper;

import com.raullopezpenalva.contact_service.modules.contact.domain.events.ContactRequestCreatedEvent;
import com.raullopezpenalva.contact_service.modules.platform.notification.application.model.NotificationMessage;

public class NotificationMessageMapper {

    public static NotificationMessage toNotificationMessage(ContactRequestCreatedEvent event) {
        return new NotificationMessage(
            event.eventId(),
            event.ocurredAt(),
            event.email(),
            event.subject(),
            event.message()
        );
    }
    
}
