package com.sportsify.chat.infrastructure;

import com.sportsify.chat.domain.model.chatRoom.ChatRoom;
import com.sportsify.chat.domain.model.chatRoom.ChatRoomId;
import com.sportsify.chat.domain.model.chatRoom.ChatRoomStatus;
import com.sportsify.chat.domain.model.chatRoom.ChatRoomType;
import com.sportsify.chat.domain.repository.ChatRoomRepository;
import com.sportsify.chat.infrastructure.webSocket.StompAuthChannelInterceptor;
import com.sportsify.chat.infrastructure.webSocket.WebSocketSessionRegistry;
import com.sportsify.infrastructure.security.JwtProvider;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.socket.WebSocketSession;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StompAuthChannelInterceptorTest {

    private static final Instant NOW = Instant.parse("2026-05-12T00:00:00Z");
    private static final Instant TOKEN_EXPIRY = NOW.plusSeconds(3600);
    private static final String TOKEN = "valid.jwt.token";
    private static final String SID = "sid-1";
    private static final long MEMBER_ID = 42L;
    private static final String BLACKLIST_KEY = "auth:blacklist:" + TOKEN;

    @Mock JwtProvider jwtProvider;
    @Mock WebSocketSessionRegistry registry;
    @Mock StringRedisTemplate redisTemplate;
    @Mock StompAuthChannelInterceptor.ChatRoomAccessChecker accessChecker;
    @Mock ChatRoomRepository chatRoomRepository;
    @Mock MessageChannel channel;
    @Mock WebSocketSession wsSession;
    @Mock Claims claims;

    StompAuthChannelInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new StompAuthChannelInterceptor(
                jwtProvider, registry, redisTemplate,
                Clock.fixed(NOW, ZoneOffset.UTC), accessChecker, chatRoomRepository);
    }

    // ── 헬퍼 ─────────────────────────────────────────────────

    private Message<byte[]> connectMessage(boolean withToken, boolean withWsSession) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setSessionId(SID);
        if (withToken) {
            accessor.addNativeHeader("Authorization", "Bearer " + TOKEN);
        }
        if (withWsSession) {
            Map<String, Object> attrs = new HashMap<>();
            attrs.put(WebSocketSessionRegistry.WS_SESSION_ATTR, wsSession);
            accessor.setSessionAttributes(attrs);
        }
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Message<byte[]> subscribeMessage(String destination, Long memberId) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setSessionId(SID);
        accessor.setDestination(destination);
        if (memberId != null) {
            accessor.setUser(new StompAuthChannelInterceptor.StompPrincipal(memberId));
        }
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Message<byte[]> sendMessage(Long memberId) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setSessionId(SID);
        accessor.setDestination("/app/chat.send");
        if (memberId != null) {
            accessor.setUser(new StompAuthChannelInterceptor.StompPrincipal(memberId));
        }
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private ChatRoom mockActiveRoom(ChatRoomType type) {
        ChatRoom room = mock(ChatRoom.class);
        given(room.getStatus()).willReturn(ChatRoomStatus.ACTIVE);
        given(room.getType()).willReturn(type);
        return room;
    }

    // ── CONNECT 성공 ──────────────────────────────────────────

    @Test
    @DisplayName("유효한 토큰으로 CONNECT 시 세션이 등록된다")
    void connect_유효토큰_세션등록() {
        given(redisTemplate.hasKey(BLACKLIST_KEY)).willReturn(false);
        given(jwtProvider.parse(TOKEN)).willReturn(claims);
        given(claims.getSubject()).willReturn(String.valueOf(MEMBER_ID));
        given(claims.get("role", String.class)).willReturn("USER");
        given(claims.getExpiration()).willReturn(Date.from(TOKEN_EXPIRY));

        Message<?> result = interceptor.preSend(connectMessage(true, true), channel);

        assertThat(result).isNotNull();
        verify(registry).register(eq(SID), eq(wsSession), any(), eq(TOKEN_EXPIRY), eq(NOW));
    }

    @Test
    @DisplayName("토큰 없이 CONNECT 시 익명 연결이 허용된다")
    void connect_토큰없음_익명허용() {
        Message<?> result = interceptor.preSend(connectMessage(false, false), channel);

        assertThat(result).isNotNull();
        verifyNoInteractions(jwtProvider, registry);
    }

    // ── CONNECT 실패 ──────────────────────────────────────────

    @Test
    @DisplayName("블랙리스트 토큰으로 CONNECT 시 예외가 발생한다")
    void connect_블랙리스트토큰_예외() {
        given(redisTemplate.hasKey(BLACKLIST_KEY)).willReturn(true);

        assertThatThrownBy(() -> interceptor.preSend(connectMessage(true, true), channel))
                .isInstanceOf(MessageDeliveryException.class);
        verifyNoInteractions(jwtProvider, registry);
    }

    @Test
    @DisplayName("유효하지 않은 JWT로 CONNECT 시 예외가 발생한다")
    void connect_유효하지않은JWT_예외() {
        given(redisTemplate.hasKey(BLACKLIST_KEY)).willReturn(false);
        given(jwtProvider.parse(TOKEN)).willThrow(new JwtException("invalid"));

        assertThatThrownBy(() -> interceptor.preSend(connectMessage(true, true), channel))
                .isInstanceOf(MessageDeliveryException.class);
        verifyNoInteractions(registry);
    }

    @Test
    @DisplayName("WebSocket 세션이 없으면 CONNECT 시 예외가 발생한다")
    void connect_WebSocket세션없음_예외() {
        given(redisTemplate.hasKey(BLACKLIST_KEY)).willReturn(false);
        given(jwtProvider.parse(TOKEN)).willReturn(claims);
        given(claims.getSubject()).willReturn(String.valueOf(MEMBER_ID));
        given(claims.get("role", String.class)).willReturn("USER");

        assertThatThrownBy(() -> interceptor.preSend(connectMessage(true, false), channel))
                .isInstanceOf(MessageDeliveryException.class);
        verifyNoInteractions(registry);
    }

    // ── SUBSCRIBE 성공 ────────────────────────────────────────

    @Test
    @DisplayName("GAME 방은 인증 없이 구독할 수 있다")
    void subscribe_GAME방_인증없이구독가능() {
        ChatRoom room = mockActiveRoom(ChatRoomType.GAME);
        given(chatRoomRepository.findById(any())).willReturn(Optional.of(room));

        interceptor.preSend(subscribeMessage("/topic/rooms/1", null), channel);

        verify(registry).subscribeRoom(SID, 1L);
    }

    @Test
    @DisplayName("DIRECT 방을 인증된 멤버가 구독하면 subscribeRoom이 호출된다")
    void subscribe_DIRECT방_인증멤버_구독성공() {
        ChatRoom room = mockActiveRoom(ChatRoomType.DIRECT);
        given(chatRoomRepository.findById(any())).willReturn(Optional.of(room));
        given(accessChecker.canSubscribe(eq(room), any())).willReturn(true);

        interceptor.preSend(subscribeMessage("/topic/rooms/1", MEMBER_ID), channel);

        verify(registry).subscribeRoom(SID, 1L);
    }

    @Test
    @DisplayName("방이 아닌 destination을 인증된 유저가 구독하면 통과된다")
    void subscribe_비방목적지_인증유저_통과() {
        Message<?> result = interceptor.preSend(subscribeMessage("/user/queue/errors", MEMBER_ID), channel);

        assertThat(result).isNotNull();
        verifyNoInteractions(chatRoomRepository, registry);
    }

    // ── SUBSCRIBE 실패 ────────────────────────────────────────

    @Test
    @DisplayName("존재하지 않는 방을 구독하면 예외가 발생한다")
    void subscribe_방없음_예외() {
        given(chatRoomRepository.findById(any())).willReturn(Optional.empty());

        assertThatThrownBy(() -> interceptor.preSend(subscribeMessage("/topic/rooms/1", MEMBER_ID), channel))
                .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    @DisplayName("ACTIVE가 아닌 방을 구독하면 예외가 발생한다")
    void subscribe_비활성방_예외() {
        ChatRoom room = mock(ChatRoom.class);
        given(room.getStatus()).willReturn(ChatRoomStatus.ARCHIVED);
        given(chatRoomRepository.findById(any())).willReturn(Optional.of(room));

        assertThatThrownBy(() -> interceptor.preSend(subscribeMessage("/topic/rooms/1", MEMBER_ID), channel))
                .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    @DisplayName("DIRECT 방 구독 시 미인증이면 예외가 발생한다")
    void subscribe_DIRECT방_미인증_예외() {
        ChatRoom room = mockActiveRoom(ChatRoomType.DIRECT);
        given(chatRoomRepository.findById(any())).willReturn(Optional.of(room));

        assertThatThrownBy(() -> interceptor.preSend(subscribeMessage("/topic/rooms/1", null), channel))
                .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    @DisplayName("DIRECT 방 구독 시 접근 권한이 없으면 예외가 발생한다")
    void subscribe_DIRECT방_권한없음_예외() {
        ChatRoom room = mockActiveRoom(ChatRoomType.DIRECT);
        given(chatRoomRepository.findById(any())).willReturn(Optional.of(room));
        given(accessChecker.canSubscribe(eq(room), any())).willReturn(false);

        assertThatThrownBy(() -> interceptor.preSend(subscribeMessage("/topic/rooms/1", MEMBER_ID), channel))
                .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    @DisplayName("방이 아닌 destination을 미인증 유저가 구독하면 예외가 발생한다")
    void subscribe_비방목적지_미인증_예외() {
        assertThatThrownBy(() -> interceptor.preSend(subscribeMessage("/user/queue/errors", null), channel))
                .isInstanceOf(MessageDeliveryException.class);
    }

    // ── SEND ──────────────────────────────────────────────────

    @Test
    @DisplayName("인증된 유저의 SEND는 통과된다")
    void send_인증유저_통과() {
        Message<?> result = interceptor.preSend(sendMessage(MEMBER_ID), channel);

        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("미인증 유저의 SEND는 예외가 발생한다")
    void send_미인증_예외() {
        assertThatThrownBy(() -> interceptor.preSend(sendMessage(null), channel))
                .isInstanceOf(MessageDeliveryException.class);
    }

    // ── 기타 커맨드 ───────────────────────────────────────────

    @Test
    @DisplayName("DISCONNECT 등 다른 커맨드는 그대로 통과된다")
    void preSend_기타커맨드_통과() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.DISCONNECT);
        accessor.setSessionId(SID);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> result = interceptor.preSend(message, channel);

        assertThat(result).isNotNull();
        verifyNoInteractions(jwtProvider, registry, chatRoomRepository);
    }
}
