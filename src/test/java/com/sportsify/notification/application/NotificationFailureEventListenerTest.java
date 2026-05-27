package com.sportsify.notification.application;

import com.sportsify.common.event.NotificationPermanentlyFailedEvent;
import com.sportsify.common.notification.NotificationEventType;
import com.sportsify.notification.application.listener.FailureEventListener;
import com.sportsify.notification.infrastructure.config.NotificationProperties;
import com.sportsify.notification.infrastructure.slack.SlackNotifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FailureEventListenerTest {

    @Mock private SlackNotifier slackNotifier;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOps;

    private NotificationPermanentlyFailedEvent failedEvent;

    @BeforeEach
    void setUp() {
        failedEvent = new NotificationPermanentlyFailedEvent(
                1L, NotificationEventType.PAYMENT_COMPLETED, 3, "PEL", LocalDateTime.now());
    }

    @Test
    @DisplayName("webhookUrl이 비어 있으면 Slack 알림을 보내지 않는다")
    void onPermanentlyFailed_슬랙미설정_알림미발송() {
        FailureEventListener listener = listenerWithWebhook("");

        listener.onPermanentlyFailed(failedEvent);

        verify(slackNotifier, never()).send(any());
    }

    @Test
    @DisplayName("동일 eventType이 suppressTtl 내에 이미 발송되었으면 중복 Slack 알림을 보내지 않는다")
    void onPermanentlyFailed_TTL내중복_알림미발송() {
        given(redisTemplate.opsForValue()).willReturn(valueOps);
        given(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).willReturn(false);
        FailureEventListener listener = listenerWithWebhook("https://hooks.slack.com/test");

        listener.onPermanentlyFailed(failedEvent);

        verify(slackNotifier, never()).send(any());
    }

    @Test
    @DisplayName("suppressTtl 만료 후 첫 실패이면 Slack 알림을 보낸다")
    void onPermanentlyFailed_TTL만료후첫실패_알림발송() {
        given(redisTemplate.opsForValue()).willReturn(valueOps);
        given(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).willReturn(true);
        FailureEventListener listener = listenerWithWebhook("https://hooks.slack.com/test");

        listener.onPermanentlyFailed(failedEvent);

        verify(slackNotifier).send(contains("PAYMENT_COMPLETED"));
    }

    @Test
    @DisplayName("Slack 메시지에 eventId, eventType, retryCount, source가 포함된다")
    void onPermanentlyFailed_메시지내용_검증() {
        given(redisTemplate.opsForValue()).willReturn(valueOps);
        given(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).willReturn(true);
        FailureEventListener listener = listenerWithWebhook("https://hooks.slack.com/test");

        listener.onPermanentlyFailed(failedEvent);

        verify(slackNotifier).send(argThat(msg ->
                msg.contains("1") &&
                msg.contains("PAYMENT_COMPLETED") &&
                msg.contains("3") &&
                msg.contains("PEL")
        ));
    }

    private FailureEventListener listenerWithWebhook(String webhookUrl) {
        NotificationProperties properties = new NotificationProperties(
                new NotificationProperties.Retry(3),
                new NotificationProperties.Pel(Duration.ofMinutes(10), 100, Duration.ofMinutes(10), List.of(3, 5, 10)),
                new NotificationProperties.Stream(10000),
                new NotificationProperties.Scheduler("0 0/5 * * * *", "0 0 3 * * *", "0 0/10 * * * *", "0 0/1 * * * *", Duration.ofSeconds(310)),
                new NotificationProperties.Slack(webhookUrl, "secret", Duration.ofMinutes(10))
        );
        return new FailureEventListener(slackNotifier, properties, redisTemplate);
    }
}
