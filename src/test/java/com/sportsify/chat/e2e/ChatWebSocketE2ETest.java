package com.sportsify.chat.e2e;

import com.sportsify.chat.domain.model.event.ErrorEventType;
import com.sportsify.chat.domain.model.event.EventType;
import com.sportsify.chat.infrastructure.persistence.chatRoom.ChatRoomJpaEntity;
import com.sportsify.chat.infrastructure.persistence.message.MessageJpaEntity;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.socket.WebSocketHttpHeaders;

import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Slf4j
@DisplayName("WebSocket STOMP E2E 테스트")
class ChatWebSocketE2ETest extends ChatE2ETestBase {

    private static final long MEMBER_A = 9001L;
    private static final long MEMBER_B = 9002L;

    @Autowired
    private SimpUserRegistry simpUserRegistry;

    @Autowired
    private com.sportsify.chat.infrastructure.webSocket.WebSocketSessionRegistry webSocketSessionRegistry;


    private void stompSend(StompSession session, String destination, Object data) {
        session.send(destination, data);
    }

    private void restPatch(String path, Object body, long memberId) {
        restClient.patch()
                .uri(url(path))
                .header("Authorization", "Bearer " + jwtProvider.createAccessToken(memberId, "USER"))
                .body(body != null ? body : Map.of())
                .retrieve()
                .toBodilessEntity();
    }

    private void restDelete(String path, long memberId) {
        restClient.delete()
                .uri(url(path))
                .header("Authorization", "Bearer " + jwtProvider.createAccessToken(memberId, "USER"))
                .retrieve()
                .toBodilessEntity();
    }


    @SuppressWarnings("unchecked")
    private Map<String, Object> payload(Map<String, Object> event) {
        return (Map<String, Object>) event.get("payload");
    }

    @Test
    @DisplayName("메시지 전송 시 구독 중인 다른 사용자에게 MESSAGE_SENT 이벤트가 전달된다")
    void 메시지전송_MESSAGE_SENT_이벤트_수신() throws Exception {
        ChatRoomJpaEntity room = fixture.createRoom("게임방", "GAME", "ACTIVE", MEMBER_A);
        fixture.createMember(room.getId(), MEMBER_A, "JOINED");
        fixture.createMember(room.getId(), MEMBER_B, "JOINED");

        StompSession sessionA = connect(MEMBER_A);
        StompSession sessionB = connect(MEMBER_B);
        BlockingQueue<Map<String, Object>> receivedByB = subscribeRoom(sessionB, room.getId());

        sendMessage(sessionA, room.getId(), "안녕하세요");

        Map<String, Object> event = receivedByB.poll(TIMEOUT_SEC, TimeUnit.SECONDS);
        assertThat(event).isNotNull();
        assertThat(event.get("event")).isEqualTo("MESSAGE_SENT");
        assertThat(payload(event).get("content")).isEqualTo("안녕하세요");
        assertThat(((Number) payload(event).get("senderId")).longValue()).isEqualTo(MEMBER_A);
    }


    @Test
    @DisplayName("유효한 JWT로 WebSocket 연결이 성공한다")
    void 유효한JWT_WebSocket_연결_성공() throws Exception {
        StompSession session = connect(MEMBER_A);
        assertThat(session.isConnected()).isTrue();
    }

