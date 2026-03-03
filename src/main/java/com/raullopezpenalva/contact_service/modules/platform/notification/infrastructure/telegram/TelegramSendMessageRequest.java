package com.raullopezpenalva.contact_service.modules.platform.notification.infrastructure.telegram;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TelegramSendMessageRequest(
    @JsonProperty("chat_id") String chatId,
    @JsonProperty("text") String text,
    @JsonProperty("disable_web_page_preview") boolean disableWebPagePreview
) {}