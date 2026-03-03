package com.raullopezpenalva.contact_service.modules.platform.notification.infrastructure.telegram;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "telegram")
public record TelegramProperties(
    String botToken,
    String chatId,
    String apiBaseUrl
) {}
