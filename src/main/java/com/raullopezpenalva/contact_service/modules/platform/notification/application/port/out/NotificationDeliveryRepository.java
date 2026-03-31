package com.raullopezpenalva.contact_service.modules.platform.notification.application.port.out;

import java.util.Optional;
import java.util.UUID;

import com.raullopezpenalva.contact_service.modules.platform.notification.domain.model.NotificationChannel;
import com.raullopezpenalva.contact_service.modules.platform.notification.domain.model.NotificationDelivery;

public interface NotificationDeliveryRepository {
    
    NotificationDelivery save(NotificationDelivery delivery);

    Optional<NotificationDelivery> findByEventIdAndChannel(
        UUID eventId,
        NotificationChannel channel
    );
}
