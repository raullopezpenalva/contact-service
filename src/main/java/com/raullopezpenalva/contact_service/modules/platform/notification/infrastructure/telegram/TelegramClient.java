package com.raullopezpenalva.contact_service.modules.platform.notification.infrastructure.telegram;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.http.MediaType;

@Component
public class TelegramClient {

    private final RestClient restClient;
    private final TelegramProperties props;

    public TelegramClient(TelegramProperties props) {
        this.props = props;
        this.restClient = RestClient.builder()
            .baseUrl(props.apiBaseUrl())
            .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .build();
    }
    
    public void sendMessage(String text) {
        String path = "/bot" + props.botToken() + "/sendMessage";

        TelegramSendMessageRequest body = new TelegramSendMessageRequest(
            props.chatId(),
            text,
            true
        );

        restClient.post()
            .uri(path)
            .body(body)
            .retrieve()
            .toBodilessEntity();
    }
}
