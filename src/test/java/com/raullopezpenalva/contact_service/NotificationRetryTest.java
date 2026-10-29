package com.raullopezpenalva.contact_service;

import java.time.Instant;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.raullopezpenalva.contact_service.modules.platform.notification.application.mapper.NotificationPayloadCodec;
import com.raullopezpenalva.contact_service.modules.platform.notification.application.model.NotificationMessage;
import com.raullopezpenalva.contact_service.modules.platform.notification.application.port.out.*;
import com.raullopezpenalva.contact_service.modules.platform.notification.application.service.*;
import com.raullopezpenalva.contact_service.modules.platform.notification.domain.model.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NotificationRetryTest {
    private final NotificationPayloadCodec codec = new NotificationPayloadCodec(new ObjectMapper());
    private final NotificationDeliveryRepository repository = mock(NotificationDeliveryRepository.class);
    private final NotificationGateway gateway = mock(NotificationGateway.class);
    private final NotificationMessage message = new NotificationMessage(UUID.randomUUID(), Instant.EPOCH,
        "person@example.com", "Subject, message=not a delimiter", "Quotes \" , message= and\nUnicode: café");

    private NotificationDelivery failedDelivery() {
        var delivery = NotificationDelivery.createNew(message.getEventId(), "contact.request.created",
            NotificationChannel.EMAIL, codec.encode(message));
        delivery.incrementAttempts();
        delivery.markAsFailed("Previous failure");
        return delivery;
    }

    private RetryNotificationService worker(boolean retryEnabled, boolean notificationsEnabled, int max) {
        when(gateway.getChannel()).thenReturn(NotificationChannel.EMAIL);
        var sender = new NotificationService(List.of(gateway), repository, notificationsEnabled);
        return new RetryNotificationService(repository, sender, codec, retryEnabled, notificationsEnabled, max, 100);
    }

    @Test void roundTripsVersionedPayloadWithoutDelimiterAmbiguity() {
        var delivery = failedDelivery();
        assertTrue(delivery.getPayloadSnapshot().startsWith(NotificationPayloadCodec.VERSION_ONE_PREFIX));
        var restored = codec.decode(delivery);
        assertEquals(message.getEventId(), restored.getEventId());
        assertEquals(message.getOcurredAt(), restored.getOcurredAt());
        assertEquals(message.getEmail(), restored.getEmail());
        assertEquals(message.getSubject(), restored.getSubject());
        assertEquals(message.getMessage(), restored.getMessage());
    }

    @Test void retryReusesDeliveryAndClearsOldErrorOnSuccess() {
        var delivery = failedDelivery();
        var id = delivery.getId();
        when(repository.findRetryable(3, 100)).thenReturn(List.of(delivery));
        var worker = worker(true, true, 3);
        worker.retryFailedDeliveries();
        worker.retryFailedDeliveries();
        assertEquals(id, delivery.getId());
        assertEquals(NotificationStatus.SENT, delivery.getStatus());
        assertEquals(2, delivery.getAttempts());
        assertNull(delivery.getLastError());
        verify(gateway, times(1)).sendNotification(any());
        verify(repository, times(2)).save(same(delivery));
    }

    @Test void repeatedFailureStopsAtConfiguredTotalAttempts() {
        var delivery = failedDelivery();
        when(repository.findRetryable(2, 100)).thenReturn(List.of(delivery));
        doThrow(new IllegalStateException("still unavailable")).when(gateway).sendNotification(any());
        var worker = worker(true, true, 2);
        worker.retryFailedDeliveries();
        worker.retryFailedDeliveries();
        assertEquals(2, delivery.getAttempts());
        assertEquals(NotificationStatus.FAILED, delivery.getStatus());
        assertEquals("still unavailable", delivery.getLastError());
        verify(gateway, times(1)).sendNotification(any());
    }

    @Test void supportsAttemptBudgetsAboveThePreviousHardCodedThree() {
        var delivery = failedDelivery();
        when(repository.findRetryable(5, 100)).thenReturn(List.of(delivery));
        doThrow(new IllegalStateException("unavailable")).when(gateway).sendNotification(any());
        var worker = worker(true, true, 5);
        for (int i = 0; i < 6; i++) worker.retryFailedDeliveries();
        assertEquals(5, delivery.getAttempts());
        verify(gateway, times(4)).sendNotification(any());
    }

    @Test void disabledRetryNeverQueriesOrSends() {
        worker(false, true, 3).retryFailedDeliveries();
        verifyNoInteractions(repository);
        verify(gateway, never()).sendNotification(any());
    }

    @Test void globallyDisabledNotificationsNeverQueryOrSend() {
        worker(true, false, 3).retryFailedDeliveries();
        verifyNoInteractions(repository);
        verify(gateway, never()).sendNotification(any());
    }

    @Test void pendingSentAndExhaustedDeliveriesCannotBeRetried() {
        var pending = NotificationDelivery.createNew(message.getEventId(), "contact.request.created", NotificationChannel.EMAIL, codec.encode(message));
        var sent = failedDelivery(); sent.markAsSent();
        var exhausted = failedDelivery(); exhausted.incrementAttempts(); exhausted.incrementAttempts();
        when(repository.findRetryable(3, 100)).thenReturn(List.of(pending, sent, exhausted));
        worker(true, true, 3).retryFailedDeliveries();
        verify(gateway, never()).sendNotification(any());
        verify(repository, never()).save(any());
    }

    @Test void invalidSnapshotConsumesBoundedAttemptAndDoesNotStarveValidDelivery() {
        var invalid = NotificationDelivery.createNew(UUID.randomUUID(), "contact.request.created", NotificationChannel.EMAIL, "{\"version\":1,broken");
        invalid.markAsFailed("old");
        var valid = failedDelivery();
        when(repository.findRetryable(3, 100)).thenReturn(List.of(invalid, valid));
        worker(true, true, 3).retryFailedDeliveries();
        assertEquals(1, invalid.getAttempts());
        assertEquals("Invalid versioned notification snapshot", invalid.getLastError());
        assertEquals(NotificationStatus.SENT, valid.getStatus());
        verify(gateway, times(1)).sendNotification(any());
    }

    @Test void rejectsMismatchedEventAndLegacySnapshotsWithoutLeakingPayload() {
        var mismatch = NotificationDelivery.createNew(UUID.randomUUID(), "contact.request.created", NotificationChannel.EMAIL, codec.encode(message));
        var legacy = NotificationDelivery.createNew(message.getEventId(), "contact.request.created", NotificationChannel.EMAIL, "email=private@example.com, subject=x, message=secret");
        for (var delivery : List.of(mismatch, legacy)) {
            var error = assertThrows(IllegalArgumentException.class, () -> codec.decode(delivery));
            assertFalse(error.getMessage().contains("private@example.com"));
            assertFalse(error.getMessage().contains("secret"));
        }
    }

    @Test void errorsFitDatabaseColumnAndDomainSupportsConfiguredLimit() {
        var delivery = failedDelivery();
        delivery.markAsFailed("x".repeat(1000));
        assertEquals(255, delivery.getLastError().length());
        assertFalse(delivery.canRetry(1));
        assertTrue(delivery.canRetry(2));
    }
}
