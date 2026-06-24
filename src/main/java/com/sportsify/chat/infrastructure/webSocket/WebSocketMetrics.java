package com.sportsify.chat.infrastructure.webSocket;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WebSocketMetrics {

    private final MeterRegistry meterRegistry;
    private final WebSocketSessionRegistry sessionRegistry;

    private Counter connectErrorCounter;
    private Counter messagesInCounter;
    private Counter messagesOutCounter;
    private Timer messageDurationTimer;
    private Timer brokerPublishTimer;
    private Counter disconnectCounter;

    @PostConstruct
    void init() {
        Gauge.builder("ws_active_sessions", sessionRegistry, WebSocketSessionRegistry::getSessionCount)
                .description("Active WebSocket sessions")
                .register(meterRegistry);
        Gauge.builder("ws_active_subscriptions", sessionRegistry, WebSocketSessionRegistry::getTotalSubscriptionCount)
                .description("Active room subscriptions")
                .register(meterRegistry);
        connectErrorCounter = Counter.builder("ws_connect_errors_total")
                .description("WebSocket CONNECT failures")
                .register(meterRegistry);
        messagesInCounter = Counter.builder("ws_messages_in_total")
                .description("Inbound chat messages (/chat.send)")
                .register(meterRegistry);
        messagesOutCounter = Counter.builder("ws_messages_out_total")
                .description("Outbound chat messages broadcast to rooms")
                .register(meterRegistry);
        messageDurationTimer = Timer.builder("ws_message_duration")
                .description("Message processing duration (inbound → DB commit)")
                .publishPercentiles(0.5, 0.90, 0.95, 0.99)
                .register(meterRegistry);
        brokerPublishTimer = Timer.builder("ws_broker_publish_duration")
                .description("SimpleBroker dispatch time: convertAndSend 호출 ~ 모든 구독자 outbound 큐 적재 완료")
                .publishPercentiles(0.5, 0.90, 0.95, 0.99)
                .register(meterRegistry);
        disconnectCounter = Counter.builder("ws_disconnect_total")
                .description("WebSocket disconnections")
                .register(meterRegistry);
    }

    public void recordConnectError() {
        connectErrorCounter.increment();
    }

    public void recordMessageIn() {
        messagesInCounter.increment();
    }

    public void recordMessageOut() {
        messagesOutCounter.increment();
    }

    public void recordMessageDuration(Runnable task) {
        messageDurationTimer.record(task);
    }

    public void recordBrokerPublish(Runnable task) {
        brokerPublishTimer.record(task);
    }

    public void recordInterceptorDuration(String command, Runnable task) {
        Timer.builder("ws_interceptor_duration")
                .description("StompAuthChannelInterceptor preSend duration")
                .tag("command", command)
                .publishPercentiles(0.5, 0.90, 0.95, 0.99)
                .register(meterRegistry)
                .record(task);
    }

    @EventListener
    public void onSessionEnded(WsSessionEndedEvent event) {
        disconnectCounter.increment();
    }
}
