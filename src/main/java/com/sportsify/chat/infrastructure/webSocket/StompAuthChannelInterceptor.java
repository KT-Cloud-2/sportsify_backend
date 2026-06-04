package com.sportsify.chat.infrastructure.webSocket;

import com.sportsify.chat.application.webSocket.ChatRoomAccessChecker;
import com.sportsify.chat.domain.model.chatRoom.ChatRoom;
import com.sportsify.chat.domain.model.chatRoom.ChatRoomId;
import com.sportsify.chat.domain.model.chatRoom.MemberId;
import com.sportsify.chat.domain.repository.ChatRoomRepository;
import com.sportsify.chat.infrastructure.config.PrincipalWebSocketSession;
import com.sportsify.infrastructure.security.JwtProvider;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }
        try {
            switch (accessor.getCommand()) {
                case CONNECT -> handleConnect(accessor);
                case SUBSCRIBE -> handleSubscribe(accessor);
                case UNSUBSCRIBE -> handleUnsubscribe(accessor);
                case SEND -> {
                    if (!handleSend(accessor)) return null;
                }
                default -> {
                }
            }
        } catch (MessageDeliveryException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error in STOMP interceptor: {}", e.getMessage());
            throw new MessageDeliveryException("Internal authentication error");
        }
        populateUserFromRegistry(accessor);
        return message;
    }

    private void populateUserFromRegistry(StompHeaderAccessor accessor) {
        if (accessor.getUser() != null) return;
        if (!accessor.isMutable()) return;
        String sessionId = accessor.getSessionId();
        if (sessionId == null) return;
        var info = webSocketSessionRegistry.get(sessionId);
        if (info.isPresent()) {
            accessor.setUser(info.get().toAuthentication());
        } else {
            accessor.setUser(() -> "guest:" + sessionId);
        }
    }

    // 토큰 없으면 익명 연결 허용
    private void handleConnect(StompHeaderAccessor accessor) {
        String token = extractToken(accessor);
        if (token == null) return;

        if (isBlacklisted(token)) {
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
        if (ws instanceof PrincipalWebSocketSession pws) {
            pws.setPrincipal(auth);
        }
        accessor.setUser(auth);
        Instant expiry = parsed.getExpiration().toInstant();
        webSocketSessionRegistry.register(accessor.getSessionId(), ws, memberId, role, expiry, Instant.now(clock));
    }

    // game room은 비인증 허용
    private void handleSubscribe(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null) throw new MessageDeliveryException("Invalid subscribe");

        if (destination.startsWith(ChatEventPublisher.ROOM_TOPIC_PREFIX)) {
            String[] parts = destination.split("/");
            if (parts.length >= 4) {
                ChatRoomId roomId = ChatRoomId.of(Long.parseLong(parts[3]));
                ChatRoom chatRoom = chatRoomRepository.findById(roomId).orElseThrow(() -> new MessageDeliveryException("Room not found"));
                Optional<MemberId> memberId = resolveAuthenticatedMemberId(accessor.getSessionId());
                if (!accessChecker.canSubscribe(chatRoom, memberId)) {
                    throw new MessageDeliveryException("Access denied to room: " + roomId.value());
                }
                webSocketSessionRegistry.subscribeRoom(accessor.getSessionId(), accessor.getSubscriptionId(), roomId.value());
            }
        }
    }

    private void handleUnsubscribe(StompHeaderAccessor accessor) {
        String subscriptionId = accessor.getSubscriptionId();
        if (subscriptionId == null) return;
        webSocketSessionRegistry.unsubscribeRoom(accessor.getSessionId(), subscriptionId);
    }

    private boolean handleSend(StompHeaderAccessor accessor) {
        String sid = accessor.getSessionId();
        WebSocketSessionRegistry.SessionInfo info = webSocketSessionRegistry.get(sid)
                .orElseThrow(() -> new MessageDeliveryException("SEND on unknown session"));

        if (!info.tokenExpiresAt().isBefore(Instant.now(clock))) return true;

        if (tryRefreshExpiry(sid, info, accessor)) return true;

        webSocketSessionRegistry.enterGracePeriod(sid);
        eventPublisher.publishEvent(new TokenExpiredEvent(sid, info.memberId()));
        return false;
    }


    private boolean tryRefreshExpiry(String sid, WebSocketSessionRegistry.SessionInfo info, StompHeaderAccessor accessor) {
        String token = extractToken(accessor);
        if (token == null || isBlacklisted(token)) return false;
        Claims parsed;
        try {
            parsed = jwtProvider.parse(token);
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
        if (info.memberId() != Long.parseLong(parsed.getSubject())) return false;
        webSocketSessionRegistry.updateExpiry(sid, parsed.getExpiration().toInstant());
        return true;
    }

    private Optional<MemberId> resolveAuthenticatedMemberId(String sessionId) {
        return webSocketSessionRegistry.get(sessionId)
                .map(info -> MemberId.of(info.memberId()));
    }

    private String extractToken(StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader(AUTH_HEADER);
        return (header != null && header.startsWith(BEARER_PREFIX)) ? header.substring(BEARER_PREFIX.length()) : null;
    }

    private boolean isBlacklisted(String token) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(BLACKLIST_KEY_PREFIX + token));
    }

}
