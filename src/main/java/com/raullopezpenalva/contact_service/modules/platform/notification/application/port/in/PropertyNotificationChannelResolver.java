package com.raullopezpenalva.contact_service.modules.platform.notification.application.port.in;

import com.raullopezpenalva.contact_service.modules.platform.notification.domain.model.NotificationChannel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class PropertyNotificationChannelResolver implements NotificationChannelResolver {

    private final NotificationChannel channel;

    public PropertyNotificationChannelResolver(
        @Value("${app.notification.channel:EMAIL}") NotificationChannel channel
    ) {
        this.channel = channel;
    }

    @Override
    public NotificationChannel resolve() {
        return channel;
    }
}