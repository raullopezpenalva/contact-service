package com.raullopezpenalva.contact_service.modules.platform.notification.application.port.in;

import com.raullopezpenalva.contact_service.modules.platform.notification.domain.model.NotificationChannel;

public interface NotificationChannelResolver {
    NotificationChannel resolve();
}
