package com.raullopezpenalva.contact_service.modules.platform.notification.application.port.in;

import java.util.Set;

import com.raullopezpenalva.contact_service.modules.platform.notification.domain.model.NotificationChannel;

public interface NotificationChannelsResolver {
    Set<NotificationChannel> resolve();
}
