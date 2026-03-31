package com.raullopezpenalva.contact_service.modules.platform.notification.infrastructure.telegram;

import com.raullopezpenalva.contact_service.modules.platform.notification.application.model.NotificationMessage;
import com.raullopezpenalva.contact_service.modules.platform.notification.application.port.out.NotificationGateway;

import org.springframework.stereotype.Component;;

@Component
public class TelegramNotificationChannel implements NotificationGateway {

    private final TelegramClient telegramClient;

    public TelegramNotificationChannel(TelegramClient telegramClient) {
        this.telegramClient = telegramClient;
    }

    @Override
    public void sendNotification(NotificationMessage message) {
        String text = buildMessageText(message);
        telegramClient.sendMessage(text);
    }

    private String buildMessageText(NotificationMessage message) {
        return """
            New contact request received!

            Email: %s
            Subject: %s
            Message: %s
        """.formatted(
            message.getEmail(),
            message.getSubject(),
            message.getMessage()
        );
    }    
}
