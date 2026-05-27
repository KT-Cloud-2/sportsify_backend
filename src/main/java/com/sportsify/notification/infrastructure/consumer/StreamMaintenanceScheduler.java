package com.sportsify.notification.infrastructure.consumer;

import com.sportsify.common.notification.NotificationEventType;
import com.sportsify.notification.infrastructure.config.NotificationProperties;
import com.sportsify.notification.infrastructure.config.RedisStreamsConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
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
public class StreamMaintenanceScheduler {

    private static final Map<String, NotificationEventType> STREAM_TO_EVENT =
            NotificationEventType.streamKeyMap();

    private final StringRedisTemplate redisTemplate;
    private final PelMessageProcessor pelMessageProcessor;
    private final NotificationProperties properties;

    @Scheduled(cron = "${notification.scheduler.pel-reclaim-cron}")
    public void reclaimPendingMessages() {
        STREAM_TO_EVENT.forEach(this::reclaimForStream);
    }

    @Scheduled(cron = "${notification.scheduler.stream-trim-cron}")
    public void trimStreams() {
        for (String streamKey : RedisStreamsConfig.STREAM_KEYS) {
            Long removed = redisTemplate.opsForStream()
                    .trim(streamKey, properties.stream().maxLen(), true);
            log.info("Stream trimmed streamKey={} removed={}", streamKey, removed);
        }
    }

    private void reclaimForStream(String streamKey, NotificationEventType eventType) {
        try {
            int batchSize = properties.pel().batchSize();

            List<PendingMessage> pending = redisTemplate.opsForStream()
                    .pending(streamKey, RedisStreamsConfig.NOTIFICATION_GROUP, Range.unbounded(), batchSize)
                    .stream()
                    .filter(this::isBackoffElapsed)
                    .toList();

            if (pending.isEmpty()) {
                return;
            }

            for (PendingMessage pendingMessage : pending) {
                Duration backoff = pelMessageProcessor.resolveBackoff((int) pendingMessage.getTotalDeliveryCount() - 1);
                RecordId id = RecordId.of(pendingMessage.getIdAsString());
                List<MapRecord<String, Object, Object>> reclaimed = redisTemplate.opsForStream()
                        .claim(streamKey, RedisStreamsConfig.NOTIFICATION_GROUP, "maintenance-consumer",
                                backoff, id);
                for (MapRecord<String, Object, Object> message : reclaimed) {
                    pelMessageProcessor.process(streamKey, eventType, message);
                }
            }
        } catch (Exception e) {
            log.error("PEL 재처리 스케줄러 오류 streamKey={} error={}", streamKey, e.getMessage());
        }
    }

    private boolean isBackoffElapsed(PendingMessage msg) {
        int deliveryCount = (int) msg.getTotalDeliveryCount();
        Duration backoff = pelMessageProcessor.resolveBackoff(deliveryCount - 1);
        return msg.getElapsedTimeSinceLastDelivery().compareTo(backoff) >= 0;
    }
}
