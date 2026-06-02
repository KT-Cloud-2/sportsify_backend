package com.sportsify.notification.application;

import com.sportsify.common.notification.NotificationEventType;
import com.sportsify.notification.application.service.PayloadParser;
import com.sportsify.notification.support.NotificationIntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class PayloadParserTest {

    private PayloadParser parser;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = JsonMapper.builder().build();
        parser = new PayloadParser(objectMapper, NotificationIntegrationTestSupport.defaultProperties());
    }

    @Test
    @DisplayName("saleStartAt이 미래이면 예약 시각을 반환한다")
    void extractScheduledAt_TICKET_OPEN_미래시각_예약반환() {
        LocalDateTime future = LocalDateTime.now().plusHours(2);
        String payload = ticketOpenPayload(future);

        Optional<LocalDateTime> result = parser.extractScheduledAt(payload, NotificationEventType.TICKET_OPEN);

        assertThat(result).isPresent();
        assertThat(result.get()).isEqualToIgnoringNanos(future);
    }

    @Test
    @DisplayName("saleStartAt이 과거이면 즉시 발송을 위해 빈 Optional을 반환한다")
    void extractScheduledAt_TICKET_OPEN_과거시각_즉시발송() {
        LocalDateTime past = LocalDateTime.now().minusMinutes(1);
        String payload = ticketOpenPayload(past);

        Optional<LocalDateTime> result = parser.extractScheduledAt(payload, NotificationEventType.TICKET_OPEN);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("gameStartAt 30분 전 오프셋이 과거이면 즉시 발송을 위해 빈 Optional을 반환한다")
    void extractScheduledAt_GAME_START_오프셋후_과거시각_즉시발송() {
        // gameStartAt이 20분 뒤 → 오프셋(30분 전) = 10분 전 → 과거
        LocalDateTime gameStartAt = LocalDateTime.now().plusMinutes(20);
        String payload = gameStartPayload(gameStartAt);

        Optional<LocalDateTime> result = parser.extractScheduledAt(payload, NotificationEventType.GAME_START);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("gameStartAt 30분 전 오프셋이 미래이면 예약 시각을 반환한다")
    void extractScheduledAt_GAME_START_오프셋후_미래시각_예약반환() {
        // gameStartAt이 2시간 뒤 → 오프셋(30분 전) = 90분 뒤 → 미래
        LocalDateTime gameStartAt = LocalDateTime.now().plusHours(2);
        String payload = gameStartPayload(gameStartAt);

        Optional<LocalDateTime> result = parser.extractScheduledAt(payload, NotificationEventType.GAME_START);

        assertThat(result).isPresent();
    }

    private String ticketOpenPayload(LocalDateTime saleStartAt) {
        return String.format(
                "{\"gameId\":1,\"homeTeam\":\"A\",\"awayTeam\":\"B\",\"saleStartAt\":\"%s\",\"gameStartAt\":null}",
                saleStartAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
    }

    private String gameStartPayload(LocalDateTime gameStartAt) {
        return String.format(
                "{\"gameId\":1,\"homeTeam\":\"A\",\"awayTeam\":\"B\",\"gameStartAt\":\"%s\"}",
                gameStartAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
    }
}
