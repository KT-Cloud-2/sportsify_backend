package com.sportsify.notification.infrastructure.consumer;

import com.sportsify.notification.application.service.NotificationEventProcessor;
import com.sportsify.notification.application.service.NotificationEventStatusService;
import com.sportsify.common.notification.NotificationEventType;
import com.sportsify.notification.domain.model.NotificationEvent;
import com.sportsify.notification.infrastructure.config.RedisStreamsConfig;
import com.sportsify.notification.infrastructure.publisher.RedisStreamNotificationEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessagesSummary;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationStreamMaintenanceScheduler {

    private static final Duration PEL_CLAIM_MIN_IDLE = Duration.ofMinutes(10);
    private static final int PEL_BATCH_SIZE = 100;

    private final StringRedisTemplate redisTemplate;
    private final NotificationEventProcessor eventProcessor;
    private final NotificationEventStatusService statusService;

    private static final Map<String, NotificationEventType> STREAM_TO_EVENT =
            NotificationEventType.streamKeyMap();

    @Scheduled(fixedDelay = 600_000)
    public void reclaimPendingMessages() {
        STREAM_TO_EVENT.forEach(this::reclaimForStream);
    }

    @Scheduled(cron = "0 0 3 * * *")
    public void trimStreams() {
        for (String streamKey : RedisStreamsConfig.STREAM_KEYS) {
            Long removed = redisTemplate.opsForStream()
                    .trim(streamKey, RedisStreamsConfig.STREAM_MAX_LEN, true);
            log.info("Stream trimmed streamKey={} removed={}", streamKey, removed);
        }
    }

    private void reclaimForStream(String streamKey, NotificationEventType eventType) {
        try {
            PendingMessagesSummary summary = redisTemplate.opsForStream()
                    .pending(streamKey, RedisStreamsConfig.NOTIFICATION_GROUP);

            if (summary == null || summary.getTotalPendingMessages() == 0) {
                return;
            }

            List<PendingMessage> pending = redisTemplate.opsForStream()
                    .pending(streamKey, RedisStreamsConfig.NOTIFICATION_GROUP, Range.unbounded(), PEL_BATCH_SIZE)
                    .stream()
                    .filter(msg -> msg.getElapsedTimeSinceLastDelivery().compareTo(PEL_CLAIM_MIN_IDLE) >= 0)
                    .toList();

            if (pending.isEmpty()) {
                return;
            }

            RecordId[] ids = pending.stream()
                    .map(PendingMessage::getIdAsString)
                    .map(RecordId::of)
                    .toArray(RecordId[]::new);

            List<MapRecord<String, Object, Object>> reclaimed = redisTemplate.opsForStream()
                    .claim(streamKey, RedisStreamsConfig.NOTIFICATION_GROUP, "maintenance-consumer",
                            PEL_CLAIM_MIN_IDLE, ids);

            for (MapRecord<String, Object, Object> message : reclaimed) {
                processReclaimedMessage(streamKey, eventType, message);
            }
        } catch (Exception e) {
            log.error("PEL 재처리 스케줄러 오류 streamKey={} error={}", streamKey, e.getMessage());
        }
    }

    private void processReclaimedMessage(String streamKey, NotificationEventType eventType,
                                         MapRecord<String, Object, Object> message) {
        try {
            String payload = String.valueOf(message.getValue().get(RedisStreamNotificationEventPublisher.PAYLOAD_KEY));
            String streamMessageId = message.getId().getValue();
            NotificationEvent event = statusService.saveEventWithStreamMessageId(eventType, payload, streamMessageId);
            if (!event.isScheduled()) {
                eventProcessor.fanout(event, eventType, payload);
            }
            redisTemplate.opsForStream().acknowledge(streamKey, RedisStreamsConfig.NOTIFICATION_GROUP, message.getId());
            log.info("PEL 재처리 완료 streamKey={} id={}", streamKey, message.getId());
        } catch (Exception e) {
            log.error("PEL 재처리 실패 streamKey={} id={} error={}", streamKey, message.getId(), e.getMessage());
        }
    }
}
