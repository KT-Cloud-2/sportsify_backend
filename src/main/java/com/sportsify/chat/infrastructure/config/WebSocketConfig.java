package com.sportsify.chat.infrastructure.config;

import com.sportsify.chat.infrastructure.webSocket.InboundChannelMetricsInterceptor;
import com.sportsify.chat.infrastructure.webSocket.OutboundChannelMetricsInterceptor;
import com.sportsify.chat.infrastructure.webSocket.StompAuthChannelInterceptor;
import com.sportsify.chat.infrastructure.webSocket.WebSocketSessionRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompAuthChannelInterceptor stompAuthChannelInterceptor;
    private final InboundChannelMetricsInterceptor inboundChannelMetricsInterceptor;
    private final OutboundChannelMetricsInterceptor outboundChannelMetricsInterceptor;
    private final MeterRegistry meterRegistry;
    @Value("${app.cors.allowed-origins}")
    String[] allowedOrigins;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws/chat").setAllowedOrigins(allowedOrigins);
    }


    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.setApplicationDestinationPrefixes("/app");
        registry.enableSimpleBroker("/topic", "/queue")
                .setHeartbeatValue(new long[]{10_000, 10_000})
                .setTaskScheduler(heartbeatScheduler());
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(inboundChannelMetricsInterceptor, stompAuthChannelInterceptor)
                .taskExecutor().corePoolSize(2).maxPoolSize(4).queueCapacity(20000);
    }

    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        // LinkedBlockingQueue 특성상 maxPoolSize는 큐가 꽉 찰 때만 효과 있음.
        // corePoolSize = maxPoolSize로 맞춰 스레드를 항상 유지.
        registration.interceptors(outboundChannelMetricsInterceptor)
                .taskExecutor().corePoolSize(8).maxPoolSize(16).queueCapacity(30000);
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration
                .setSendTimeLimit(30_000)
                .setSendBufferSizeLimit(1024 * 1024);
        registration.addDecoratorFactory(handler -> new PrincipalSessionDecorator(handler, meterRegistry));
    }

    /* -------------------- internal settings  -------------------- */

    @Bean
    public TaskScheduler heartbeatScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("ws-heartbeat-");
        scheduler.initialize();
        return scheduler;
    }

    private static final class PrincipalSessionDecorator extends WebSocketHandlerDecorator {

        private final MeterRegistry meterRegistry;

        private PrincipalSessionDecorator(WebSocketHandler delegate, MeterRegistry meterRegistry) {
            super(delegate);
            this.meterRegistry = meterRegistry;
        }

        @Override
        public void afterConnectionEstablished(WebSocketSession session) throws Exception {
            PrincipalWebSocketSession withMetrics = new PrincipalWebSocketSession(session, meterRegistry);
            ConcurrentWebSocketSessionDecorator concurrent = new ConcurrentWebSocketSessionDecorator(
                    withMetrics, 10_000, 512 * 1024
            );
            session.getAttributes().put(WebSocketSessionRegistry.WS_SESSION_ATTR, concurrent);
            super.afterConnectionEstablished(concurrent);
        }
    }
}
