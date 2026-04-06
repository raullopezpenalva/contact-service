package com.raullopezpenalva.contact_service.modules.platform.notification.application.port.out;

import com.raullopezpenalva.contact_service.modules.platform.notification.application.model.NotificationMessage;
import com.raullopezpenalva.contact_service.modules.platform.notification.domain.model.NotificationChannel;

public interface NotificationGateway {
    
    NotificationChannel getChannel();

    void sendNotification(NotificationMessage message);
}
