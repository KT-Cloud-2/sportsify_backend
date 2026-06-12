package com.sportsify.notification.infrastructure.sse;

import com.sportsify.notification.application.port.SseNotificationPort;
import com.sportsify.notification.infrastructure.config.NotificationProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class SseEmitterManager implements SseNotificationPort {

    private final NotificationProperties properties;
    private final MeterRegistry meterRegistry;
    private final Map<Long, SseEmitter> emitters = new ConcurrentHashMap<>();

    private Counter sentCounter;
    private Timer sendDurationTimer;

    @PostConstruct
    void initMetrics() {
        Gauge.builder("sse_active_connections", emitters, Map::size)
                .description("Active SSE connections")
                .register(meterRegistry);
        sentCounter = Counter.builder("sse_sent_total")
                .description("SSE events sent successfully")
                .register(meterRegistry);
        sendDurationTimer = Timer.builder("sse_send_duration")
                .description("SSE send duration")
                .register(meterRegistry);
    }

    @Override
    public SseEmitter subscribe(Long memberId) {
        SseEmitter emitter = new SseEmitter(properties.sse().timeoutMs());
        SseEmitter previous = emitters.put(memberId, emitter);
        if (previous != null) {
            previous.complete();
        }
        emitter.onCompletion(() -> emitters.remove(memberId, emitter));
        emitter.onTimeout(() -> {
            emitters.remove(memberId, emitter);
            meterRegistry.counter("sse_errors_total", "reason", "timeout").increment();
        });
        emitter.onError(e -> {
            emitters.remove(memberId, emitter);
            meterRegistry.counter("sse_errors_total", "reason", "error").increment();
        });
        log.info("SSE subscribed memberId={}", memberId);
        return emitter;
    }

    @Override
    public void send(Long memberId, Object data) {
        Optional.ofNullable(emitters.get(memberId))
                .ifPresent(emitter -> sendToEmitter(memberId, emitter, data));
    }

    private void sendToEmitter(Long memberId, SseEmitter emitter, Object data) {
        long start = System.nanoTime();
        try {
            emitter.send(SseEmitter.event().name("notification").data(data));
            sentCounter.increment();
        } catch (IOException e) {
            emitters.remove(memberId);
            meterRegistry.counter("sse_errors_total", "reason", "io_error").increment();
            log.warn("SSE send failed memberId={}", memberId);
        } finally {
            sendDurationTimer.record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
        }
    }

    public boolean isConnected(Long memberId) {
        return emitters.containsKey(memberId);
    }

    public void unsubscribe(Long memberId) {
        emitters.remove(memberId);
    }
}
