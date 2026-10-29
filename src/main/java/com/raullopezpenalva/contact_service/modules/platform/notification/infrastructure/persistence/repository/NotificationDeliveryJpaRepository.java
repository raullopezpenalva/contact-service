package com.raullopezpenalva.contact_service.modules.platform.notification.infrastructure.persistence.repository;

import com.raullopezpenalva.contact_service.modules.platform.notification.infrastructure.persistence.entity.NotificationDeliveryEntity;
import com.raullopezpenalva.contact_service.modules.platform.notification.domain.model.NotificationChannel;
import com.raullopezpenalva.contact_service.modules.platform.notification.domain.model.NotificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationDeliveryJpaRepository
        extends JpaRepository<NotificationDeliveryEntity, UUID> {

    Optional<NotificationDeliveryEntity> findByEventIdAndChannel(
            UUID eventId,
            NotificationChannel channel
    );
    @Query("""
        select delivery from NotificationDeliveryEntity delivery
        where delivery.status = :status and delivery.attempts < :maxAttempts
          and delivery.payloadSnapshot like :prefix
        order by delivery.updatedAt asc, delivery.id asc
        """)
    List<NotificationDeliveryEntity> findRetryable(
        @Param("status") NotificationStatus status,
        @Param("maxAttempts") int maxAttempts,
        @Param("prefix") String prefix,
        Pageable pageable
    );
}
