package com.raullopezpenalva.contact_service.modules.platform.notification.infrastructure.persistence.mapper;

import com.raullopezpenalva.contact_service.modules.platform.notification.domain.model.NotificationChannel;
import com.raullopezpenalva.contact_service.modules.platform.notification.domain.model.NotificationDelivery;
import com.raullopezpenalva.contact_service.modules.platform.notification.domain.model.NotificationStatus;
import com.raullopezpenalva.contact_service.modules.platform.notification.infrastructure.persistence.entity.NotificationDeliveryEntity;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class NotificationDeliveryPersistenceMapperTest {

    @Test
    void shouldMapDomainToEntity() {
        UUID id = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        Instant now = Instant.now();

        NotificationDelivery delivery = NotificationDelivery.reconstitute(
                id,
                eventId,
                "contact.request.created",
                NotificationChannel.TELEGRAM,
                NotificationStatus.FAILED,
                2,
                "Telegram error",
                "{\"email\":\"test@example.com\"}",
                now,
                now
        );

        NotificationDeliveryEntity entity = NotificationDeliveryPersistenceMapper.toEntity(delivery);

        assertNotNull(entity);
        assertEquals(id, entity.getId());
        assertEquals(eventId, entity.getEventId());
        assertEquals("contact.request.created", entity.getEventType());
        assertEquals(NotificationChannel.TELEGRAM, entity.getChannel());
        assertEquals(NotificationStatus.FAILED, entity.getStatus());
        assertEquals(2, entity.getAttempts());
        assertEquals("Telegram error", entity.getLastError());
        assertEquals("{\"email\":\"test@example.com\"}", entity.getPayloadSnapshot());
        assertEquals(now, entity.getCreatedAt());
        assertEquals(now, entity.getUpdatedAt());
    }

    @Test
    void shouldMapEntityToDomain() {
        UUID id = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        Instant now = Instant.now();

        NotificationDeliveryEntity entity = new NotificationDeliveryEntity();
        entity.setId(id);
        entity.setEventId(eventId);
        entity.setEventType("contact.request.created");
        entity.setChannel(NotificationChannel.TELEGRAM);
        entity.setStatus(NotificationStatus.SENT);
        entity.setAttempts(1);
        entity.setLastError(null);
        entity.setPayloadSnapshot("{\"email\":\"test@example.com\"}");
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);

        NotificationDelivery delivery = NotificationDeliveryPersistenceMapper.toDomain(entity);

        assertNotNull(delivery);
        assertEquals(id, delivery.getId());
        assertEquals(eventId, delivery.getEventId());
        assertEquals("contact.request.created", delivery.getEventType());
        assertEquals(NotificationChannel.TELEGRAM, delivery.getChannel());
        assertEquals(NotificationStatus.SENT, delivery.getStatus());
        assertEquals(1, delivery.getAttempts());
        assertNull(delivery.getLastError());
        assertEquals("{\"email\":\"test@example.com\"}", delivery.getPayloadSnapshot());
        assertEquals(now, delivery.getCreatedAt());
        assertEquals(now, delivery.getUpdatedAt());
    }
}