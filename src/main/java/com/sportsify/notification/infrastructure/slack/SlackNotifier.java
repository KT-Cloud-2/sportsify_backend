package com.sportsify.notification.infrastructure.slack;

import com.sportsify.notification.infrastructure.config.NotificationProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
public class SlackNotifier {

    private final NotificationProperties properties;
    private final RestClient restClient;

    public SlackNotifier(NotificationProperties properties,
                         @Qualifier("slackRestClient") RestClient restClient) {
        this.properties = properties;
        this.restClient = restClient;
    }

    public void send(String message) {
        try {
            String body = "{\"text\":\"" + escapeJson(message) + "\"}";
            restClient.post()
                    .uri(properties.slack().webhookUrl())
                    .header("Content-Type", "application/json")
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Slack 알람 발송 완료");
        } catch (Exception e) {
            log.error("Slack 알람 발송 실패 error={}", e.getMessage());
        }
    }

    private String escapeJson(String text) {
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n");
    }
}
