package com.sportsify.chat.infrastructure;

import com.sportsify.chat.infrastructure.webSocket.WebSocketSessionRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.core.Authentication;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebSocketSessionRegistryTest {

    private static final String SID = "sid-1";
    private static final String USERNAME = "42";
    private static final Instant TOKEN_EXPIRY = Instant.now().plusSeconds(3600);
    private static final Instant CONNECTED_AT = Instant.now();
    @Mock
    WebSocketSession wsSession;
    @Mock
    Authentication auth;
    WebSocketSessionRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new WebSocketSessionRegistry();
        lenient().when(auth.getName()).thenReturn(USERNAME);
        lenient().when(wsSession.isOpen()).thenReturn(true);
    }

    private void registerDefault() {
        registry.register(SID, wsSession, auth, TOKEN_EXPIRY, CONNECTED_AT);
    }

    // ── register ─────────────────────────────────────────────

    @Test
    @DisplayName("register 후 get으로 SessionInfo를 조회할 수 있다")
    void register_세션저장_조회성공() {
        registerDefault();

        var info = registry.get(SID);

        assertThat(info).isPresent();
        assertThat(info.get().sessionId()).isEqualTo(SID);
        assertThat(info.get().authentication()).isEqualTo(auth);
        assertThat(info.get().tokenExpiresAt()).isEqualTo(TOKEN_EXPIRY);
    }

    @Test
    @DisplayName("소프트 종료된 세션의 구독 방은 재연결 시 복원된다")
    void register_재연결_구독방복원() throws Exception {
        registerDefault();
        registry.subscribeRoom(SID, 1L);
        registry.forceDisconnect(SID, "reconnect");

        WebSocketSession ws2 = mock(WebSocketSession.class);
        Authentication auth2 = mock(Authentication.class);
        given(auth2.getName()).willReturn(USERNAME);
        registry.register("sid-2", ws2, auth2, TOKEN_EXPIRY, CONNECTED_AT);

        assertThat(registry.get("sid-2").get().subscribedRooms()).contains(1L);
    }

    // ── subscribeRoom ─────────────────────────────────────────

    @Test
    @DisplayName("subscribeRoom 후 SessionInfo의 subscribedRooms에 방이 추가된다")
    void subscribeRoom_구독추가() {
        registerDefault();

        registry.subscribeRoom(SID, 99L);

        assertThat(registry.get(SID).get().subscribedRooms()).contains(99L);
    }

    // ── forceDisconnect ───────────────────────────────────────

    @Test
    @DisplayName("forceDisconnect 시 WebSocket이 닫히고 세션이 제거된다")
    void forceDisconnect_세션종료_제거() throws Exception {
        registerDefault();

        registry.forceDisconnect(SID, "reason");

        verify(wsSession).close(any(CloseStatus.class));
        assertThat(registry.get(SID)).isEmpty();
    }

    @Test
    @DisplayName("소프트 종료된 세션은 pendingCleanup을 통해 재연결 시 방을 복원한다")
    void forceDisconnect_소프트종료_pendingCleanup진입() throws Exception {
        registerDefault();
        registry.subscribeRoom(SID, 5L);
        registry.forceDisconnect(SID, "soft");

        WebSocketSession ws2 = mock(WebSocketSession.class);
        Authentication auth2 = mock(Authentication.class);
        given(auth2.getName()).willReturn(USERNAME);
        registry.register("sid-2", ws2, auth2, TOKEN_EXPIRY, CONNECTED_AT);

        assertThat(registry.get("sid-2").get().subscribedRooms()).contains(5L);
    }

    // ── hardDisconnect ────────────────────────────────────────

    @Test
    @DisplayName("hardDisconnect 시 세션이 제거되고 재연결해도 방이 복원되지 않는다")
    void hardDisconnect_하드종료_방복원불가() throws Exception {
        registerDefault();
        registry.subscribeRoom(SID, 7L);

        registry.hardDisconnect(SID, "hard reason");

        assertThat(registry.get(SID)).isEmpty();

        WebSocketSession ws2 = mock(WebSocketSession.class);
        Authentication auth2 = mock(Authentication.class);
        given(auth2.getName()).willReturn(USERNAME);
        registry.register("sid-2", ws2, auth2, TOKEN_EXPIRY, CONNECTED_AT);

        assertThat(registry.get("sid-2").get().subscribedRooms()).doesNotContain(7L);
    }

    // ── revokeUser ────────────────────────────────────────────

    @Test
    @DisplayName("revokeUser는 해당 유저의 모든 세션을 종료한다")
    void revokeUser_유저전체세션종료() throws Exception {
        WebSocketSession ws2 = mock(WebSocketSession.class);
        Authentication auth2 = mock(Authentication.class);
        given(ws2.isOpen()).willReturn(true);
        given(auth2.getName()).willReturn(USERNAME);

        registry.register(SID, wsSession, auth, TOKEN_EXPIRY, CONNECTED_AT);
        registry.register("sid-2", ws2, auth2, TOKEN_EXPIRY, CONNECTED_AT);

        registry.revokeUser(USERNAME, "revoke");

        verify(wsSession).close(any(CloseStatus.class));
        verify(ws2).close(any(CloseStatus.class));
        assertThat(registry.get(SID)).isEmpty();
        assertThat(registry.get("sid-2")).isEmpty();
    }

    // ── forceDisconnectByMember ───────────────────────────────

    @Test
    @DisplayName("forceDisconnectByMember는 memberId 유저의 세션을 종료한다")
    void forceDisconnectByMember_멤버세션종료() throws Exception {
        registerDefault();

        registry.forceDisconnectByMember(Long.parseLong(USERNAME), "reason");

        verify(wsSession).close(any(CloseStatus.class));
        assertThat(registry.get(SID)).isEmpty();
    }

    // ── forceDisconnectByMemberInRoom ─────────────────────────

    @Test
    @DisplayName("forceDisconnectByMemberInRoom은 해당 방에 있는 멤버의 세션만 종료한다")
    void forceDisconnectByMemberInRoom_특정방세션만종료() throws Exception {
        WebSocketSession ws2 = mock(WebSocketSession.class);
        Authentication auth2 = mock(Authentication.class);
        given(auth2.getName()).willReturn(USERNAME);

        registry.register(SID, wsSession, auth, TOKEN_EXPIRY, CONNECTED_AT);
        registry.subscribeRoom(SID, 10L);

        registry.register("sid-2", ws2, auth2, TOKEN_EXPIRY, CONNECTED_AT);
        registry.subscribeRoom("sid-2", 20L);

        registry.forceDisconnectByMemberInRoom(Long.parseLong(USERNAME), 10L, "reason");

        verify(wsSession).close(any(CloseStatus.class));
        verify(ws2, never()).close(any());
    }

    // ── forceDisconnectAllInRoom ──────────────────────────────

    @Test
    @DisplayName("forceDisconnectAllInRoom은 해당 방의 모든 세션을 종료한다")
    void forceDisconnectAllInRoom_방전체세션종료() throws Exception {
        WebSocketSession ws2 = mock(WebSocketSession.class);
        Authentication auth2 = mock(Authentication.class);
        given(ws2.isOpen()).willReturn(true);
        given(auth2.getName()).willReturn("99");

        registry.register(SID, wsSession, auth, TOKEN_EXPIRY, CONNECTED_AT);
        registry.subscribeRoom(SID, 10L);
        registry.register("sid-2", ws2, auth2, TOKEN_EXPIRY, CONNECTED_AT);
        registry.subscribeRoom("sid-2", 10L);

        registry.forceDisconnectAllInRoom(10L, "reason");

        verify(wsSession).close(any(CloseStatus.class));
        verify(ws2).close(any(CloseStatus.class));
    }

    // ── onDisconnect ──────────────────────────────────────────

    @Test
    @DisplayName("onDisconnect 이벤트 수신 시 해당 세션이 제거된다")
    void onDisconnect_이벤트수신_세션제거() {
        registerDefault();

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.DISCONNECT);
        accessor.setSessionId(SID);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        SessionDisconnectEvent event = new SessionDisconnectEvent(this, message, SID, CloseStatus.NORMAL);

        registry.onDisconnect(event);

        assertThat(registry.get(SID)).isEmpty();
    }

    // ── evictExpiredSessions ──────────────────────────────────

    @Test
    @DisplayName("만료된 토큰의 세션은 evictExpiredSessions 호출 시 종료된다")
    void evictExpiredSessions_만료세션종료() throws Exception {
        Instant expiredAt = Instant.now().minusSeconds(1);
        registry.register(SID, wsSession, auth, expiredAt, CONNECTED_AT);

        registry.evictExpiredSessions();

        verify(wsSession).close(any(CloseStatus.class));
        assertThat(registry.get(SID)).isEmpty();
    }

    @Test
    @DisplayName("만료되지 않은 세션은 evictExpiredSessions 호출 시 종료되지 않는다")
    void evictExpiredSessions_유효세션유지() throws Exception {
        registerDefault();

        registry.evictExpiredSessions();

        verify(wsSession, never()).close(any());
        assertThat(registry.get(SID)).isPresent();
    }

    // ── get ───────────────────────────────────────────────────
    @Test
    @DisplayName("존재하지 않는 sid 조회 시 빈 Optional을 반환한다")
    void get_없는sid_빈Optional반환() {
        assertThat(registry.get("unknown-sid")).isEmpty();
    }

    // ── updateExpiry ──────────────────────────────────────────

    @Test
    @DisplayName("updateExpiry는 세션의 토큰 만료 시간을 갱신한다")
    void updateExpiry_만료시간갱신() {
        registerDefault();
        Instant newExpiry = Instant.now().plusSeconds(7200);

        registry.updateExpiry(SID, newExpiry);

        assertThat(registry.get(SID).get().tokenExpiresAt()).isEqualTo(newExpiry);
    }
}
