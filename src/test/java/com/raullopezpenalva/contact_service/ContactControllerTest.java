package com.raullopezpenalva.contact_service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.raullopezpenalva.contact_service.modules.contact.infrastructure.repository.ContactMessageRepository;
import com.raullopezpenalva.contact_service.modules.platform.notification.domain.model.NotificationChannel;
import com.raullopezpenalva.contact_service.modules.platform.notification.domain.model.NotificationStatus;
import com.raullopezpenalva.contact_service.modules.platform.notification.infrastructure.emailSender.EmailClient;
import com.raullopezpenalva.contact_service.modules.platform.notification.infrastructure.persistence.repository.NotificationDeliveryJpaRepository;
import com.raullopezpenalva.contact_service.modules.platform.notification.infrastructure.telegram.TelegramClient;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.awaitility.Awaitility.await;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ContactControllerTest {
    
    @Autowired MockMvc mockMvc;
    @Autowired ContactMessageRepository contactMessageRepository;
    @Autowired ObjectMapper objectMapper;
    @Autowired NotificationDeliveryJpaRepository notificationDeliveryJpaRepository;

    @MockitoBean
    EmailClient emailClient;

    @MockitoBean
    TelegramClient telegramClient;

    @BeforeEach
    void cleanUp() {
        notificationDeliveryJpaRepository.deleteAll();
        contactMessageRepository.deleteAll();
    }

    @Test
    void createContact_shouldPersistAndTriggerNotificationDelivery() throws Exception {
        // Arrange
        var payload = """
        {
            "email": "integration@test.com",
            "subject": "Integration Test Subject",
            "content": "This is an integration test message content."
        }
        """;

        // Act & Assert HTTP
        mockMvc.perform(
            post("/api/v1/contact/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
        )
        .andExpect(status().isCreated())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.id").exists());

        // Assert message persisted
        assertTrue(contactMessageRepository.findAll().stream()
            .anyMatch(m -> "integration@test.com".equals(m.getEmail())));

        // Assert DB - notification delivery persisted asynchronously
        await().atMost(5, SECONDS).pollInterval(100, MILLISECONDS).untilAsserted(() -> {
            var deliveries = notificationDeliveryJpaRepository.findAll();

            System.out.println("---- TEST DELIVERIES ----");
            deliveries.forEach(d -> System.out.println(
                "id=" + d.getId()
                + ", eventId=" + d.getEventId()
                + ", channel=" + d.getChannel()
                + ", status=" + d.getStatus()
                + ", attempts=" + d.getAttempts()
                + ", lastError=" + d.getLastError()
            ));

            assertTrue(deliveries.stream().anyMatch(d ->
                d.getChannel() == NotificationChannel.TELEGRAM &&
                d.getStatus() == NotificationStatus.SENT &&
                d.getPayloadSnapshot() != null &&
                d.getPayloadSnapshot().contains("integration@test.com")
            ));
        });
    }

    @Test
    void createContact_shouldReturn201AndPersistFailedDeliveryWhenNotificationFails() throws Exception {
        // Arrange
        doThrow(new RuntimeException("telegram down")).when(telegramClient).sendMessage(any());

        var payload = """
        {
            "email": "fail@test.com",
            "subject": "Integration Test Subject",
            "content": "This is an integration test message content."
        }
        """;

        // Act & Assert HTTP
        mockMvc.perform(
            post("/api/v1/contact/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
        )
        .andExpect(status().isCreated())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.id").exists());

        // Assert DB
        assertTrue(contactMessageRepository.findAll().stream()
            .anyMatch(m -> "fail@test.com".equals(m.getEmail())));

        
        // Assert DB - failed notification delivery persisted asynchronously
        await().atMost(5, SECONDS).pollInterval(100, MILLISECONDS).untilAsserted(() -> {
            var deliveries = notificationDeliveryJpaRepository.findAll();

            System.out.println("---- FAILED TEST DELIVERIES ----");
            deliveries.forEach(d -> System.out.println(
                "id=" + d.getId()
                + ", eventId=" + d.getEventId()
                + ", channel=" + d.getChannel()
                + ", status=" + d.getStatus()
                + ", attempts=" + d.getAttempts()
                + ", lastError=" + d.getLastError()
            ));
            assertTrue(deliveries.stream().anyMatch(d ->
                d.getChannel() == NotificationChannel.TELEGRAM &&
                d.getStatus() == NotificationStatus.FAILED &&
                d.getPayloadSnapshot() != null &&
                d.getPayloadSnapshot().contains("fail@test.com") &&
                d.getLastError() != null &&
                d.getLastError().contains("telegram down")
            ));
        });
    }
}