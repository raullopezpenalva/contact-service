package com.raullopezpenalva.contact_service.modules.platform.notification.application.service;

import com.raullopezpenalva.contact_service.modules.platform.notification.application.model.NotificationMessage;
import com.raullopezpenalva.contact_service.modules.platform.notification.application.port.out.NotificationDeliveryRepository;
import com.raullopezpenalva.contact_service.modules.platform.notification.application.port.out.NotificationGateway;
import com.raullopezpenalva.contact_service.modules.platform.notification.domain.model.NotificationDelivery;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private final NotificationGateway notificationGateway;
    private final NotificationDeliveryRepository notificationDeliveryRepository;
    private final boolean notificationsEnabled;

    public NotificationService(
        NotificationGateway notificationGateway,
        NotificationDeliveryRepository notificationDeliveryRepository,
        @Value("${notifications.enabled:true}") boolean notificationsEnabled
    ) {
        this.notificationGateway = notificationGateway;
        this.notificationDeliveryRepository = notificationDeliveryRepository;
        this.notificationsEnabled = notificationsEnabled;
    }

    public void sendNotification(NotificationMessage message, NotificationDelivery delivery) {
        notificationDeliveryRepository.save(delivery);

        if (!notificationsEnabled) {
            delivery.markAsFailed("Notifications are disabled by configuration");
            log.debug("Notifications are disabled. Skipping sending notification for eventId={}", message.getEventId());
            notificationDeliveryRepository.save(delivery);
            return;
        }
        try {
            delivery.incrementAttempts();
            notificationGateway.sendNotification(message);
            delivery.markAsSent();
            log.info("Notification sent successfully for eventId={}", message.getEventId());
        } catch (Exception ex) {
            delivery.markAsFailed(ex.getMessage());
            log.error("Failed to send notification for eventId={}", message.getEventId(), ex);
        }
        notificationDeliveryRepository.save(delivery);
    }
}