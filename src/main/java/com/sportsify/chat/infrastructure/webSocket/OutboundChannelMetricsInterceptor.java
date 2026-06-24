package com.sportsify.chat.infrastructure.webSocket;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.support.ExecutorChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@RequiredArgsConstructor
public class OutboundChannelMetricsInterceptor implements ExecutorChannelInterceptor {

    private static final String ENQUEUE_NANOS = "ws.outbound.enqueueNanos";

    private final MeterRegistry meterRegistry;
    private final AtomicInteger activeCount = new AtomicInteger();

    private Timer outboundQueueWaitTimer;
    private Timer outboundSendTimer;
    // beforeHandle → afterMessageHandled는 같은 스레드에서 실행되므로 ThreadLocal로 전달
    private final ThreadLocal<Long> sendStartNanos = new ThreadLocal<>();

    @PostConstruct
    void init() {
        outboundQueueWaitTimer = Timer.builder("ws_outbound_queue_wait")
                .description("clientOutboundChannel 큐 적재 ~ 스레드 픽업까지 대기시간")
                .publishPercentiles(0.5, 0.90, 0.95, 0.99)
                .register(meterRegistry);

        outboundSendTimer = Timer.builder("ws_outbound_send_duration")
                .description("clientOutboundChannel 스레드가 실제 WebSocket 프레임을 전송하는 데 걸린 시간")
                .publishPercentiles(0.5, 0.90, 0.95, 0.99)
                .register(meterRegistry);

        Gauge.builder("ws_outbound_active", this, o -> o.activeCount.get())
                .description("clientOutboundChannel에서 현재 처리 중인 메시지 수")
                .register(meterRegistry);
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        return MessageBuilder.fromMessage(message)
                .setHeader(ENQUEUE_NANOS, System.nanoTime())
                .build();
    }

    @Override
    public Message<?> beforeHandle(Message<?> message, MessageChannel channel, MessageHandler handler) {
        activeCount.incrementAndGet();
        Long enqueueNanos = message.getHeaders().get(ENQUEUE_NANOS, Long.class);
        if (enqueueNanos != null) {
            outboundQueueWaitTimer.record(System.nanoTime() - enqueueNanos, TimeUnit.NANOSECONDS);
        }
        sendStartNanos.set(System.nanoTime());
        return message;
    }

    @Override
    public void afterMessageHandled(Message<?> message, MessageChannel channel, MessageHandler handler, Exception ex) {
        activeCount.decrementAndGet();
        Long startNanos = sendStartNanos.get();
        if (startNanos != null) {
            outboundSendTimer.record(System.nanoTime() - startNanos, TimeUnit.NANOSECONDS);
            sendStartNanos.remove();
        }
    }
}
