package com.sportsify.chat.e2e;

import com.sportsify.chat.infrastructure.persistence.chatRoom.ChatRoomJpaEntity;
import com.sportsify.common.notification.NotificationEventType;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.stomp.StompSession;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SSE 채팅 알림 E2E 테스트")
@Slf4j
class ChatSseE2ETest extends ChatE2ETestBase {

    private static final long MEMBER_A = 9101L;
    private static final long MEMBER_B = 9102L;

    @Test
    @DisplayName("수신자(MEMBER_B)에게 CHAT_MENTION 외부 알림이 발송된다")
    void DIRECT채팅방_오프라인수신자_SSE_알림발송() throws Exception {
        ChatRoomJpaEntity room = fixture.createRoom("DM방", "DIRECT", "ACTIVE", MEMBER_A);
        fixture.createMember(room.getId(), MEMBER_A, "JOINED");
        fixture.createMember(room.getId(), MEMBER_B, "JOINED");

        SseConnection sseB = subscribeSse(MEMBER_B);
        try {
            StompSession sessionA = connect(MEMBER_A);
            sendMessage(sessionA, room.getId(), "안녕하세요");

            String sseEvent = sseB.events().poll(TIMEOUT_SEC, TimeUnit.SECONDS);
            assertThat(sseEvent)
                    .as("DIRECT 채팅방 메시지 수신 시 오프라인 수신자에게 SSE 알림이 와야 한다")
                    .isEqualTo(NotificationEventType.CHAT_MENTION.name());
        } finally {
            sseB.thread().interrupt();
            sseB.httpClient().close();
        }
    }

    @Test
    @DisplayName("발신자(MEMBER_A)에게는 SSE 알림이 발송되지 않는다")
    void DIRECT채팅방_발신자_SSE_알림_미발송() throws Exception {
        ChatRoomJpaEntity room = fixture.createRoom("DM방", "DIRECT", "ACTIVE", MEMBER_A);
        fixture.createMember(room.getId(), MEMBER_A, "JOINED");
        fixture.createMember(room.getId(), MEMBER_B, "JOINED");

        SseConnection sseA = subscribeSse(MEMBER_A);
        try {
            StompSession sessionA = connect(MEMBER_A);
            sendMessage(sessionA, room.getId(), "내가 보낸 메시지");

            String sseEvent = sseA.events().poll(NO_EVENT_WAIT_SEC, TimeUnit.SECONDS);
            assertThat(sseEvent).as("발신자에게는 SSE 알림이 발송되지 않아야 한다").isNull();
        } finally {
            sseA.thread().interrupt();
            sseA.httpClient().close();
        }
    }

    @Test
    @DisplayName("GAME 채팅방 메시지 전송 시 SSE CHAT_MENTION 이벤트가 발송되지 않는다")
    void GAME채팅방_메시지전송_SSE_알림_미발송() throws Exception {
        ChatRoomJpaEntity room = fixture.createRoom("게임방", "GAME", "ACTIVE", MEMBER_A);
        fixture.createMember(room.getId(), MEMBER_A, "JOINED");
        fixture.createMember(room.getId(), MEMBER_B, "JOINED");

        SseConnection sseB = subscribeSse(MEMBER_B);
        try {
            StompSession sessionA = connect(MEMBER_A);
            sendMessage(sessionA, room.getId(), "게임 채팅 메시지");

            String sseEvent = sseB.events().poll(NO_EVENT_WAIT_SEC, TimeUnit.SECONDS);
            assertThat(sseEvent).as("GAME 채팅방에서는 SSE 외부 알림이 발송되지 않아야 한다").isNull();
        } finally {
            sseB.thread().interrupt();
            sseB.httpClient().close();
        }
    }

    @Test
    @DisplayName("초대 시 초대받은 사용자의 SSE 채널로 CHAT_INVITED 이벤트가 전달된다")
    void 초대_SSE_알림발송() throws Exception {
        ChatRoomJpaEntity room = fixture.createRoom("게임방", "GAME", "ACTIVE", MEMBER_A);
        fixture.createMember(room.getId(), MEMBER_A, "JOINED");
        fixture.createMemberRecord(MEMBER_B);

        SseConnection sseB = subscribeSse(MEMBER_B);
        try {
            restPost("/api/chat/rooms/" + room.getId() + "/invite?inviteeId=" + MEMBER_B, null, MEMBER_A);

            String sseEvent = sseB.events().poll(TIMEOUT_SEC, TimeUnit.SECONDS);
            assertThat(sseEvent)
                    .as("초대 시 초대받은 사용자에게 SSE 알림이 와야 한다")
                    .isEqualTo(NotificationEventType.CHAT_INVITED.name());
        } finally {
            sseB.thread().interrupt();
            sseB.httpClient().close();
        }
    }

