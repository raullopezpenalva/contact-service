package com.raullopezpenalva.contact_service;

import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.raullopezpenalva.contact_service.modules.platform.notification.application.mapper.NotificationPayloadCodec;
import com.raullopezpenalva.contact_service.modules.platform.notification.application.model.NotificationMessage;
import com.raullopezpenalva.contact_service.modules.platform.notification.application.port.out.NotificationDeliveryRepository;
import com.raullopezpenalva.contact_service.modules.platform.notification.application.service.RetryNotificationService;
import com.raullopezpenalva.contact_service.modules.platform.notification.domain.model.*;
import com.raullopezpenalva.contact_service.modules.platform.notification.infrastructure.emailSender.EmailClient;
import com.raullopezpenalva.contact_service.modules.platform.notification.infrastructure.telegram.TelegramClient;
import com.raullopezpenalva.contact_service.modules.platform.notification.infrastructure.persistence.repository.NotificationDeliveryJpaRepository;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {"app.notifications.retry.enabled=true", "app.notifications.retry.interval=1h", "app.notifications.retry.max-attempts=3"})
@ActiveProfiles("test")
class NotificationRetryIT extends JwtTestSupport {
    @Autowired NotificationDeliveryRepository deliveries;
    @Autowired NotificationDeliveryJpaRepository jpa;
    @Autowired NotificationPayloadCodec codec;
    @Autowired RetryNotificationService retries;
    @MockitoBean EmailClient email;
    @MockitoBean TelegramClient telegram;

    @BeforeEach void clean() { jpa.deleteAll(); }

    NotificationDelivery saved(NotificationStatus status, int attempts, String legacy) {
        var eventId = UUID.randomUUID();
        var message = new NotificationMessage(eventId, Instant.EPOCH, "person@example.com", "Subject", "Body, message=with delimiters");
        var delivery = NotificationDelivery.reconstitute(UUID.randomUUID(), eventId, "contact.request.created",
            NotificationChannel.EMAIL, status, attempts, "previous failure", legacy == null ? codec.encode(message) : legacy,
            Instant.now(), Instant.now());
        return deliveries.save(delivery);
    }

    @Test void onlyEligibleVersionedFailuresAreSelectedAndUpdatedInPlace() {
        var eligible = saved(NotificationStatus.FAILED, 1, null);
        var exhausted = saved(NotificationStatus.FAILED, 3, null);
        var sent = saved(NotificationStatus.SENT, 1, null);
        var pending = saved(NotificationStatus.PENDING, 0, null);
        var legacy = saved(NotificationStatus.FAILED, 1, "email=person@example.com, subject=ambiguous, message=old");
        assertEquals(List.of(eligible.getId()), deliveries.findRetryable(3, 100).stream().map(NotificationDelivery::getId).toList());
        retries.retryFailedDeliveries();
        var updated = jpa.findById(eligible.getId()).orElseThrow();
        assertEquals(NotificationStatus.SENT, updated.getStatus());
        assertEquals(2, updated.getAttempts());
        assertNull(updated.getLastError());
        assertEquals(5, jpa.count());
        assertEquals(NotificationStatus.FAILED, jpa.findById(legacy.getId()).orElseThrow().getStatus());
        assertEquals(1, jpa.findById(legacy.getId()).orElseThrow().getAttempts());
        assertEquals(3, jpa.findById(exhausted.getId()).orElseThrow().getAttempts());
        assertEquals(NotificationStatus.SENT, jpa.findById(sent.getId()).orElseThrow().getStatus());
        assertEquals(NotificationStatus.PENDING, jpa.findById(pending.getId()).orElseThrow().getStatus());
        verify(email, times(1)).sendEmail(eq("person@example.com"), anyString(), anyString());
        verifyNoInteractions(telegram);
    }

    @Test void persistsFailureUntilBudgetIsExhaustedAndDoesNotCreateDuplicateRows() {
        var delivery = saved(NotificationStatus.FAILED, 1, null);
        doThrow(new IllegalStateException("SMTP unavailable")).when(email).sendEmail(anyString(), anyString(), anyString());
        retries.retryFailedDeliveries();
        retries.retryFailedDeliveries();
        retries.retryFailedDeliveries();
        var updated = jpa.findById(delivery.getId()).orElseThrow();
        assertEquals(NotificationStatus.FAILED, updated.getStatus());
        assertEquals(3, updated.getAttempts());
        assertEquals("SMTP unavailable", updated.getLastError());
        assertEquals(1, jpa.count());
        verify(email, times(2)).sendEmail(anyString(), anyString(), anyString());
    }

    @Test void legacyRowsCannotFillBatchAheadOfReplayableRows() {
        for (int i = 0; i < 5; i++) saved(NotificationStatus.FAILED, 1, "email=x, subject=x, message=x");
        var eligible = saved(NotificationStatus.FAILED, 1, null);
        assertEquals(eligible.getId(), deliveries.findRetryable(3, 1).getFirst().getId());
    }

    @Test void corruptSupportedSnapshotIsBoundedAndPreservedForDiagnosis() {
        var delivery = saved(NotificationStatus.FAILED, 2, "{\"version\":1,broken");
        retries.retryFailedDeliveries();
        retries.retryFailedDeliveries();
        var updated = jpa.findById(delivery.getId()).orElseThrow();
        assertEquals(3, updated.getAttempts());
        assertEquals("Invalid versioned notification snapshot", updated.getLastError());
        assertEquals("{\"version\":1,broken", updated.getPayloadSnapshot());
        verifyNoInteractions(email, telegram);
    }
}
