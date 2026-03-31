package com.raullopezpenalva.contact_service.modules.platform.notification.infrastructure.persistence.adapter;

import com.raullopezpenalva.contact_service.modules.platform.notification.application.port.out.NotificationDeliveryRepository;
import com.raullopezpenalva.contact_service.modules.platform.notification.domain.model.NotificationChannel;
import com.raullopezpenalva.contact_service.modules.platform.notification.domain.model.NotificationDelivery;
import com.raullopezpenalva.contact_service.modules.platform.notification.infrastructure.persistence.mapper.NotificationDeliveryPersistenceMapper;
import com.raullopezpenalva.contact_service.modules.platform.notification.infrastructure.persistence.repository.NotificationDeliveryJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class NotificationDeliveryRepositoryAdapter implements NotificationDeliveryRepository {

    private final NotificationDeliveryJpaRepository notificationDeliveryJpaRepository;

    public NotificationDeliveryRepositoryAdapter(NotificationDeliveryJpaRepository notificationDeliveryJpaRepository) {
        this.notificationDeliveryJpaRepository = notificationDeliveryJpaRepository;
    }

    @Override
    public NotificationDelivery save(NotificationDelivery delivery) {
        var entity = NotificationDeliveryPersistenceMapper.toEntity(delivery);
        var savedEntity = notificationDeliveryJpaRepository.save(entity);
        return NotificationDeliveryPersistenceMapper.toDomain(savedEntity);
    }

    @Override
    public Optional<NotificationDelivery> findByEventIdAndChannel(UUID eventId, NotificationChannel channel) {
        return notificationDeliveryJpaRepository.findByEventIdAndChannel(eventId, channel)
                .map(NotificationDeliveryPersistenceMapper::toDomain);
    }
}