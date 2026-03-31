package com.raullopezpenalva.contact_service.modules.platform.notification.application.port.out;

import com.raullopezpenalva.contact_service.modules.platform.notification.application.model.NotificationMessage;

public interface NotificationGateway {
    void sendNotification(NotificationMessage message);
}
