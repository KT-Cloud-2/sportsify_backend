package com.sportsify.notification.application.service;

import com.sportsify.notification.domain.model.NotificationBufferItem;
import com.sportsify.notification.infrastructure.config.NotificationProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import static com.sportsify.notification.infrastructure.config.RedisStreamsConfig.NOTIFICATION_GROUP;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationBatchBuffer {

    private final BulkPersistService bulkPersistService;
    private final StringRedisTemplate redisTemplate;
    private final NotificationProperties properties;

    private final ConcurrentLinkedQueue<NotificationBufferItem> queue = new ConcurrentLinkedQueue<>();
    private final AtomicInteger count = new AtomicInteger(0);
    private final ReentrantLock flushLock = new ReentrantLock();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
            r -> new Thread(r, "notif-buffer-flusher")
    );

    @PostConstruct
    void startFlushScheduler() {
        long intervalMs = properties.buffer().flushInterval().toMillis();
        scheduler.scheduleAtFixedRate(this::flush, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    void shutdown() {
        scheduler.shutdown();
        flush();
    }

    public void enqueue(NotificationBufferItem item) {
        queue.add(item);
        if (count.incrementAndGet() >= properties.buffer().maxSize()) {
            scheduler.execute(this::flush);
        }
    }

    private void flush() {
        if (!flushLock.tryLock()) {
            return;
        }
        try {
            if (queue.isEmpty()) {
                return;
            }
            List<NotificationBufferItem> batch = drain();
            if (batch.isEmpty()) {
                return;
            }
            bulkPersistService.persistBuffered(batch);
            ackAll(batch);
            log.info("버퍼 flush 완료 size={}", batch.size());
        } catch (Exception e) {
            log.error("버퍼 flush 실패 size={} — PEL 재처리 대기", queue.size(), e);
        } finally {
            flushLock.unlock();
        }
    }

    private List<NotificationBufferItem> drain() {
        List<NotificationBufferItem> batch = new ArrayList<>();
        NotificationBufferItem item;
        while ((item = queue.poll()) != null) {
            batch.add(item);
        }
        count.set(0);
        return batch;
    }

    private void ackAll(List<NotificationBufferItem> batch) {
        batch.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        NotificationBufferItem::streamKey,
                        java.util.stream.Collectors.mapping(i -> i.recordId().getValue(), java.util.stream.Collectors.toList())
                ))
                .forEach((streamKey, ids) ->
                        redisTemplate.opsForStream().acknowledge(
                                streamKey,
                                NOTIFICATION_GROUP,
                                ids.toArray(String[]::new)
                        )
                );
    }
}
