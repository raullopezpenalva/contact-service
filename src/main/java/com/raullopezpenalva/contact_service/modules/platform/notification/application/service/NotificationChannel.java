package com.raullopezpenalva.contact_service.modules.platform.notification.application.service;

import com.raullopezpenalva.contact_service.modules.platform.notification.application.model.NotificationMessage;

public interface NotificationChannel {
    void sendNotification(NotificationMessage message);
}
