package com.raullopezpenalva.contact_service;

import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import com.raullopezpenalva.contact_service.modules.platform.notification.domain.model.NotificationStatus;
import com.raullopezpenalva.contact_service.modules.platform.notification.infrastructure.persistence.repository.NotificationDeliveryJpaRepository;
import com.raullopezpenalva.contact_service.modules.platform.notification.infrastructure.telegram.TelegramClient;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"app.notifications.channels=EMAIL", "app.notifications.retry.enabled=true",
    "app.notifications.retry.interval=50ms", "spring.mail.username=service@example.com", "email.recipient=owner@example.com"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class NotificationSchedulerIT {
    @Autowired MockMvc mvc;
    @Autowired NotificationDeliveryJpaRepository deliveries;
    @MockitoBean JavaMailSender mail;
    @MockitoBean TelegramClient telegram;

    @Test void scheduledRetryRecoversRealEmailAdapterFailureWhilePublicRequestReturns201() throws Exception {
        deliveries.deleteAll();
        when(mail.createMimeMessage()).thenAnswer(i -> new MimeMessage(Session.getInstance(new Properties())));
        var attempts = new AtomicInteger();
        var retryStarted = new CountDownLatch(1);
        var releaseRetry = new CountDownLatch(1);
        doAnswer(i -> {
            int attempt = attempts.incrementAndGet();
            if (attempt == 1) throw new MailSendException("Simulated SMTP outage");
            retryStarted.countDown();
            if (!releaseRetry.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Test retry timeout");
            return null;
        }).when(mail).send(any(MimeMessage.class));
        try {
            mvc.perform(post("/api/v1/contact/messages").contentType("application/json")
                .content("{\"email\":\"person@example.com\",\"subject\":\"Hello\",\"content\":\"Message long enough for validation\"}"))
                .andExpect(status().isCreated());
            assertTrue(retryStarted.await(5, TimeUnit.SECONDS));
            var failed = deliveries.findAll().getFirst();
            assertEquals(NotificationStatus.FAILED, failed.getStatus());
            assertEquals(1, failed.getAttempts());
            assertEquals("Email delivery failed", failed.getLastError());
            var id = failed.getId();
            releaseRetry.countDown();
            await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
                var recovered = deliveries.findById(id).orElseThrow();
                assertEquals(NotificationStatus.SENT, recovered.getStatus());
                assertEquals(2, recovered.getAttempts());
                assertNull(recovered.getLastError());
                assertEquals(1, deliveries.count());
            });
            assertEquals(2, attempts.get());
            verifyNoInteractions(telegram);
        } finally {
            releaseRetry.countDown();
        }
    }
}
