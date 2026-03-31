package com.raullopezpenalva.contact_service.modules.platform.notification.infrastructure.emailSender;

import org.springframework.stereotype.Component;

import com.raullopezpenalva.contact_service.modules.platform.notification.application.model.NotificationMessage;
import com.raullopezpenalva.contact_service.modules.platform.notification.application.port.out.NotificationGateway;
import com.raullopezpenalva.contact_service.modules.platform.notification.domain.model.NotificationChannel;

@Component
public class EmailNotificationChannel implements NotificationGateway {

    private final EmailClient emailClient;

    public EmailNotificationChannel(EmailClient emailClient) {
        this.emailClient = emailClient;
    }

    @Override
    public NotificationChannel getChannel() {
        return NotificationChannel.EMAIL;
    }
    
    @Override
    public void sendNotification(NotificationMessage message) {
        String subject = "New contact request received!";
        String body = buildMessageBody(message);
        emailClient.sendEmail(subject, body);
    }

    private String buildMessageBody(NotificationMessage message) {
        return """
            From: %s
            Subject: %s
            Message: %s
        """.formatted(
            message.getEmail(),
            message.getSubject(),
            message.getMessage()
        );
    }
    
}
