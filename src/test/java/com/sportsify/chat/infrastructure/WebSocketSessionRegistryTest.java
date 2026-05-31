package com.sportsify.chat.infrastructure;

import com.sportsify.chat.infrastructure.webSocket.TokenExpiredEvent;
import com.sportsify.chat.infrastructure.webSocket.WebSocketSessionRegistry;
import com.sportsify.chat.infrastructure.webSocket.dto.RoomSubscriptionRevokedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebSocketSessionRegistryTest {

    private static final String SID = "sid-1";
    private static final long MEMBER_ID = 42L;
    private static final String ROLE = "USER";
    private static final Instant TOKEN_EXPIRY = Instant.now().plusSeconds(3600);
    private static final Instant CONNECTED_AT = Instant.now();

    @Mock
    WebSocketSession wsSession;
    @Mock
    ApplicationEventPublisher eventPublisher;

    WebSocketSessionRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new WebSocketSessionRegistry(eventPublisher, Clock.systemDefaultZone());
        lenient().when(wsSession.isOpen()).thenReturn(true);
    }

    private void registerDefault() {
        registry.register(SID, wsSession, MEMBER_ID, ROLE, TOKEN_EXPIRY, CONNECTED_AT);
    }

    // ── register ─────────────────────────────────────────────

    @Test
    @DisplayName("register 후 SessionInfo를 조회할 수 있다")
    void register_세션저장_조회성공() {
        registerDefault();

        var info = registry.get(SID);

        assertThat(info).isPresent();
        assertThat(info.get().sessionId()).isEqualTo(SID);
        assertThat(info.get().memberId()).isEqualTo(MEMBER_ID);
        assertThat(info.get().role()).isEqualTo(ROLE);
        assertThat(info.get().tokenExpiresAt()).isEqualTo(TOKEN_EXPIRY);
    }
    
    @Test
    @DisplayName("register 도중 wsSessions 저장이 실패하면 sessions에서 해당 세션이 롤백된다")
    void register_롤백_예외시sessions정리() throws Exception {
        Map<String, WebSocketSession> failingMap = injectFailingWsSessions();

        assertThatThrownBy(() -> registry.register(SID, wsSession, MEMBER_ID, ROLE, TOKEN_EXPIRY, CONNECTED_AT))
                .isInstanceOf(RuntimeException.class);

        assertThat(sessions()).doesNotContainKey(SID);
        verify(failingMap).remove(SID);
        assertThat(userSessions()).doesNotContainKey(MEMBER_ID);
    }

    // ── subscribeRoom ─────────────────────────────────────────

    @Test
    @DisplayName("subscribeRoom 후 SessionInfo의 subscribedRooms에 방이 추가된다")
    void subscribeRoom_구독추가() {
        registerDefault();

        registry.subscribeRoom(SID, "sub-99", 99L);

        assertThat(registry.get(SID).get().subscribedRooms()).containsValue(99L);
    }

    // ── forceDisconnect ───────────────────────────────────────

    @Test
    @DisplayName("forceDisconnect 시 WebSocket이 닫히고, 이후 disconnect 이벤트로 세션이 제거된다")
    void forceDisconnect_세션종료_제거() throws Exception {
        registerDefault();

        registry.forceDisconnect(SID, "reason");
        verify(wsSession).close(any(CloseStatus.class));

        // forceDisconnect는 소켓 종료만 담당; 세션 정리는 SessionDisconnectEvent 경유
        registry.onDisconnect(disconnectEvent(SID));
        assertThat(registry.get(SID)).isEmpty();
    }

    // ── revokeUser ────────────────────────────────────────────

    @Test
    @DisplayName("revokeUser는 해당 유저의 모든 세션을 종료한다")
    void revokeUser_유저전체세션종료() throws Exception {
        WebSocketSession ws2 = mock(WebSocketSession.class);
        given(ws2.isOpen()).willReturn(true);

        registry.register(SID, wsSession, MEMBER_ID, ROLE, TOKEN_EXPIRY, CONNECTED_AT);
        registry.register("sid-2", ws2, MEMBER_ID, ROLE, TOKEN_EXPIRY, CONNECTED_AT);

        registry.revokeUser(MEMBER_ID, "revoke");

        verify(wsSession).close(any(CloseStatus.class));
        verify(ws2).close(any(CloseStatus.class));
        registry.onDisconnect(disconnectEvent(SID));
        registry.onDisconnect(disconnectEvent("sid-2"));
        assertThat(registry.get(SID)).isEmpty();
        assertThat(registry.get("sid-2")).isEmpty();
    }

    // ── revokeRoomSubscriptionByMember ───────────────────────

    @Test
    @DisplayName("revokeRoomSubscriptionByMember는 해당 방의 구독만 해제하고 다른 방 연결은 유지한다")
    void revokeRoomSubscriptionByMember_해당방구독만해제() throws Exception {
        WebSocketSession ws2 = mock(WebSocketSession.class);

        registry.register(SID, wsSession, MEMBER_ID, ROLE, TOKEN_EXPIRY, CONNECTED_AT);
        registry.subscribeRoom(SID, "sub-10", 10L);

        registry.register("sid-2", ws2, MEMBER_ID, ROLE, TOKEN_EXPIRY, CONNECTED_AT);
        registry.subscribeRoom("sid-2", "sub-20", 20L);

        registry.revokeRoomSubscriptionByMember(MEMBER_ID, 10L);

        // WebSocket 연결 자체는 유지
        verify(wsSession, never()).close(any());
        verify(ws2, never()).close(any());
        // 방 구독 인덱스에서 제거됨
        assertThat(registry.get(SID).get().subscribedRooms()).doesNotContainValue(10L);
        verify(eventPublisher).publishEvent(new RoomSubscriptionRevokedEvent(SID, 10L));
    }

    // ── revokeAllRoomSubscriptions ────────────────────────────

    @Test
    @DisplayName("revokeAllRoomSubscriptions는 해당 방의 모든 세션 구독을 해제한다")
    void revokeAllRoomSubscriptions_방전체구독해제() throws Exception {
        WebSocketSession ws2 = mock(WebSocketSession.class);

        registry.register(SID, wsSession, MEMBER_ID, ROLE, TOKEN_EXPIRY, CONNECTED_AT);
        registry.subscribeRoom(SID, "sub-10a", 10L);
        registry.register("sid-2", ws2, 99L, ROLE, TOKEN_EXPIRY, CONNECTED_AT);
        registry.subscribeRoom("sid-2", "sub-10b", 10L);

        registry.revokeAllRoomSubscriptions(10L);

        // WebSocket 연결 자체는 유지
        verify(wsSession, never()).close(any());
        verify(ws2, never()).close(any());
        // 두 세션 모두 방 구독 해제 이벤트 발행
        verify(eventPublisher).publishEvent(new RoomSubscriptionRevokedEvent(SID, 10L));
        verify(eventPublisher).publishEvent(new RoomSubscriptionRevokedEvent("sid-2", 10L));
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
    @DisplayName("Phase1: 만료된 세션은 grace period 진입 후 종료되지 않고 오류 이벤트가 발행된다")
    void evictExpiredSessions_phase1_gracePeriod진입_알림발행() throws Exception {
        registry.register(SID, wsSession, MEMBER_ID, ROLE, Instant.now().minusSeconds(1), CONNECTED_AT);

        registry.evictExpiredSessions();

        verify(wsSession, never()).close(any());
        assertThat(registry.get(SID)).isPresent();
        verify(eventPublisher).publishEvent(new TokenExpiredEvent(SID));
    }

    @Test
    @DisplayName("Phase2: grace period가 만료된 세션은 강제 종료된다")
    void evictExpiredSessions_phase2_grace만료_강제종료() throws Exception {
        registry.register(SID, wsSession, MEMBER_ID, ROLE, Instant.now().minusSeconds(1), CONNECTED_AT);

        registry.evictExpiredSessions();
        setGraceDeadlineToPast(SID);
        registry.evictExpiredSessions();

        verify(wsSession).close(any(CloseStatus.class));
        registry.onDisconnect(disconnectEvent(SID));
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
    @DisplayName("updateExpiry는 세션의 토큰 만료 시간을 갱신하고 grace period를 제거한다")
    void updateExpiry_만료시간갱신_gracePeriod제거() throws Exception {
        registry.register(SID, wsSession, MEMBER_ID, ROLE, Instant.now().minusSeconds(1), CONNECTED_AT);
        registry.evictExpiredSessions(); // enters grace
        Instant newExpiry = Instant.now().plusSeconds(7200);

        registry.updateExpiry(SID, newExpiry);

        assertThat(registry.get(SID).get().tokenExpiresAt()).isEqualTo(newExpiry);
        setGraceDeadlineToPast(SID);
        registry.evictExpiredSessions();
        verify(wsSession, never()).close(any());
    }

    // ── helpers ───────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private Map<String, WebSocketSession> injectFailingWsSessions() throws Exception {
        Field field = WebSocketSessionRegistry.class.getDeclaredField("wsSessions");
        field.setAccessible(true);
        Map<String, WebSocketSession> failingMap = mock(Map.class);
        doThrow(new RuntimeException("simulated storage failure")).when(failingMap).put(any(), any());
        field.set(registry, failingMap);
        return failingMap;
    }

    @SuppressWarnings("unchecked")
    private Map<String, WebSocketSessionRegistry.SessionInfo> sessions() throws Exception {
        Field field = WebSocketSessionRegistry.class.getDeclaredField("sessions");
        field.setAccessible(true);
        return (Map<String, WebSocketSessionRegistry.SessionInfo>) field.get(registry);
    }

    @SuppressWarnings("unchecked")
    private Map<Long, Set<String>> userSessions() throws Exception {
        Field field = WebSocketSessionRegistry.class.getDeclaredField("userSessions");
        field.setAccessible(true);
        return (Map<Long, Set<String>>) field.get(registry);
    }

    private SessionDisconnectEvent disconnectEvent(String sid) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.DISCONNECT);
        accessor.setSessionId(sid);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        return new SessionDisconnectEvent(this, message, sid, CloseStatus.NORMAL);
    }

    @SuppressWarnings("unchecked")
    private void setGraceDeadlineToPast(String sid) throws Exception {
        Field field = WebSocketSessionRegistry.class.getDeclaredField("sessions");
        field.setAccessible(true);
        Map<String, WebSocketSessionRegistry.SessionInfo> sessions =
                (Map<String, WebSocketSessionRegistry.SessionInfo>) field.get(registry);
        sessions.computeIfPresent(sid, (_, info) -> info.withGraceDeadline(Instant.now().minusSeconds(1)));
    }
}
