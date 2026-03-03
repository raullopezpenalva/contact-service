package com.raullopezpenalva.contact_service.modules.platform.notification.application.service;

import com.raullopezpenalva.contact_service.modules.platform.notification.application.model.NotificationMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private final NotificationChannel notificationChannel;
    private final boolean notificationsEnabled;

    public NotificationService(
        NotificationChannel notificationChannel,
        @Value("${app.notifications.enabled:true}") boolean notificationsEnabled
    ) {
        this.notificationChannel = notificationChannel;
        this.notificationsEnabled = notificationsEnabled;
    }

    public void sendNotification(NotificationMessage message) {
        if (!notificationsEnabled) {
            log.debug("Notifications are disabled. Skipping sending notification for eventId={}", message.getEventId());
            return;
        }
        try {
            notificationChannel.sendNotification(message);
            log.info("Notification sent successfully for eventId={}", message.getEventId());
        } catch (Exception ex) {
            log.error("Failed to send notification for eventId={}", message.getEventId(), ex);
        }
    }
}