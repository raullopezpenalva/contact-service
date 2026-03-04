package com.raullopezpenalva.contact_service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.raullopezpenalva.contact_service.modules.contact.infrastructure.repository.ContactMessageRepository;
import com.raullopezpenalva.contact_service.modules.platform.notification.application.service.NotificationChannel;
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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ContactControllerTest {
    
    @Autowired MockMvc mockMvc;
    @Autowired ContactMessageRepository contactMessageRepository;
    @Autowired ObjectMapper objectMapper;

    @MockitoBean NotificationChannel notificationChannel;

    @Test
    void createContact_shouldPersistAndTriggerNotification() throws Exception {
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

        // Assert DB
        assertTrue(contactMessageRepository.findAll().stream()
            .anyMatch(m -> "integration@test.com".equals(m.getEmail())));

        // Assert Notification called
        verify(notificationChannel, timeout(1000).times(1)).sendNotification(any());
    }

    @Test
    void createContact_shouldReturn201EvenIfNotificationFails() throws Exception {
        // Arrange
        doThrow(new RuntimeException("telegram down")).when(notificationChannel).sendNotification(any());

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

        // Assert Notification called
        verify(notificationChannel, timeout(1000).times(1)).sendNotification(any());
    }
}