    @Test
    @DisplayName("채팅방 WebSocket 구독 중인 수신자는 SSE 알림을 받지 않고 WebSocket으로 메시지를 수신한다")
    void 채팅방구독중_수신자_SSE_미발송_WebSocket_수신() throws Exception {
        ChatRoomJpaEntity room = fixture.createRoom("DM방", "DIRECT", "ACTIVE", MEMBER_A);
        fixture.createMember(room.getId(), MEMBER_A, "JOINED");
        fixture.createMember(room.getId(), MEMBER_B, "JOINED");

        SseConnection sseB = subscribeSse(MEMBER_B);
        try {
            StompSession sessionB = connect(MEMBER_B);
            BlockingQueue<Map<String, Object>> wsEventsB = subscribeRoom(sessionB, room.getId());

            StompSession sessionA = connect(MEMBER_A);
            sendMessage(sessionA, room.getId(), "안녕하세요");

            Map<String, Object> wsEvent = wsEventsB.poll(TIMEOUT_SEC, TimeUnit.SECONDS);
            assertThat(wsEvent).isNotNull();
            assertThat(wsEvent.get("event")).isEqualTo("MESSAGE_SENT");

            String sseEvent = sseB.events().poll(NO_EVENT_WAIT_SEC, TimeUnit.SECONDS);
            assertThat(sseEvent)
                    .as("채팅방 WebSocket 구독 중인 사용자에게는 SSE 알림이 발송되지 않아야 한다")
                    .isNull();
        } finally {
            sseB.thread().interrupt();
            sseB.httpClient().close();
        }
    }

    @Test
    @DisplayName("알림 비활성화 중에서는 메시지 수신 시 외부 알림이 발송되지 않는다")
    void 알림_비활성화_SSE_미발송() throws Exception {
        ChatRoomJpaEntity room = fixture.createRoom("DM방", "DIRECT", "ACTIVE", MEMBER_A);
        fixture.createMember(room.getId(), MEMBER_A, "JOINED");
        fixture.createMember(room.getId(), MEMBER_B, "JOINED");

        SseConnection sseB = subscribeSse(MEMBER_B);
        try {
            restPatch("/api/chat/rooms/" + room.getId() + "/notification?enabled=false", MEMBER_B);
            StompSession sessionA = connect(MEMBER_A);
            sendMessage(sessionA, room.getId(), "안녕하세요 1");

            String sseEvent = sseB.events().poll(NO_EVENT_WAIT_SEC, TimeUnit.SECONDS);
            assertThat(sseEvent)
                    .as("알림 비활성화 상태에서는 SSE 알림이 발송되지 않아야 한다")
                    .isNull();

            restPatch("/api/chat/rooms/" + room.getId() + "/notification?enabled=true", MEMBER_B);
            sendMessage(sessionA, room.getId(), "안녕하세요 2");

            String sseEvent2 = sseB.events().poll(TIMEOUT_SEC, TimeUnit.SECONDS);
            assertThat(sseEvent2)
                    .isEqualTo(NotificationEventType.CHAT_MENTION.name());
        } finally {
            sseB.thread().interrupt();
            sseB.httpClient().close();
        }
    }

    private SseConnection subscribeSse(long memberId) throws InterruptedException {
        String token = jwtProvider.createAccessToken(memberId, "USER");
        BlockingQueue<String> events = new LinkedBlockingQueue<>();

        HttpClient httpClient = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/notifications/stream"))
                .header("Authorization", "Bearer " + token)
                .header("Accept", "text/event-stream")
                .GET()
                .build();

        Thread sseThread = new Thread(() -> {
            try {
                httpClient.send(request, responseInfo ->
                        HttpResponse.BodyHandlers.ofLines().apply(responseInfo)
                ).body()
                        .filter(line -> line.startsWith("data:"))
                        .map(line -> line.substring(5).trim())
                        .forEach(events::offer);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.error("SSE 수신 중 오류 발생: memberId={}", memberId, e);
            }
        }, "sse-" + memberId);
        sseThread.setDaemon(true);
        sseThread.start();

        long deadline = System.currentTimeMillis() + 5_000;
        while (sseThread.getState() == Thread.State.NEW || sseThread.getState() == Thread.State.RUNNABLE) {
            if (System.currentTimeMillis() > deadline) {
                sseThread.interrupt();
                httpClient.close();
                throw new IllegalStateException("SSE 연결 timeout: memberId=" + memberId);
            }
            Thread.sleep(50);
        }

        // 인증 실패·연결 거부로 스레드가 즉시 종료된 경우
        if (sseThread.getState() == Thread.State.TERMINATED) {
            httpClient.close();
            throw new IllegalStateException("SSE 연결 실패 (즉시 종료): memberId=" + memberId);
        }

        return new SseConnection(sseThread, httpClient, events);
    }

    private record SseConnection(Thread thread, HttpClient httpClient, BlockingQueue<String> events) {
    }
}
