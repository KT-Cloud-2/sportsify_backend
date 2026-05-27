package com.sportsify.notification.infrastructure;

import com.sportsify.common.event.NotificationPermanentlyFailedEvent;
import com.sportsify.common.notification.NotificationEventType;
import com.sportsify.notification.application.service.EventStatusService;
import com.sportsify.notification.application.service.FanoutService;
import com.sportsify.notification.domain.model.NotificationEvent;
import com.sportsify.notification.domain.repository.NotificationEventRepository;
import com.sportsify.notification.infrastructure.config.NotificationProperties;
import com.sportsify.notification.infrastructure.config.RedisStreamsConfig;
import com.sportsify.notification.infrastructure.consumer.PelMessageProcessor;
import com.sportsify.notification.infrastructure.publisher.RedisStreamNotificationEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PelMessageProcessorTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private FanoutService fanoutService;
    @Mock private EventStatusService statusService;
    @Mock private NotificationEventRepository eventRepository;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private StreamOperations<String, Object, Object> streamOps;

    private PelMessageProcessor processor;
    private static final String STREAM_KEY = "payment.completed";

    @BeforeEach
    void setUp() {
        NotificationProperties properties = new NotificationProperties(
                new NotificationProperties.Retry(3),
                new NotificationProperties.Pel(Duration.ofMinutes(10), 100, Duration.ofMinutes(10), List.of(3, 5, 10)),
                new NotificationProperties.Stream(10000),
                new NotificationProperties.Scheduler("0 0/5 * * * *", "0 0 3 * * *", "0 0/10 * * * *", "0 0/1 * * * *", Duration.ofSeconds(310)),
                new NotificationProperties.Slack("", "", Duration.ofMinutes(10))
        );
        processor = new PelMessageProcessor(redisTemplate, fanoutService, statusService, eventRepository, properties, eventPublisher);
    }

    @Test
    @DisplayName("예약 이벤트이면 fanout 없이 ACK 처리한다")
    void process_예약이벤트_ACK처리() {
        NotificationEvent scheduledEvent = NotificationEvent.scheduledWithId(
                1L, NotificationEventType.PAYMENT_COMPLETED, "{}", LocalDateTime.now().plusHours(1));
        MapRecord<String, Object, Object> message = record("1-0");
        given(redisTemplate.opsForStream()).willReturn(streamOps);
        given(statusService.saveEventWithStreamMessageId(any(), any(), any())).willReturn(scheduledEvent);

        processor.process(STREAM_KEY, NotificationEventType.PAYMENT_COMPLETED, message);

        verify(streamOps).acknowledge(STREAM_KEY, RedisStreamsConfig.NOTIFICATION_GROUP, RecordId.of("1-0"));
        verify(fanoutService, never()).fanout(any(), any(), any());
    }

    @Test
    @DisplayName("재시도 소진 시 PERMANENTLY_FAILED 처리 후 ACK한다")
    void process_재시도소진_영구실패처리() {
        NotificationEvent event = eventWithRetry(2);
        MapRecord<String, Object, Object> message = record("2-0");
        given(redisTemplate.opsForStream()).willReturn(streamOps);
        given(statusService.saveEventWithStreamMessageId(any(), any(), any())).willReturn(event);
        given(eventRepository.save(any())).willReturn(event);

        processor.process(STREAM_KEY, NotificationEventType.PAYMENT_COMPLETED, message);

        verify(eventRepository).save(event);
        verify(streamOps).acknowledge(STREAM_KEY, RedisStreamsConfig.NOTIFICATION_GROUP, RecordId.of("2-0"));
        verify(fanoutService, never()).fanout(any(), any(), any());

        ArgumentCaptor<NotificationPermanentlyFailedEvent> captor = ArgumentCaptor.forClass(NotificationPermanentlyFailedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().source()).isEqualTo("PEL");
    }

    @Test
    @DisplayName("fanout 실패 시 ACK하지 않는다 (PEL에 남겨 재시도)")
    void process_fanout실패_ACK미처리() {
        NotificationEvent event = eventWithRetry(0);
        MapRecord<String, Object, Object> message = record("3-0");
        given(statusService.saveEventWithStreamMessageId(any(), any(), any())).willReturn(event);
        given(eventRepository.save(any())).willReturn(event);
        given(fanoutService.fanout(any(), any(), any())).willReturn(true);

        processor.process(STREAM_KEY, NotificationEventType.PAYMENT_COMPLETED, message);

        verify(redisTemplate, never()).opsForStream();
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("fanout 성공 시 ACK한다")
    void process_fanout성공_ACK처리() {
        NotificationEvent event = eventWithRetry(0);
        MapRecord<String, Object, Object> message = record("4-0");
        given(redisTemplate.opsForStream()).willReturn(streamOps);
        given(statusService.saveEventWithStreamMessageId(any(), any(), any())).willReturn(event);
        given(eventRepository.save(any())).willReturn(event);
        given(fanoutService.fanout(any(), any(), any())).willReturn(false);

        processor.process(STREAM_KEY, NotificationEventType.PAYMENT_COMPLETED, message);

        verify(streamOps).acknowledge(STREAM_KEY, RedisStreamsConfig.NOTIFICATION_GROUP, RecordId.of("4-0"));
    }

    @Test
    @DisplayName("resolveBackoff는 retryCount에 따라 올바른 백오프를 반환한다")
    void resolveBackoff_retryCount별_백오프반환() {
        assertThat(processor.resolveBackoff(0)).isEqualTo(Duration.ofMinutes(3));
        assertThat(processor.resolveBackoff(1)).isEqualTo(Duration.ofMinutes(5));
        assertThat(processor.resolveBackoff(2)).isEqualTo(Duration.ofMinutes(10));
        assertThat(processor.resolveBackoff(99)).isEqualTo(Duration.ofMinutes(10));
    }

    @Test
    @DisplayName("예외 발생 시 조용히 처리되어 다른 메시지 처리에 영향을 주지 않는다")
    void process_예외발생_조용히처리() {
        MapRecord<String, Object, Object> message = record("5-0");
        given(statusService.saveEventWithStreamMessageId(any(), any(), any()))
                .willThrow(new RuntimeException("DB 오류"));

        processor.process(STREAM_KEY, NotificationEventType.PAYMENT_COMPLETED, message);

        verify(redisTemplate, never()).opsForStream();
    }

    private NotificationEvent eventWithRetry(int initialRetry) {
        NotificationEvent event = NotificationEvent.withId(10L, NotificationEventType.PAYMENT_COMPLETED, "{}");
        for (int i = 0; i < initialRetry; i++) {
            event.incrementRetry();
        }
        return event;
    }

    private MapRecord<String, Object, Object> record(String id) {
        return MapRecord.create(STREAM_KEY, Map.<Object, Object>of(
                RedisStreamNotificationEventPublisher.PAYLOAD_KEY, "{}"
        )).withId(RecordId.of(id));
    }
}
