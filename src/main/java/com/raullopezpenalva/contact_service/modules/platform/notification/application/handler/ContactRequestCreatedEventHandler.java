package com.raullopezpenalva.contact_service.modules.platform.notification.application.handler;

import com.raullopezpenalva.contact_service.modules.platform.notification.application.model.NotificationMessage;
import com.raullopezpenalva.contact_service.modules.platform.notification.application.mapper.NotificationMessageMapper;
import com.raullopezpenalva.contact_service.modules.platform.notification.application.service.NotificationService;
import com.raullopezpenalva.contact_service.shared.events.ContactRequestCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Async;
import org.springframework.context.event.EventListener;


@Component
public class ContactRequestCreatedEventHandler {

    private static final Logger log = LoggerFactory.getLogger(ContactRequestCreatedEventHandler.class);
    private final NotificationService notificationService;

    public ContactRequestCreatedEventHandler(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Async
    @EventListener
    public void handle(ContactRequestCreatedEvent event) {
        try {
            NotificationMessage message = NotificationMessageMapper.toNotificationMessage(event);

            log.info(
                "Handling eventType={}, eventId={}, contactRequestId={}",
                event.eventType(),
                event.eventId(),
                event.contactRequestId()
            );
            notificationService.sendNotification(message);
        } catch (Exception ex) {
            
            log.error(
                "Failed to handle ContactRequestCreatedEvent eventId={}, contactRequestId={}",
                event.eventId(),
                event.contactRequestId(),
                ex
            );
        }
    }
}
