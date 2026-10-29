package com.raullopezpenalva.contact_service.modules.platform.notification.application.mapper;

import com.raullopezpenalva.contact_service.modules.contact.domain.events.ContactRequestCreatedEvent;
import com.raullopezpenalva.contact_service.modules.platform.notification.domain.model.NotificationChannel;
import com.raullopezpenalva.contact_service.modules.platform.notification.domain.model.NotificationDelivery;
import org.springframework.stereotype.Component;

@Component
public class NotificationDeliveryMapper {
    private final NotificationPayloadCodec payloadCodec;

    public NotificationDeliveryMapper(NotificationPayloadCodec payloadCodec) {
        this.payloadCodec = payloadCodec;
    }

    public NotificationDelivery fromEvent(ContactRequestCreatedEvent event, NotificationChannel channel) {
        return NotificationDelivery.createNew(event.eventId(), event.eventType(), channel,
            payloadCodec.encode(NotificationMessageMapper.toNotificationMessage(event)));
    }
}
