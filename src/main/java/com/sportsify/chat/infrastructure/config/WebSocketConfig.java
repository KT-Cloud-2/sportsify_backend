package com.sportsify.chat.infrastructure.config;

import com.sportsify.chat.infrastructure.webSocket.StompAuthChannelInterceptor;
import com.sportsify.chat.infrastructure.webSocket.WebSocketSessionRegistry;
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
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompAuthChannelInterceptor stompAuthChannelInterceptor;
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
        registration.interceptors(stompAuthChannelInterceptor).taskExecutor().corePoolSize(4).maxPoolSize(8);
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.addDecoratorFactory(PrincipalSessionDecorator::new);
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

    private static final class PrincipalSessionDecorator
            extends WebSocketHandlerDecorator {

        private PrincipalSessionDecorator(WebSocketHandler delegate) {
            super(delegate);
        }

        @Override
        public void afterConnectionEstablished(WebSocketSession session)
                throws Exception {
            PrincipalWebSocketSession wrapped = new PrincipalWebSocketSession(session);
            session.getAttributes().put(WebSocketSessionRegistry.WS_SESSION_ATTR, wrapped);
            super.afterConnectionEstablished(wrapped);
        }
    }
}
