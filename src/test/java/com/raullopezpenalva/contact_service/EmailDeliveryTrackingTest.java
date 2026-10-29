package com.raullopezpenalva.contact_service;

import java.time.Instant;
import java.util.*;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.*;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import com.raullopezpenalva.contact_service.modules.platform.notification.application.model.NotificationMessage;
import com.raullopezpenalva.contact_service.modules.platform.notification.application.port.out.NotificationDeliveryRepository;
import com.raullopezpenalva.contact_service.modules.platform.notification.application.service.NotificationService;
import com.raullopezpenalva.contact_service.modules.platform.notification.domain.model.*;
import com.raullopezpenalva.contact_service.modules.platform.notification.infrastructure.emailSender.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(OutputCaptureExtension.class)
class EmailDeliveryTrackingTest {
    private final JavaMailSender smtp = mock(JavaMailSender.class);
    private final NotificationDeliveryRepository repository = mock(NotificationDeliveryRepository.class);
    private final List<NotificationStatus> persistedStates = new ArrayList<>();
    private final NotificationMessage message = new NotificationMessage(UUID.randomUUID(), Instant.EPOCH,
        "visitor@example.com", "Hello", "Contact message");

    private NotificationService service() {
        var client = new EmailClient(smtp);
        ReflectionTestUtils.setField(client, "sender", "service@example.com");
        ReflectionTestUtils.setField(client, "recipient", "owner@example.com");
        doAnswer(i -> {
            NotificationDelivery delivery = i.getArgument(0);
            persistedStates.add(delivery.getStatus());
            return delivery;
        }).when(repository).save(any());
        return new NotificationService(List.of(new EmailNotificationChannel(client)), repository, true);
    }

    @ParameterizedTest @ValueSource(booleans = {true, false})
    void constructionOrSubmissionFailuresPersistFailedWithoutLeakingDiagnostics(boolean constructionFailure, CapturedOutput output) {
        String privateDiagnostic = "sensitive-smtp-diagnostic-credentials";
        var mime = new MimeMessage(Session.getInstance(new Properties()));
        if (constructionFailure) {
            when(smtp.createMimeMessage()).thenThrow(new MailSendException(privateDiagnostic));
        } else {
            when(smtp.createMimeMessage()).thenReturn(mime);
            doThrow(new MailSendException(privateDiagnostic)).when(smtp).send(mime);
        }
        var delivery = NotificationDelivery.createNew(message.getEventId(), "contact.request.created", NotificationChannel.EMAIL, "{}");
        var service = service();
        assertDoesNotThrow(() -> service.sendNotification(message, delivery));
        assertEquals(List.of(NotificationStatus.PENDING, NotificationStatus.FAILED), persistedStates);
        assertEquals(1, delivery.getAttempts());
        assertEquals("Email delivery failed", delivery.getLastError());
        assertTrue(output.getAll().contains("Notification failed"));
        assertFalse(output.getAll().contains(privateDiagnostic));
    }

    @Test void successfulSubmissionRecordsSentWithoutClaimingInboxDelivery() {
        when(smtp.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
        var delivery = NotificationDelivery.createNew(message.getEventId(), "contact.request.created", NotificationChannel.EMAIL, "{}");
        service().sendNotification(message, delivery);
        assertEquals(List.of(NotificationStatus.PENDING, NotificationStatus.SENT), persistedStates);
        assertEquals(1, delivery.getAttempts());
        verify(smtp).send(any(MimeMessage.class));
        // A later provider bounce is not observable through this synchronous gateway call.
    }
}
