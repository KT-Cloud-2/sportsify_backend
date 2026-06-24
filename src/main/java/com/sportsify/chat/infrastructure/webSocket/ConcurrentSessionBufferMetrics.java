package com.sportsify.chat.infrastructure.webSocket;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.messaging.SubProtocolWebSocketHandler;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Queue;

/**
 * ConcurrentWebSocketSessionDecorator의 내부 버퍼 상태를 Micrometer Gauge로 노출한다.
 *
 * - ws_concurrent_buffer_bytes   : 전체 세션 버퍼의 누적 바이트 합 (flushLock 경합 시 누적)
 * - ws_concurrent_pending_messages : 전체 세션 버퍼 대기 메시지 수 합
 *
 * 두 값이 0을 유지하면 ConcurrentWebSocketSessionDecorator는 버퍼링 없이 즉시 write 중임을 의미한다.
 * 값이 올라가면 flushLock 경합 → 실제 TCP write 지연이 발생하고 있는 것이다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConcurrentSessionBufferMetrics {

    private final ApplicationContext applicationContext;
    private final MeterRegistry meterRegistry;

    private SubProtocolWebSocketHandler wsHandler;
    private Field sessionsField;
    private Method getSessionMethod;
    private Field bufferQueueField;

    @PostConstruct
    void init() {
        // Gauge를 먼저 등록 — reflection 셋업 실패 시에도 메트릭 자체는 항상 노출되어야 함
        Gauge.builder("ws_concurrent_buffer_bytes", this, ConcurrentSessionBufferMetrics::totalBufferBytes)
                .description("ConcurrentWebSocketSessionDecorator 전체 세션 버퍼 누적 크기 (bytes) — 0 이상이면 flushLock 경합으로 인한 write 지연 발생")
                .register(meterRegistry);

        Gauge.builder("ws_concurrent_pending_messages", this, ConcurrentSessionBufferMetrics::totalPendingMessages)
                .description("ConcurrentWebSocketSessionDecorator 전체 세션 버퍼 대기 메시지 수 합산")
                .register(meterRegistry);

        try {
            // 이름 기반 lookup: 선언 타입이 WebSocketHandler 인터페이스여서 타입 기반 검색이 실패할 수 있음
            var handler = applicationContext.getBean("subProtocolWebSocketHandler");
            if (!(handler instanceof SubProtocolWebSocketHandler h)) {
                log.warn("[ConcurrentSessionBufferMetrics] subProtocolWebSocketHandler bean이 SubProtocolWebSocketHandler가 아님: {}", handler.getClass());
                return;
            }
            wsHandler = h;

            sessionsField = SubProtocolWebSocketHandler.class.getDeclaredField("sessions");
            sessionsField.setAccessible(true);

            Class<?> holderClass = Class.forName(
                    "org.springframework.web.socket.messaging.SubProtocolWebSocketHandler$WebSocketSessionHolder");
            getSessionMethod = holderClass.getDeclaredMethod("getSession");
            getSessionMethod.setAccessible(true);

            bufferQueueField = ConcurrentWebSocketSessionDecorator.class.getDeclaredField("buffer");
            bufferQueueField.setAccessible(true);

            log.info("[ConcurrentSessionBufferMetrics] reflection 셋업 완료");
        } catch (Exception e) {
            log.warn("[ConcurrentSessionBufferMetrics] reflection 셋업 실패 — 지표가 -1로 고정됨", e);
        }
    }

    private double totalBufferBytes() {
        if (sessionsField == null) return -1;
        try {
            @SuppressWarnings("unchecked")
            Map<String, ?> sessions = (Map<String, ?>) sessionsField.get(wsHandler);
            double total = 0;
            for (Object holder : sessions.values()) {
                WebSocketSession ws = (WebSocketSession) getSessionMethod.invoke(holder);
                if (ws instanceof ConcurrentWebSocketSessionDecorator dec) {
                    total += dec.getBufferSize();
                }
            }
            return total;
        } catch (Exception e) {
            return -1;
        }
    }

    private double totalPendingMessages() {
        if (bufferQueueField == null) return -1;
        try {
            @SuppressWarnings("unchecked")
            Map<String, ?> sessions = (Map<String, ?>) sessionsField.get(wsHandler);
            double total = 0;
            for (Object holder : sessions.values()) {
                WebSocketSession ws = (WebSocketSession) getSessionMethod.invoke(holder);
                if (ws instanceof ConcurrentWebSocketSessionDecorator dec) {
                    @SuppressWarnings("unchecked")
                    Queue<?> queue = (Queue<?>) bufferQueueField.get(dec);
                    total += queue.size();
                }
            }
            return total;
        } catch (Exception e) {
            return -1;
        }
    }
}
