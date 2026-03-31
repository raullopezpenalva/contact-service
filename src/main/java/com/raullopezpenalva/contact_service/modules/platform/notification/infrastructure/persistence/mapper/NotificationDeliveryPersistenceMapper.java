package com.raullopezpenalva.contact_service.modules.platform.notification.infrastructure.persistence.mapper;

import com.raullopezpenalva.contact_service.modules.platform.notification.domain.model.NotificationDelivery;
import com.raullopezpenalva.contact_service.modules.platform.notification.infrastructure.persistence.entity.NotificationDeliveryEntity;

public final class NotificationDeliveryPersistenceMapper {

    private NotificationDeliveryPersistenceMapper() {
    }

    public static NotificationDeliveryEntity toEntity(NotificationDelivery delivery) {
        NotificationDeliveryEntity entity = new NotificationDeliveryEntity();

        entity.setId(delivery.getId());
        entity.setEventId(delivery.getEventId());
        entity.setEventType(delivery.getEventType());
        entity.setChannel(delivery.getChannel());
        entity.setStatus(delivery.getStatus());
        entity.setAttempts(delivery.getAttempts());
        entity.setLastError(delivery.getLastError());
        entity.setPayloadSnapshot(delivery.getPayloadSnapshot());
        entity.setCreatedAt(delivery.getCreatedAt());
        entity.setUpdatedAt(delivery.getUpdatedAt());

        return entity;
    }

    public static NotificationDelivery toDomain(NotificationDeliveryEntity entity) {
        return NotificationDelivery.reconstitute(
                entity.getId(),
                entity.getEventId(),
                entity.getEventType(),
                entity.getChannel(),
                entity.getStatus(),
                entity.getAttempts(),
                entity.getLastError(),
                entity.getPayloadSnapshot(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}