package com.sportsify.notification.infrastructure.consumer;

import com.sportsify.common.event.NotificationPermanentlyFailedEvent;
import com.sportsify.common.notification.NotificationEventType;
import com.sportsify.notification.application.service.EventStatusService;
import com.sportsify.notification.application.service.FanoutService;
import com.sportsify.notification.application.service.PayloadParser;
import com.sportsify.notification.domain.model.Notification;
import com.sportsify.notification.domain.model.NotificationEvent;
import com.sportsify.notification.domain.model.NotificationEventStatus;
import com.sportsify.notification.domain.repository.NotificationEventRepository;
import com.sportsify.notification.domain.repository.NotificationRepository;
import com.sportsify.notification.infrastructure.config.NotificationProperties;
import com.sportsify.notification.infrastructure.config.RedisStreamsConfig;
import com.sportsify.notification.infrastructure.publisher.RedisStreamNotificationEventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Slf4j
@Component
public class PelMessageProcessor {

    private final StringRedisTemplate redisTemplate;
    private final FanoutService fanoutService;
    private final EventStatusService statusService;
    private final NotificationEventRepository eventRepository;
    private final NotificationRepository notificationRepository;
    private final PayloadParser payloadParser;
    private final NotificationProperties properties;
    private final ApplicationEventPublisher eventPublisher;
    private final List<Integer> backoffMinutes;

    public PelMessageProcessor(StringRedisTemplate redisTemplate,
                                FanoutService fanoutService,
                                EventStatusService statusService,
                                NotificationEventRepository eventRepository,
                                NotificationRepository notificationRepository,
                                PayloadParser payloadParser,
                                NotificationProperties properties,
                                ApplicationEventPublisher eventPublisher) {
        this.redisTemplate = redisTemplate;
        this.fanoutService = fanoutService;
        this.statusService = statusService;
        this.eventRepository = eventRepository;
        this.notificationRepository = notificationRepository;
        this.payloadParser = payloadParser;
        this.properties = properties;
        this.eventPublisher = eventPublisher;
        this.backoffMinutes = properties.pel().backoffMinutes();
    }

    public void process(String streamKey, NotificationEventType eventType, MapRecord<String, Object, Object> message) {
        try {
            String payload = String.valueOf(message.getValue().get(RedisStreamNotificationEventPublisher.PAYLOAD_KEY));
            String streamMessageId = message.getId().getValue();
            NotificationEvent event = statusService.saveEventWithStreamMessageId(eventType, payload, streamMessageId);

            if (isAlreadyResolved(event)) {
                acknowledge(streamKey, message);
                log.info("PEL 이벤트 ACK (이미 처리됨) eventId={} status={}", event.getId(), event.getStatus());
                return;
            }

            if (isScheduledPending(event)) {
                acknowledge(streamKey, message);
                log.info("PEL 이벤트 ACK (예약 미도래) streamKey={} id={}", streamKey, message.getId());
                return;
            }

            boolean failed = fanoutService.fanout(event, eventType, payload);
            if (!failed) {
                statusService.markEventStatus(event.getId(), false);
                acknowledge(streamKey, message);
                log.info("PEL 재처리 완료 streamKey={} id={}", streamKey, message.getId());
                return;
            }

            if (isExhausted(event, streamKey, message)) {
                return;
            }

            log.warn("PEL 재처리 발송 실패 streamKey={} id={} retryCount={}", streamKey, message.getId(), event.getRetryCount());
        } catch (Exception e) {
            log.error("PEL 재처리 실패 streamKey={} id={} error={}", streamKey, message.getId(), e.getMessage());
        }
    }

    public Duration resolveBackoff(int retryCount) {
        int index = Math.min(retryCount, backoffMinutes.size() - 1);
        return Duration.ofMinutes(backoffMinutes.get(index));
    }

    private boolean isExhausted(NotificationEvent event, String streamKey, MapRecord<String, Object, Object> message) {
        boolean exhausted = event.incrementRetryAndCheckExhausted(backoffMinutes.size());
        if (exhausted) {
            event.markPermanentlyFailed();
        }
        eventRepository.save(event);

        if (exhausted) {
            saveNotificationForPermanentlyFailed(event);
            acknowledge(streamKey, message);
            log.error("PEL 재처리 모두 소진, 영구 실패 처리 eventId={} retryCount={}", event.getId(), event.getRetryCount());
            eventPublisher.publishEvent(NotificationPermanentlyFailedEvent.of(event.getId(), event.getEventType(), event.getRetryCount(), "PEL", event.getPayload()));
        }
        return exhausted;
    }

    private void saveNotificationForPermanentlyFailed(NotificationEvent event) {
        if (!event.getEventType().isSingleTarget()) {
            return;
        }
        try {
            Long memberId = payloadParser.extractMemberId(event.getPayload(), event.getTypeName());
            if (notificationRepository.existsByEventIdAndMemberId(event.getId(), memberId)) return;
            notificationRepository.save(Notification.create(memberId, event.getId()));
        } catch (Exception e) {
            log.error("영구 실패 알림 기록 저장 실패 eventId={}", event.getId(), e);
        }
    }

    private boolean isAlreadyResolved(NotificationEvent event) {
        NotificationEventStatus status = event.getStatus();
        return status == NotificationEventStatus.PUBLISHED || status == NotificationEventStatus.PERMANENTLY_FAILED;
    }

    private boolean isScheduledPending(NotificationEvent event) {
        return event.isScheduled() && event.getStatus() == NotificationEventStatus.PENDING;
    }

    private void acknowledge(String streamKey, MapRecord<String, Object, Object> message) {
        redisTemplate.opsForStream().acknowledge(streamKey, RedisStreamsConfig.NOTIFICATION_GROUP, message.getId());
    }
}
