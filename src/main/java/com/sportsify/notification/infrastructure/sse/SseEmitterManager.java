package com.sportsify.notification.infrastructure.sse;

import com.sportsify.notification.application.port.SseNotificationPort;
import com.sportsify.notification.domain.model.NotificationChannel;
import com.sportsify.notification.domain.model.NotificationSetting;
import com.sportsify.notification.infrastructure.config.NotificationProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class SseEmitterManager implements SseNotificationPort {

    private final NotificationProperties properties;
    private final MeterRegistry meterRegistry;
    private final Executor sseVirtualThreadExecutor;

    private final Map<Long, SseSession> sessions = new ConcurrentHashMap<>();
    private Counter sentCounter;
    private Timer sendDurationTimer;

    @PostConstruct
    void initMetrics() {
        Gauge.builder("sse_active_connections", sessions, Map::size)
                .register(meterRegistry);
        sentCounter = Counter.builder("sse_sent_total")
                .register(meterRegistry);
        sendDurationTimer = Timer.builder("sse_send_duration")
                .register(meterRegistry);
    }

    @Override
    public SseEmitter subscribe(Long memberId, NotificationSetting setting, List<NotificationChannel> channels) {
        SseEmitter emitter = new SseEmitter(properties.sse().timeoutMs());
        SseSession previous = sessions.put(memberId, new SseSession(emitter, setting, channels));
        if (previous != null) {
            previous.emitter().complete();
        }
        emitter.onCompletion(() -> removeSession(memberId, emitter));
        emitter.onTimeout(() -> {
            removeSession(memberId, emitter);
            meterRegistry.counter("sse_errors_total", "reason", "timeout").increment();
        });
        emitter.onError(e -> {
            removeSession(memberId, emitter);
            meterRegistry.counter("sse_errors_total", "reason", "error").increment();
        });
        log.info("SSE subscribed memberId={}", memberId);
        return emitter;
    }

    @Override
    public void send(Long memberId, Object data) {
        Optional.ofNullable(sessions.get(memberId))
                .ifPresent(session -> sendToEmitter(memberId, session.emitter(), data));
    }

    @Scheduled(cron = "${notification.sse.ping-cron}")
    public void evictDeadSessions() {
        sessions.forEach((memberId, session) ->
                sseVirtualThreadExecutor.execute(() -> {
                    try {
                        session.emitter().send(SseEmitter.event().name("ping").data(""));
                    } catch (Exception e) {
                        removeSession(memberId, session.emitter());
                    }
                })
        );
    }

    private void sendToEmitter(Long memberId, SseEmitter emitter, Object data) {
        long start = System.nanoTime();
        try {
            emitter.send(SseEmitter.event().name("notification").data(data));
            sentCounter.increment();
        } catch (IOException e) {
            removeSession(memberId, emitter);
            meterRegistry.counter("sse_errors_total", "reason", "io_error").increment();
            log.warn("SSE send failed memberId={}", memberId);
        } finally {
            sendDurationTimer.record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
        }
    }

    private void removeSession(Long memberId, SseEmitter emitter) {
        sessions.compute(memberId, (id, current) -> {
            if (current != null && current.emitter() == emitter) {
                return null;
            }
            return current;
        });
    }

    public boolean isConnected(Long memberId) {
        return sessions.containsKey(memberId);
    }


    @Override
    public List<NotificationChannel> getCachedChannels(Long memberId) {
        SseSession session = sessions.get(memberId);
        return session != null ? session.channels() : List.of();
    }

    public void unsubscribe(Long memberId) {
        sessions.remove(memberId);
    }

    private record SseSession(SseEmitter emitter, NotificationSetting setting, List<NotificationChannel> channels) {}
}
