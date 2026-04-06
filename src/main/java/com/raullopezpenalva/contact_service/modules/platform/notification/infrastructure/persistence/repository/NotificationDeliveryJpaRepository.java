package com.raullopezpenalva.contact_service.modules.platform.notification.infrastructure.persistence.repository;

import com.raullopezpenalva.contact_service.modules.platform.notification.infrastructure.persistence.entity.NotificationDeliveryEntity;
import com.raullopezpenalva.contact_service.modules.platform.notification.domain.model.NotificationChannel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationDeliveryJpaRepository
        extends JpaRepository<NotificationDeliveryEntity, UUID> {

    Optional<NotificationDeliveryEntity> findByEventIdAndChannel(
            UUID eventId,
            NotificationChannel channel
    );
    List<NotificationDeliveryEntity> findAll();
}