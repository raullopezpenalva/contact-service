package com.raullopezpenalva.contact_service.modules.platform.notification.application.mapper;

import java.time.Instant;
import java.util.UUID;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.raullopezpenalva.contact_service.modules.platform.notification.application.model.NotificationMessage;
import com.raullopezpenalva.contact_service.modules.platform.notification.domain.model.NotificationDelivery;
import org.springframework.stereotype.Component;

/** Versioned snapshots are self-contained; legacy display strings are never parsed. */
@Component
public class NotificationPayloadCodec {
    public static final String VERSION_ONE_PREFIX = "{\"version\":1,";
    private final ObjectMapper mapper;

    public NotificationPayloadCodec(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public String encode(NotificationMessage message) {
        try {
            return mapper.writer().without(com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT).writeValueAsString(new Payload(1, message.getEventId(),
                message.getOcurredAt().toString(), message.getEmail(), message.getSubject(), message.getMessage()));
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Cannot encode notification snapshot", ex);
        }
    }

    public NotificationMessage decode(NotificationDelivery delivery) {
        try {
            Payload payload = mapper.readValue(delivery.getPayloadSnapshot(), Payload.class);
            if (payload.version() != 1 || !delivery.getEventId().equals(payload.eventId())
                    || payload.email() == null || payload.subject() == null || payload.message() == null) {
                throw new IllegalArgumentException("Unsupported or incomplete notification snapshot");
            }
            return new NotificationMessage(payload.eventId(), Instant.parse(payload.occurredAt()),
                payload.email(), payload.subject(), payload.message());
        } catch (JsonProcessingException | RuntimeException ex) {
            // Do not put payload contents or parser diagnostics in logs or delivery errors.
            throw new IllegalArgumentException("Invalid versioned notification snapshot");
        }
    }

    @com.fasterxml.jackson.annotation.JsonPropertyOrder({"version", "eventId", "occurredAt", "email", "subject", "message"})
    public record Payload(int version, UUID eventId, String occurredAt,
                          String email, String subject, String message) {}
}
