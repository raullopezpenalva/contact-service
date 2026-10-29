package com.raullopezpenalva.contact_service;

import java.util.*;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import com.raullopezpenalva.contact_service.modules.platform.notification.infrastructure.emailSender.EmailClient;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EmailClientTest {
    @Test void buildsMimeMessageWithReplyToAndSends() throws Exception {
        var sender = mock(JavaMailSender.class);
        var mime = new MimeMessage(Session.getInstance(new Properties()));
        when(sender.createMimeMessage()).thenReturn(mime);
        var client = new EmailClient(sender);
        ReflectionTestUtils.setField(client, "sender", "service@example.com");
        ReflectionTestUtils.setField(client, "recipient", "owner@example.com");
        client.sendEmail("visitor@example.com", "Hello", "Message body");
        verify(sender).send(mime);
        assertEquals("Hello", mime.getSubject());
        assertEquals("visitor@example.com", mime.getReplyTo()[0].toString());
        assertEquals("owner@example.com", mime.getAllRecipients()[0].toString());
        assertEquals("service@example.com", mime.getFrom()[0].toString());
    }

    @Test void propagatesSmtpFailureToDeliveryOrchestration() {
        var sender = mock(JavaMailSender.class);
        var mime = new MimeMessage(Session.getInstance(new Properties()));
        when(sender.createMimeMessage()).thenReturn(mime);
        doThrow(new MailSendException("SMTP unavailable")).when(sender).send(mime);
        var client = new EmailClient(sender);
        ReflectionTestUtils.setField(client, "sender", "service@example.com");
        ReflectionTestUtils.setField(client, "recipient", "owner@example.com");
        var error = assertThrows(IllegalStateException.class,
            () -> client.sendEmail("visitor@example.com", "Hello", "Message body"));
        assertEquals("Email delivery failed", error.getMessage());
        assertInstanceOf(MailSendException.class, error.getCause());
    }
}
