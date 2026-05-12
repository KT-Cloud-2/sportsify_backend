package com.sportsify.chat.infrastructure.webSocket;

import com.sportsify.chat.domain.model.chatRoom.*;
import com.sportsify.chat.domain.repository.ChatRoomRepository;
import com.sportsify.infrastructure.security.JwtProvider;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.security.Principal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {
    private static final String BLACKLIST_KEY_PREFIX = "auth:blacklist:";
    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtProvider jwtProvider;
    private final WebSocketSessionRegistry webSocketSessionRegistry;
    private final StringRedisTemplate redisTemplate;
    private final Clock clock;
    private final ChatRoomAccessChecker accessChecker;
    private final ChatRoomRepository chatRoomRepository;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        if (accessor.getCommand() == null) {
            return message;
        }
        try {
            switch (accessor.getCommand()) {
                case CONNECT -> handleConnect(accessor);
                case SUBSCRIBE -> handleSubscribe(accessor);
                case SEND -> handleSend(accessor);
                default -> {
                }
            }
        } catch (Exception e) {
            log.error("Error in STOMP interceptor: {}", e.getMessage());
            throw new MessageDeliveryException("Internal authentication error");
        }
        return MessageBuilder.createMessage(message.getPayload(), accessor.getMessageHeaders());
    }

    private void handleConnect(StompHeaderAccessor accessor) {
        String token = extractToken(accessor);
        // 토큰 없으면 익명 연결 허용
        if (token == null) return;

        if (isBlacklist(token)) {
            throw new MessageDeliveryException("Invalid or Missing Token");
        }
        Claims parsed;
        try {
            parsed = jwtProvider.parse(token);
        } catch (JwtException | IllegalArgumentException e) {
            throw new MessageDeliveryException("Invalid or Missing Token");
        }
        long memberId = Long.parseLong(parsed.getSubject());
        String role = parsed.get("role", String.class);
        Authentication auth = new UsernamePasswordAuthenticationToken(memberId, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
        WebSocketSession ws = sessionAttributes == null ? null
                : (WebSocketSession) sessionAttributes.get(WebSocketSessionRegistry.WS_SESSION_ATTR);
        if (ws == null) throw new MessageDeliveryException("Websocket Session missing");
        accessor.setUser(new StompPrincipal(memberId));
        Instant expiry = parsed.getExpiration().toInstant();
        webSocketSessionRegistry.register(accessor.getSessionId(), ws, auth, expiry, Instant.now(clock));
    }

    private void handleSubscribe(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null) throw new MessageDeliveryException("Invalid subscribe");

        if (destination.startsWith("/topic/rooms/")) {
            String[] parts = destination.split("/");
            if (parts.length >= 4) {
                ChatRoomId roomId = ChatRoomId.of(Long.parseLong(parts[3]));
                ChatRoom chatRoom = chatRoomRepository.findById(roomId).orElseThrow(() -> new MessageDeliveryException("Room not found"));
                if (chatRoom.getStatus() != ChatRoomStatus.ACTIVE) {
                    throw new MessageDeliveryException("Room Not Active");
                }
                // game room은 비인증 허용
                if (chatRoom.getType() == ChatRoomType.GAME) {
                    webSocketSessionRegistry.subscribeRoom(accessor.getSessionId(), roomId.value());
                    return;
                }
                Principal user = accessor.getUser();
                if (user == null) throw new MessageDeliveryException("Authentication required");
                MemberId memberId = MemberId.of(((StompPrincipal) user).memberId());
                if (!accessChecker.canSubscribe(chatRoom, memberId)) {
                    throw new MessageDeliveryException("Access denied to room: " + roomId.value());
                }
                webSocketSessionRegistry.subscribeRoom(accessor.getSessionId(), roomId.value());
            }
        } else {
            if (accessor.getUser() == null) throw new MessageDeliveryException("Authentication required");
        }
    }

    private void handleSend(StompHeaderAccessor accessor) {
        if (accessor.getUser() == null) throw new MessageDeliveryException("Authentication required");
    }

    private String extractToken(StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader(AUTH_HEADER);
        return (header != null && header.startsWith(BEARER_PREFIX)) ? header.substring(7) : null;
    }

    private void ensureSessionValid(StompHeaderAccessor accessor, String op) {
        String sid = accessor.getSessionId();
        var info = webSocketSessionRegistry.get(sid)
                .orElseThrow(() -> new MessageDeliveryException(op + "on unknown session"));
        if (info.tokenExpiresAt().isBefore(Instant.now())) {
            webSocketSessionRegistry.forceDisconnect(sid, "Token expired");
            throw new MessageDeliveryException("Token expired");
        }
    }

    private boolean isBlacklist(String token) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(BLACKLIST_KEY_PREFIX + token));
    }

    public interface ChatRoomAccessChecker {
        boolean canSubscribe(ChatRoom room, MemberId memberId);

    }

    public record StompPrincipal(long memberId) implements Principal {

        @Override
        public String getName() {
            return String.valueOf(memberId);
        }
    }

}
