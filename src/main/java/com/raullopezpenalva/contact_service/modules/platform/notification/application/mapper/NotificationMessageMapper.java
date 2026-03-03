package com.raullopezpenalva.contact_service.modules.platform.notification.application.mapper;

import com.raullopezpenalva.contact_service.modules.platform.notification.application.model.NotificationMessage;
import com.raullopezpenalva.contact_service.shared.events.ContactRequestCreatedEvent;

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
