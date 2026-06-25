package com.sportsify.chat.infrastructure.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.WebSocketSessionDecorator;

import java.io.IOException;
import java.security.Principal;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class PrincipalWebSocketSession extends WebSocketSessionDecorator {

    private volatile Principal principal;
    private final Timer sessionSendTimer;

    public PrincipalWebSocketSession(WebSocketSession delegate, MeterRegistry registry) {
        super(delegate);
        this.principal = delegate.getPrincipal();
        this.sessionSendTimer = Timer.builder("ws_session_send_duration")
                .description("ConcurrentWebSocketSessionDecorator가 실제로 flush할 때의 WebSocketSession.sendMessage() 소요시간 (Tomcat → TCP 버퍼 write)")
                .publishPercentiles(0.5, 0.90, 0.95, 0.99)
                .register(registry);
    }

    @Override
    public void sendMessage(WebSocketMessage<?> message) throws IOException {
        long start = System.nanoTime();
        try {
            super.sendMessage(message);
        } finally {
            sessionSendTimer.record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
        }
    }

    @Override
    public Principal getPrincipal() {
        return this.principal;
    }

    public void setPrincipal(Principal principal) {
        Objects.requireNonNull(principal, "principal");
        this.principal = principal;
    }
}