    @Test
    @DisplayName("유효하지 않은 JWT로 WebSocket 연결이 거부된다")
    void 유효하지않은JWT_WebSocket_연결_거부() {
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer invalid.jwt.token");

        CompletableFuture<StompSession> future = stompClient.connectAsync(
                wsUrl(), new WebSocketHttpHeaders(), connectHeaders,
                new StompSessionHandlerAdapter() {
                }
        );

        ExecutionException rejection = null;
        try {
            future.get(TIMEOUT_SEC, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            rejection = e;
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new AssertionError("서버가 응답하지 않음: 인증 거부가 아닌 가용성 장애", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        assertThat(rejection).as("잘못된 JWT로는 연결이 거부되어야 한다").isNotNull();
    }

    @Test
    @DisplayName("비인증 사용자도 GAME 채팅방 구독이 허용되어 메시지를 수신한다")
    void 비인증사용자_GAME채팅방_구독허용() throws Exception {
        ChatRoomJpaEntity room = fixture.createRoom("공개게임방", "GAME", "ACTIVE", MEMBER_A);
        fixture.createMember(room.getId(), MEMBER_A, "JOINED");

        StompSession anonSession = connectAnonymous();
        BlockingQueue<Map<String, Object>> anonQueue = subscribeRoom(anonSession, room.getId());

        StompSession sessionA = connect(MEMBER_A);
        sendMessage(sessionA, room.getId(), "공개 메시지");

        Map<String, Object> event = anonQueue.poll(TIMEOUT_SEC, TimeUnit.SECONDS);
        assertThat(event).isNotNull();
        assertThat(event.get("event")).isEqualTo("MESSAGE_SENT");
    }

    @Test
    @DisplayName("인증된 사용자도 GAME 채팅방 구독이 정상 처리된다")
    void 인증사용자_GAME채팅방_구독허용() throws Exception {
        ChatRoomJpaEntity room = fixture.createRoom("공개게임방", "GAME", "ACTIVE", MEMBER_A);
        fixture.createMember(room.getId(), MEMBER_A, "JOINED");
        fixture.createMember(room.getId(), MEMBER_B, "JOINED");

        StompSession sessionB = connect(MEMBER_B);
        BlockingQueue<Map<String, Object>> queueB = subscribeRoom(sessionB, room.getId());

        StompSession sessionA = connect(MEMBER_A);
        sendMessage(sessionA, room.getId(), "테스트 메시지");

        assertThat(queueB.poll(TIMEOUT_SEC, TimeUnit.SECONDS)).isNotNull();
    }

    @Test
    @DisplayName("비멤버의 DIRECT 채팅방 구독 시도가 거부된다")
    void 비멤버_DIRECT채팅방_구독_거부() throws Exception {
        ChatRoomJpaEntity room = fixture.createRoom("DM방", "DIRECT", "ACTIVE", MEMBER_A);
        fixture.createMember(room.getId(), MEMBER_A, "JOINED");

        StompSession sessionB = connectWithErr(MEMBER_B);
        subscribeRoom(sessionB, room.getId());

        Throwable error = errorFutureOf(sessionB).get(TIMEOUT_SEC, TimeUnit.SECONDS);
        assertThat(error).isNotNull();
    }

    @Test
    @DisplayName("JOINED 멤버의 DIRECT 채팅방 구독이 허용된다")
    void JOINED멤버_DIRECT채팅방_구독_허용() throws Exception {
        ChatRoomJpaEntity room = fixture.createRoom("DM방", "DIRECT", "ACTIVE", MEMBER_A);
        fixture.createMember(room.getId(), MEMBER_A, "JOINED");
        fixture.createMember(room.getId(), MEMBER_B, "JOINED");

        StompSession sessionB = connect(MEMBER_B);
        subscribeRoom(sessionB, room.getId());

        Thread.sleep(500);
        assertThat(sessionB.isConnected()).isTrue();
    }

    @Test
    @DisplayName("오프라인 중 3개 메시지가 REPLAY_MESSAGE 타입으로 재전송된다")
    void 미수신메시지_3개_REPLAY_MESSAGE_재전송() throws Exception {
        ChatRoomJpaEntity room = fixture.createRoom("리플레이방", "GAME", "ACTIVE", MEMBER_A);
        fixture.createMember(room.getId(), MEMBER_A, "JOINED");
        fixture.createMember(room.getId(), MEMBER_B, "JOINED");

        StompSession sessionBFirst = connect(MEMBER_B);
        BlockingQueue<Map<String, Object>> firstQueue = subscribeRoom(sessionBFirst, room.getId());

        StompSession sessionA = connect(MEMBER_A);
        BlockingQueue<Map<String, Object>> queueA = subscribeRoom(sessionA, room.getId());
        sendMessage(sessionA, room.getId(), "기준 메시지");

        Map<String, Object> baselineEvent = firstQueue.poll(TIMEOUT_SEC, TimeUnit.SECONDS);
        assertThat(baselineEvent).isNotNull();
        long lastMessageId = ((Number) payload(baselineEvent).get("messageId")).longValue();

        sessionBFirst.disconnect();

        sendMessage(sessionA, room.getId(), "메시지1");
        sendMessage(sessionA, room.getId(), "메시지2");
        sendMessage(sessionA, room.getId(), "메시지3");

        assertThat(queueA.poll(TIMEOUT_SEC, TimeUnit.SECONDS)).isNotNull();
        assertThat(queueA.poll(TIMEOUT_SEC, TimeUnit.SECONDS)).isNotNull();
        assertThat(queueA.poll(TIMEOUT_SEC, TimeUnit.SECONDS)).isNotNull();

        StompSession sessionBNew = connect(MEMBER_B);
        BlockingQueue<Map<String, Object>> replayQueue = subscribeReplay(sessionBNew);
        subscribeRoomWithReplay(sessionBNew, room.getId(), lastMessageId);

        Map<String, Object> replayBatch = replayQueue.poll(TIMEOUT_SEC, TimeUnit.SECONDS);
        Assertions.assertNotNull(replayBatch);
        List<?> messages = (List<?>) replayBatch.get("messages");
        assertThat(messages).hasSize(3);

        messages.forEach(m -> assertThat(((Map<?, ?>) m).get("event")).isEqualTo("REPLAY_MESSAGE"));
    }


    @Test
    @DisplayName("11개 미수신 메시지 중 9개는 REPLAY_MESSAGE, 마지막 1개는 REPLAY_OVERFLOW")
    void 미수신메시지_11개_OVERFLOW_처리() throws Exception {
        ChatRoomJpaEntity room = fixture.createRoom("오버플로우방", "GAME", "ACTIVE", MEMBER_A);
        fixture.createMember(room.getId(), MEMBER_A, "JOINED");
        fixture.createMember(room.getId(), MEMBER_B, "JOINED");

        MessageJpaEntity baseline = fixture.createMessage(room.getId(), MEMBER_A);
        long lastMessageId = baseline.getId();

        for (int i = 0; i < 11; i++) {
            fixture.createMessage(room.getId(), MEMBER_A);
        }

        StompSession sessionB = connect(MEMBER_B);
        BlockingQueue<Map<String, Object>> replayQueue = subscribeReplay(sessionB);
        subscribeRoomWithReplay(sessionB, room.getId(), lastMessageId);

        Map<String, Object> replayBatch = replayQueue.poll(TIMEOUT_SEC, TimeUnit.SECONDS);
        assertThat(replayBatch).isNotNull();

        List<?> messages = (List<?>) replayBatch.get("messages");
        List<String> events = messages.stream()
                .map(m -> (String) ((Map<?, ?>) m).get("event"))
                .toList();

        assertThat(events).hasSize(10);
        assertThat(events.subList(0, 9)).containsOnly("REPLAY_MESSAGE");
        assertThat(events.getLast()).isEqualTo("REPLAY_OVERFLOW");
    }


    @Test
    @DisplayName("DIRECT 채팅방 읽음 처리 시 구독 중인 상대방에게 READ_RECEIPT 이벤트가 전달된다")
    void DIRECT채팅방_읽음처리_READ_RECEIPT_수신() throws Exception {
        ChatRoomJpaEntity room = fixture.createRoom("DM방", "DIRECT", "ACTIVE", MEMBER_A);
        fixture.createMember(room.getId(), MEMBER_A, "JOINED");
        fixture.createMember(room.getId(), MEMBER_B, "JOINED");

        MessageJpaEntity msg = fixture.createMessage(room.getId(), MEMBER_B);

        StompSession sessionB = connect(MEMBER_B);
        BlockingQueue<Map<String, Object>> queueB = subscribeRoom(sessionB, room.getId());

        StompSession sessionA = connect(MEMBER_A);
        stompSend(sessionA, "/app/chat.read", Map.of("roomId", room.getId(), "lastReadMessageId", msg.getId()));

        Map<String, Object> event = queueB.poll(TIMEOUT_SEC, TimeUnit.SECONDS);
        assertThat(event.get("event")).isEqualTo("READ_RECEIPT");
    }

    @Test
    @DisplayName("단조 증가 보장 - 이미 읽은 messageId 이하로 재호출 시 읽음 상태가 되돌아가지 않는다")
    void DIRECT채팅방_읽음처리_단조증가_보장() throws Exception {
        ChatRoomJpaEntity room = fixture.createRoom("DM방", "DIRECT", "ACTIVE", MEMBER_A);
        fixture.createMember(room.getId(), MEMBER_A, "JOINED");
        fixture.createMember(room.getId(), MEMBER_B, "JOINED");

        MessageJpaEntity msg1 = fixture.createMessage(room.getId(), MEMBER_B);
        MessageJpaEntity msg2 = fixture.createMessage(room.getId(), MEMBER_B);

        StompSession sessionB = connect(MEMBER_B);
        BlockingQueue<Map<String, Object>> queueB = subscribeRoom(sessionB, room.getId());

        StompSession sessionA = connect(MEMBER_A);

        stompSend(sessionA, "/app/chat.read", Map.of("roomId", room.getId(), "lastReadMessageId", msg2.getId()));
        Map<String, Object> firstReceipt = queueB.poll(TIMEOUT_SEC, TimeUnit.SECONDS);
        assertThat(firstReceipt).isNotNull();

        stompSend(sessionA, "/app/chat.read", Map.of("roomId", room.getId(), "lastReadMessageId", msg1.getId()));
        Map<String, Object> secondReceipt = queueB.poll(NO_EVENT_WAIT_SEC, TimeUnit.SECONDS);
        assertThat(secondReceipt).isNull();
    }


    @Test
    @DisplayName("GAME 채팅방에서 읽음 처리 시 READ_RECEIPT 이벤트가 발행되지 않는다")
    void GAME채팅방_읽음처리_READ_RECEIPT_미발행() throws Exception {
        ChatRoomJpaEntity room = fixture.createRoom("게임방", "GAME", "ACTIVE", MEMBER_A);
        fixture.createMember(room.getId(), MEMBER_A, "JOINED");
        fixture.createMember(room.getId(), MEMBER_B, "JOINED");

        MessageJpaEntity msg = fixture.createMessage(room.getId(), MEMBER_B);

        StompSession sessionB = connect(MEMBER_B);
        BlockingQueue<Map<String, Object>> queueB = subscribeRoom(sessionB, room.getId());

        StompSession sessionA = connect(MEMBER_A);
        stompSend(sessionA, "/app/chat.read", Map.of("roomId", room.getId(), "lastReadMessageId", msg.getId()));

        Map<String, Object> event = queueB.poll(NO_EVENT_WAIT_SEC, TimeUnit.SECONDS);
        assertThat(event).as("GAME 채팅방에서는 READ_RECEIPT 이벤트가 발생하지 않아야 한다").isNull();
    }


    @Test
    @DisplayName("채팅방 삭제 시 접속 중인 멤버에게 ROOM_DELETED 이벤트가 전달된다")
    void 채팅방삭제_ROOM_DELETED_이벤트_수신() throws Exception {
        ChatRoomJpaEntity room = fixture.createRoom("삭제방", "GAME", "ACTIVE", MEMBER_A);
        fixture.createMember(room.getId(), MEMBER_A, "JOINED");
        fixture.createMember(room.getId(), MEMBER_B, "JOINED");

        StompSession sessionB = connect(MEMBER_B);
        BlockingQueue<Map<String, Object>> queueB = subscribeRoom(sessionB, room.getId());

        restDelete("/api/chat/rooms/" + room.getId(), MEMBER_A);

        Map<String, Object> event = queueB.poll(TIMEOUT_SEC, TimeUnit.SECONDS);
        assertThat(event.get("event")).isEqualTo("ROOM_DELETED");
    }


    @Test
    @DisplayName("BAN 처리 직후 해당 멤버의 채팅방 구독이 해제되고 이후 메시지를 수신하지 않는다")
    void BAN처리_구독자동해제_이후메시지_미수신() throws Exception {
        ChatRoomJpaEntity room = fixture.createRoom("BAN테스트방", "GAME", "ACTIVE", MEMBER_A);
        fixture.createMember(room.getId(), MEMBER_A, "JOINED");
        fixture.createMember(room.getId(), MEMBER_B, "JOINED");

        StompSession sessionB = connect(MEMBER_B);
        BlockingQueue<Map<String, Object>> queueB = subscribeRoom(sessionB, room.getId());
        BlockingQueue<Map<String, Object>> errorQueueB = subscribeError(sessionB);

        restPost("/api/chat/rooms/" + room.getId() + "/ban?targetId=" + MEMBER_B, null, MEMBER_A);

        Map<String, Object> banEvent = queueB.poll(TIMEOUT_SEC, TimeUnit.SECONDS);
        assertThat(banEvent.get("event")).isEqualTo("MEMBER_BANNED");

        Map<String, Object> kickedEvent = errorQueueB.poll(TIMEOUT_SEC, TimeUnit.SECONDS);
        log.info(
                "[TEST WAITING] sessionId={}, queueSize={}",
                sessionB.getSessionId(),
                errorQueueB.size()
        );
        assertThat(kickedEvent.get("type")).isEqualTo("KICKED_FROM_ROOM");

        StompSession sessionA = connect(MEMBER_A);
        sendMessage(sessionA, room.getId(), "강퇴 후 전송 메시지" +
                "");

        Map<String, Object> afterBanMessage = queueB.poll(NO_EVENT_WAIT_SEC, TimeUnit.SECONDS);
        assertThat(afterBanMessage).as("강퇴된 사용자에게 이후 메시지가 전달되지 않아야 한다").isNull();

    }

    @Test
    @DisplayName("채팅방 아카이브 시 접속 중인 멤버에게 ROOM_ARCHIVED 이벤트가 전달된다")
    void 채팅방아카이브_ROOM_ARCHIVED_이벤트_수신() throws Exception {
        ChatRoomJpaEntity room = fixture.createRoom("아카이브방", "GAME", "ACTIVE", MEMBER_A);
        fixture.createMember(room.getId(), MEMBER_A, "JOINED");
        fixture.createMember(room.getId(), MEMBER_B, "JOINED");

        StompSession sessionB = connect(MEMBER_B);
        BlockingQueue<Map<String, Object>> queueB = subscribeRoom(sessionB, room.getId());

        restPatch("/api/chat/rooms/" + room.getId() + "/archive", null, MEMBER_A);

        Map<String, Object> event = queueB.poll(TIMEOUT_SEC, TimeUnit.SECONDS);
        assertThat(event.get("event")).isEqualTo("ROOM_ARCHIVED");
    }


    @Test
    @DisplayName("퇴장 후 이후 메시지가 해당 사용자에게 전달되지 않는다")
    void 퇴장후_이후메시지_미수신() throws Exception {
        ChatRoomJpaEntity room = fixture.createRoom("퇴장방", "GAME", "ACTIVE", MEMBER_A);
        fixture.createMember(room.getId(), MEMBER_A, "JOINED");
        fixture.createMember(room.getId(), MEMBER_B, "JOINED");

        StompSession sessionA = connect(MEMBER_A);
        BlockingQueue<Map<String, Object>> queueA = subscribeRoom(sessionA, room.getId());

        restDelete("/api/chat/rooms/" + room.getId() + "/leave", MEMBER_A);
        queueA.poll(TIMEOUT_SEC, TimeUnit.SECONDS);

        StompSession sessionB = connect(MEMBER_B);
        sendMessage(sessionB, room.getId(), "퇴장 후 메시지");

        Map<String, Object> afterLeave = queueA.poll(NO_EVENT_WAIT_SEC, TimeUnit.SECONDS);
        assertThat(afterLeave).as("퇴장한 사용자에게 이후 메시지가 전달되지 않아야 한다").isNull();
    }

    @Test
    @DisplayName("타이핑 이벤트가 구독 중인 다른 사용자에게 전달된다")
    void 타이핑_이벤트_구독자_수신() throws Exception {
        ChatRoomJpaEntity room = fixture.createRoom("타이핑방", "GAME", "ACTIVE", MEMBER_A);
        fixture.createMember(room.getId(), MEMBER_A, "JOINED");
        fixture.createMember(room.getId(), MEMBER_B, "JOINED");

        StompSession sessionA = connect(MEMBER_A);
        BlockingQueue<Map<String, Object>> typingQueue = subscribeTyping(sessionA, room.getId());

        StompSession sessionB = connect(MEMBER_B);
        stompSend(sessionB, "/app/chat.typing", Map.of("roomId", room.getId(), "typing", true));

        Map<String, Object> event = typingQueue.poll(TIMEOUT_SEC, TimeUnit.SECONDS);
        assertThat(event.get("event")).isEqualTo("TYPING");
        assertThat(((Number) event.get("userId")).longValue()).isEqualTo(MEMBER_B);
        assertThat(event.get("typing")).isEqualTo(true);
    }

    @Test
    @DisplayName("채팅방 수정 시 구독 중인 멤버에게 ROOM_UPDATED 이벤트가 전달된다")
    void 채팅방수정_ROOM_UPDATED_이벤트_수신() throws Exception {
        ChatRoomJpaEntity room = fixture.createRoom("수정전", "GAME", "ACTIVE", MEMBER_A);
        fixture.createMember(room.getId(), MEMBER_A, "JOINED");
        fixture.createMember(room.getId(), MEMBER_B, "JOINED");

        StompSession sessionB = connect(MEMBER_B);
        BlockingQueue<Map<String, Object>> queueB = subscribeRoom(sessionB, room.getId());

        restPatch("/api/chat/rooms/" + room.getId(), Map.of("name", "수정후"), MEMBER_A);

        Map<String, Object> event = queueB.poll(TIMEOUT_SEC, TimeUnit.SECONDS);
        assertThat(event.get("event")).isEqualTo("ROOM_UPDATED");
    }


    @Test
    @DisplayName("메시지 삭제 시 구독 중인 사용자에게 MESSAGE_DELETED 이벤트가 전달된다")
    void 메시지삭제_MESSAGE_DELETED_이벤트_수신() throws Exception {
        ChatRoomJpaEntity room = fixture.createRoom("메시지삭제방", "GAME", "ACTIVE", MEMBER_A);
        fixture.createMember(room.getId(), MEMBER_A, "JOINED");
        fixture.createMember(room.getId(), MEMBER_B, "JOINED");

        StompSession sessionA = connect(MEMBER_A);
        StompSession sessionB = connect(MEMBER_B);
        BlockingQueue<Map<String, Object>> queueB = subscribeRoom(sessionB, room.getId());

        sendMessage(sessionA, room.getId(), "삭제할 메시지");
        Map<String, Object> sentEvent = queueB.poll(TIMEOUT_SEC, TimeUnit.SECONDS);
        assertThat(sentEvent).isNotNull();
        long messageId = ((Number) payload(sentEvent).get("messageId")).longValue();

        restDelete("/api/chat/messages/" + messageId, MEMBER_A);

        Map<String, Object> deletedEvent = queueB.poll(TIMEOUT_SEC, TimeUnit.SECONDS);
        assertThat(deletedEvent.get("event")).isEqualTo("MESSAGE_DELETED");
        assertThat(((Number) payload(deletedEvent).get("messageId")).longValue()).isEqualTo(messageId);
    }

    @Test
    @DisplayName("채팅방 삭제 시 접속 중인 사용자의 모든 동작을 제한한다")
    void 채팅방삭제_이후_모든동작_차단() throws Exception {
        ChatRoomJpaEntity room = fixture.createRoom("삭제될방", "GAME", "ACTIVE", MEMBER_A);
        fixture.createMember(room.getId(), MEMBER_A, "JOINED");
        fixture.createMember(room.getId(), MEMBER_B, "JOINED");
        MessageJpaEntity message = fixture.createMessage(room.getId(), MEMBER_B);

        testAllOperations(room, message,
                () -> restDelete("/api/chat/rooms/" + room.getId(), MEMBER_A),
                EventType.ROOM_DELETED);
    }

    @Test
    @DisplayName("채팅방 아카이브 시 접속 중인 사용자의 모든 동작을 제한한다")
    void 채팅방아카이브_이후_모든동작_차단() throws Exception {
        ChatRoomJpaEntity room = fixture.createRoom("아카이브될방", "GAME", "ACTIVE", MEMBER_A);
        fixture.createMember(room.getId(), MEMBER_A, "JOINED");
        fixture.createMember(room.getId(), MEMBER_B, "JOINED");
        MessageJpaEntity message = fixture.createMessage(room.getId(), MEMBER_B);

        testAllOperations(room, message,
                () -> restPatch("/api/chat/rooms/" + room.getId() + "/archive", null, MEMBER_A),
                EventType.ROOM_ARCHIVED);
    }

    @Test
    @DisplayName("JWT 만료 후 토큰 갱신을 통해 WebSocket 세션 인증을 갱신할 수 있다")
    void JWT_갱신_성공() throws Exception {
        ChatRoomJpaEntity room = fixture.createRoom("갱신테스트방", "GAME", "ACTIVE", MEMBER_A);
        fixture.createMember(room.getId(), MEMBER_A, "JOINED");

        long expiryMs = 3000L;
        long tokenCreatedAt = System.currentTimeMillis();
        StompSession sessionA = connectWithJwt(MEMBER_A, createShortLivedToken(MEMBER_A, "USER", expiryMs));

        BlockingQueue<Map<String, Object>> queueA = subscribeRoom(sessionA, room.getId());
        BlockingQueue<Map<String, Object>> errorQueue = subscribeError(sessionA);
        sendMessage(sessionA, room.getId(), "만료 전 메시지");
        assertThat(queueA.poll(TIMEOUT_SEC, TimeUnit.SECONDS)).isNotNull();

        long remaining = (tokenCreatedAt + expiryMs) - System.currentTimeMillis();
        if (remaining > 0) Thread.sleep(remaining + 100);

        sendMessage(sessionA, room.getId(), "만료 후 메시지");
        Map<String, Object> expiredError = errorQueue.poll(TIMEOUT_SEC, TimeUnit.SECONDS);
        assertThat(expiredError.get("type")).isEqualTo(ErrorEventType.TOKEN_EXPIRED.name());

        String newToken = jwtProvider.createAccessToken(MEMBER_A, "USER");

        StompHeaders renewalHeaders = new StompHeaders();
        renewalHeaders.setDestination("/app/chat.send");
        renewalHeaders.add("Authorization", "Bearer " + newToken);
        sessionA.send(renewalHeaders, Map.of(
                "clientMessageId", "cid-renewal-" + System.nanoTime(),
                "roomId", room.getId(),
                "type", "TEXT",
                "content", "갱신 후 메시지"
        ));

        Map<String, Object> renewedMessage = queueA.poll(TIMEOUT_SEC, TimeUnit.SECONDS);
        assertThat(renewedMessage.get("event")).isEqualTo(EventType.MESSAGE_SENT.name());
    }

    @Test
    @DisplayName("Grace Period 내 JWT 갱신 없이 만료 시 WebSocket 연결이 강제 종료된다")
    void GracePeriod_만료_후_연결_강제종료() throws Exception {
        ChatRoomJpaEntity room = fixture.createRoom("만료테스트방", "GAME", "ACTIVE", MEMBER_A);
        fixture.createMember(room.getId(), MEMBER_A, "JOINED");

        long expiryMs = 3000L;
        long tokenCreatedAt = System.currentTimeMillis();
        StompSession sessionA = connectWithJwt(MEMBER_A, createShortLivedToken(MEMBER_A, "USER", expiryMs));
        BlockingQueue<Map<String, Object>> errorQueue = subscribeError(sessionA);

        BlockingQueue<Map<String, Object>> queueA = subscribeRoom(sessionA, room.getId());
        sendMessage(sessionA, room.getId(), "만료 전 메시지");
        assertThat(queueA.poll(TIMEOUT_SEC, TimeUnit.SECONDS)).isNotNull();

        long remaining = (tokenCreatedAt + expiryMs) - System.currentTimeMillis();
        if (remaining > 0) Thread.sleep(remaining + 100);
        sendMessage(sessionA, room.getId(), "만료 후 메시지");
        long graceEnteredAt = System.currentTimeMillis();
        assertThat(errorQueue.poll(TIMEOUT_SEC, TimeUnit.SECONDS).get("type"))
                .isEqualTo(ErrorEventType.TOKEN_EXPIRED.name());

        long graceRemaining = (graceEnteredAt + 30_000) - System.currentTimeMillis();
        if (graceRemaining > 0) Thread.sleep(graceRemaining + 100);

        webSocketSessionRegistry.evictExpiredSessions();
        Thread.sleep(500);
        assertThat(sessionA.isConnected()).isFalse();
    }


    private void testAllOperations(
            ChatRoomJpaEntity room, MessageJpaEntity message,
            Runnable closeAction, EventType expectedEvent) throws Exception {
        StompSession sessionB = connect(MEMBER_B);
        BlockingQueue<Map<String, Object>> queueB = subscribeRoom(sessionB, room.getId());
        BlockingQueue<Map<String, Object>> messageErrorQueueB = subscribeMessageErrors(sessionB);

        closeAction.run();

        Map<String, Object> closedEvent = queueB.poll(TIMEOUT_SEC, TimeUnit.SECONDS);
        assertThat(closedEvent).isNotNull();
        assertThat(closedEvent.get("event")).isEqualTo(expectedEvent.name());

        sendMessage(sessionB, room.getId(), "비활성화된 방에 보내는 메시지");
        Map<String, Object> sendError = messageErrorQueueB.poll(TIMEOUT_SEC, TimeUnit.SECONDS);
        assertThat(sendError).isNotNull();
        assertThat(sendError.get("event")).isEqualTo(ErrorEventType.MESSAGE_FAILED.name());

        assertThatThrownBy(() -> restDelete("/api/chat/messages/" + message.getId(), MEMBER_B))
                .isInstanceOfSatisfying(HttpClientErrorException.class, e ->
                        assertThat(e.getStatusCode().value()).isIn(404, 422));

        assertThatThrownBy(() -> restPatch("/api/chat/rooms/" + room.getId(), Map.of("name", "수정시도"), MEMBER_A))
                .isInstanceOfSatisfying(HttpClientErrorException.class, e ->
                        assertThat(e.getStatusCode().value()).isIn(404, 422));
    }
}
