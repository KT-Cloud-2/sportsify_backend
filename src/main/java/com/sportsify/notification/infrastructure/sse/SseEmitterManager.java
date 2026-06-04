package com.sportsify.notification.infrastructure.sse;

import com.sportsify.notification.application.port.SseNotificationPort;
import com.sportsify.notification.infrastructure.config.NotificationProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class SseEmitterManager implements SseNotificationPort {

    private final NotificationProperties properties;
    private final Map<Long, SseEmitter> emitters = new ConcurrentHashMap<>();

    @Override
    public SseEmitter subscribe(Long memberId) {
        SseEmitter emitter = new SseEmitter(properties.sse().timeoutMs());
        SseEmitter previous = emitters.put(memberId, emitter);
        if (previous != null) {
            previous.complete();
        }
        emitter.onCompletion(() -> emitters.remove(memberId, emitter));
        emitter.onTimeout(() -> emitters.remove(memberId, emitter));
        emitter.onError(e -> emitters.remove(memberId, emitter));
        log.info("SSE subscribed memberId={}", memberId);
        return emitter;
    }

    @Override
    public void send(Long memberId, Object data) {
        Optional.ofNullable(emitters.get(memberId))
                .ifPresent(emitter -> sendToEmitter(memberId, emitter, data));
    }

    private void sendToEmitter(Long memberId, SseEmitter emitter, Object data) {
        try {
            emitter.send(SseEmitter.event().name("notification").data(data));
        } catch (IOException e) {
            emitters.remove(memberId);
            log.warn("SSE send failed memberId={}", memberId);
        }
    }

    public boolean isConnected(Long memberId) {
        return emitters.containsKey(memberId);
    }

    public void unsubscribe(Long memberId) {
        emitters.remove(memberId);
    }
}
