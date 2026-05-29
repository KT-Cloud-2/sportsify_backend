package com.sportsify.notification.infrastructure;

import com.sportsify.common.event.NotificationPermanentlyFailedEvent;
import com.sportsify.common.notification.NotificationEventType;
import com.sportsify.notification.application.service.EventStatusService;
import com.sportsify.notification.application.service.FanoutService;
import com.sportsify.notification.application.service.PayloadParser;
import com.sportsify.notification.domain.model.NotificationEvent;
import com.sportsify.notification.domain.repository.NotificationEventRepository;
import com.sportsify.notification.domain.repository.NotificationRepository;
import com.sportsify.notification.infrastructure.config.NotificationProperties;
import com.sportsify.notification.infrastructure.config.RedisStreamsConfig;
import com.sportsify.notification.infrastructure.consumer.PelMessageProcessor;
import com.sportsify.notification.infrastructure.publisher.RedisStreamNotificationEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import com.sportsify.notification.support.NotificationIntegrationTestSupport;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
    @Mock private NotificationRepository notificationRepository;
    @Mock private PayloadParser payloadParser;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private StreamOperations<String, Object, Object> streamOps;

    private PelMessageProcessor processor;
    private static final String STREAM_KEY = "payment.completed";

    @BeforeEach
    void setUp() {
        processor = new PelMessageProcessor(
                redisTemplate, fanoutService, statusService, eventRepository,
                notificationRepository, payloadParser,
                NotificationIntegrationTestSupport.defaultProperties(), eventPublisher);
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
        NotificationEvent event = eventWithRetry(3);
        MapRecord<String, Object, Object> message = record("2-0");
        given(redisTemplate.opsForStream()).willReturn(streamOps);
        given(statusService.saveEventWithStreamMessageId(any(), any(), any())).willReturn(event);
        given(fanoutService.fanout(any(), any(), any())).willReturn(true);
        given(eventRepository.save(any())).willReturn(event);

        processor.process(STREAM_KEY, NotificationEventType.PAYMENT_COMPLETED, message);

        verify(eventRepository).save(event);
        verify(streamOps).acknowledge(STREAM_KEY, RedisStreamsConfig.NOTIFICATION_GROUP, RecordId.of("2-0"));

        ArgumentCaptor<NotificationPermanentlyFailedEvent> captor =
                ArgumentCaptor.forClass(NotificationPermanentlyFailedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().source()).isEqualTo("PEL");
    }

    @Test
    @DisplayName("fanout 실패 시 retry 증분 후 ACK하지 않는다 (PEL에 남겨 재시도)")
    void process_fanout실패_ACK미처리() {
        NotificationEvent event = eventWithRetry(0);
        MapRecord<String, Object, Object> message = record("3-0");
        given(statusService.saveEventWithStreamMessageId(any(), any(), any())).willReturn(event);
        given(fanoutService.fanout(any(), any(), any())).willReturn(true);
        given(eventRepository.save(any())).willReturn(event);

        processor.process(STREAM_KEY, NotificationEventType.PAYMENT_COMPLETED, message);

        verify(eventRepository).save(event);
        verify(streamOps, never()).acknowledge(anyString(), anyString(), any(RecordId.class));
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("fanout 성공 시 PUBLISHED 마킹 후 ACK한다")
    void process_fanout성공_ACK처리() {
        NotificationEvent event = eventWithRetry(0);
        MapRecord<String, Object, Object> message = record("4-0");
        given(redisTemplate.opsForStream()).willReturn(streamOps);
        given(statusService.saveEventWithStreamMessageId(any(), any(), any())).willReturn(event);
        given(fanoutService.fanout(any(), any(), any())).willReturn(false);

        processor.process(STREAM_KEY, NotificationEventType.PAYMENT_COMPLETED, message);

        verify(statusService).markEventStatus(event.getId(), false);
        verify(streamOps).acknowledge(STREAM_KEY, RedisStreamsConfig.NOTIFICATION_GROUP, RecordId.of("4-0"));
    }

    @ParameterizedTest(name = "retryCount={0} → {1}분")
    @CsvSource({"0,1", "1,3", "2,5", "3,10", "99,10"})
    @DisplayName("resolveBackoff는 retryCount에 따라 올바른 백오프를 반환한다")
    void resolveBackoff_retryCount별_백오프반환(int retryCount, int expectedMinutes) {
        assertThat(processor.resolveBackoff(retryCount))
                .isEqualTo(Duration.ofMinutes(expectedMinutes));
    }

    @Test
    @DisplayName("예외 발생 시 조용히 처리되어 다른 메시지 처리에 영향을 주지 않는다")
    void process_예외발생_조용히처리() {
        MapRecord<String, Object, Object> message = record("5-0");
        given(statusService.saveEventWithStreamMessageId(any(), any(), any()))
                .willThrow(new RuntimeException("DB 오류"));

        processor.process(STREAM_KEY, NotificationEventType.PAYMENT_COMPLETED, message);

        verify(streamOps, never()).acknowledge(anyString(), anyString(), any(RecordId.class));
    }

    private NotificationEvent eventWithRetry(int initialRetry) {
        NotificationEvent event = NotificationEvent.withId(10L, NotificationEventType.PAYMENT_COMPLETED, "{}");
        for (int i = 0; i < initialRetry; i++) {
            event.incrementRetry();
        }
        return event;
    }

    private MapRecord<String, Object, Object> record(String id) {
        Map<Object, Object> body = Map.of(RedisStreamNotificationEventPublisher.PAYLOAD_KEY, "{}");
        return MapRecord.create(STREAM_KEY, body).withId(RecordId.of(id));
    }


}
