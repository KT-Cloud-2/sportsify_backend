package com.sportsify.notification.application.service;

import com.sportsify.common.notification.NotificationEventType;
import com.sportsify.notification.infrastructure.config.NotificationProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class PayloadParser {

    private final ObjectMapper objectMapper;
    private final NotificationProperties properties;

    public Long extractMemberId(String payload, String eventTypeName) {
        try {
            JsonNode node = objectMapper.readTree(payload);
            JsonNode memberIdNode = node.get("memberId");
            if (memberIdNode == null || memberIdNode.isNull() || !memberIdNode.isIntegralNumber()) {
                throw new IllegalArgumentException("invalid memberId in " + eventTypeName + " payload");
            }
            return memberIdNode.longValue();
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("invalid memberId in " + eventTypeName + " payload", e);
        }
    }

    public Optional<LocalDateTime> extractScheduledAt(String payload, NotificationEventType eventType) {
        try {
            JsonNode node = objectMapper.readTree(payload);
            LocalDateTime now = LocalDateTime.now();
            return switch (eventType) {
                case TICKET_OPEN -> parseDateTime(node, "saleStartAt")
                        .filter(t -> t.isAfter(now));
                case GAME_START -> parseDateTime(node, "gameStartAt")
                        .map(t -> t.minusMinutes(properties.payload().gameStartOffsetMinutes()))
                        .filter(t -> t.isAfter(now));
                default -> Optional.empty();
            };
        } catch (Exception e) {
            log.warn("scheduledAt 파싱 실패 eventType={} payload={}", eventType, payload, e);
            return Optional.empty();
        }
    }

    private Optional<LocalDateTime> parseDateTime(JsonNode node, String fieldName) {
        JsonNode fieldNode = node.get(fieldName);
        if (fieldNode == null || fieldNode.isNull()) {
            return Optional.empty();
        }
        return Optional.of(LocalDateTime.parse(fieldNode.asText()));
    }
}
