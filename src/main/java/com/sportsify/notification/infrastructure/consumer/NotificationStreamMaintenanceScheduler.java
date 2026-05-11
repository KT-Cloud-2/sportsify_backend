package com.sportsify.notification.infrastructure.consumer;

import com.sportsify.notification.application.service.NotificationEventProcessor;
import com.sportsify.notification.domain.model.NotificationEventType;
import com.sportsify.notification.infrastructure.config.RedisStreamsConfig;
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
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationStreamMaintenanceScheduler {

    private static final Duration PEL_CLAIM_MIN_IDLE = Duration.ofMinutes(10);
    private static final int PEL_BATCH_SIZE = 100;

    private final StringRedisTemplate redisTemplate;
    private final NotificationEventProcessor processor;

    private static final Map<String, NotificationEventType> STREAM_TO_EVENT =
            Stream.of(NotificationEventType.values())
                    .collect(Collectors.toMap(NotificationEventType::getStreamKey, e -> e));

    @Scheduled(fixedDelay = 600_000)
    public void reclaimPendingMessages() {
        for (Map.Entry<String, NotificationEventType> entry : STREAM_TO_EVENT.entrySet()) {
            String streamKey = entry.getKey();
            NotificationEventType eventType = entry.getValue();
            reclaimForStream(streamKey, eventType);
        }
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
                try {
                    String payload = String.valueOf(message.getValue().values().iterator().next());
                    processor.process(eventType, payload);
                    redisTemplate.opsForStream().acknowledge(streamKey, RedisStreamsConfig.NOTIFICATION_GROUP, message.getId());
                    log.info("PEL 재처리 완료 streamKey={} id={}", streamKey, message.getId());
                } catch (Exception e) {
                    log.error("PEL 재처리 실패 streamKey={} id={} error={}", streamKey, message.getId(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("PEL 재처리 스케줄러 오류 streamKey={} error={}", streamKey, e.getMessage());
        }
    }
}
