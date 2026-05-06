package com.sportsify.notification.infrastructure.sse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class SseEmitterManager {

    private static final long SSE_TIMEOUT_MS = 30 * 60 * 1000L;
    private final Map<Long, SseEmitter> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(Long memberId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        emitters.put(memberId, emitter);
        emitter.onCompletion(() -> emitters.remove(memberId));
        emitter.onTimeout(() -> emitters.remove(memberId));
        emitter.onError(e -> emitters.remove(memberId));
        log.info("SSE subscribed memberId={}", memberId);
        return emitter;
    }

    public void send(Long memberId, Object data) {
        SseEmitter emitter = emitters.get(memberId);
        if (emitter == null) {
            return;
        }
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
