package com.sportsify.chat.infrastructure.webSocket;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.support.ExecutorChannelInterceptor;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class InboundChannelMetricsInterceptor implements ExecutorChannelInterceptor {

    private final MeterRegistry meterRegistry;
    private Timer inboundQueueWaitTimer;
    private Timer inboundExecTimer;

    // message 객체의 identity를 키로 사용 — 재빌드 없이 타이밍 전달
    private final ConcurrentHashMap<Integer, Long> enqueueNanosMap = new ConcurrentHashMap<>();
    // beforeHandle → afterMessageHandled는 같은 스레드에서 실행되므로 ThreadLocal로 전달
    private final ThreadLocal<Long> execStartNanos = new ThreadLocal<>();

    @PostConstruct
    void init() {
        inboundQueueWaitTimer = Timer.builder("ws_inbound_queue_wait")
                .description("clientInboundChannel 큐 대기시간 (WebSocket 수신 → 스레드 픽업)")
                .publishPercentiles(0.5, 0.90, 0.95, 0.99)
                .register(meterRegistry);
        inboundExecTimer = Timer.builder("ws_inbound_exec_duration")
                .description("clientInboundChannel 스레드 실행시간 (픽업 → 핸들러 완료)")
                .publishPercentiles(0.5, 0.90, 0.95, 0.99)
                .register(meterRegistry);
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        enqueueNanosMap.put(System.identityHashCode(message), System.nanoTime());
        return message;
    }

    @Override
    public Message<?> beforeHandle(Message<?> message, MessageChannel channel, MessageHandler handler) {
        Long enqueueNanos = enqueueNanosMap.remove(System.identityHashCode(message));
        if (enqueueNanos != null) {
            inboundQueueWaitTimer.record(System.nanoTime() - enqueueNanos, TimeUnit.NANOSECONDS);
        }
        execStartNanos.set(System.nanoTime());
        return message;
    }

    @Override
    public void afterMessageHandled(Message<?> message, MessageChannel channel, MessageHandler handler, Exception ex) {
        enqueueNanosMap.remove(System.identityHashCode(message));
        Long startNanos = execStartNanos.get();
        if (startNanos != null) {
            inboundExecTimer.record(System.nanoTime() - startNanos, TimeUnit.NANOSECONDS);
            execStartNanos.remove();
        }
    }
}
