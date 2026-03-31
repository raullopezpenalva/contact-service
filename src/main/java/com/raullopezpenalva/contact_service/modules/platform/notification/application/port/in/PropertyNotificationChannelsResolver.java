package com.raullopezpenalva.contact_service.modules.platform.notification.application.port.in;

import com.raullopezpenalva.contact_service.modules.platform.notification.domain.model.NotificationChannel;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class PropertyNotificationChannelsResolver implements NotificationChannelsResolver {

    private final Set<NotificationChannel> channels;

    public PropertyNotificationChannelsResolver(
        @Value("${app.notifications.channels:EMAIL}") String channelsProperty
    ) {
        this.channels = Arrays.stream(channelsProperty.split(","))
            .map(String::trim)
            .map(String::toUpperCase)
            .map(NotificationChannel::valueOf)
            .collect(Collectors.toSet());
    }

    @Override
    public Set<NotificationChannel> resolve() {
        return channels;
    }
}