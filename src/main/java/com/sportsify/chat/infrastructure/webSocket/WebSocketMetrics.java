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
                .description("Message processing duration")
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

    @EventListener
    public void onSessionEnded(WsSessionEndedEvent event) {
        disconnectCounter.increment();
    }
}
