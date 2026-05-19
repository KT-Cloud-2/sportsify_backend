package com.sportsify.chat.application;

import com.sportsify.chat.application.event.ChatEventHandler;
import com.sportsify.chat.domain.model.chatRoom.ChatRoomId;
import com.sportsify.chat.domain.model.event.EventEnvelope;
import com.sportsify.chat.domain.model.event.EventType;
import com.sportsify.chat.domain.model.event.chatRoom.RoomArchivedPayload;
import com.sportsify.chat.domain.model.event.chatRoom.RoomDeletePayload;
import com.sportsify.chat.domain.model.event.chatRoomMember.MemberBannedPayload;
import com.sportsify.chat.domain.model.event.message.MessageSentPayload;
import com.sportsify.chat.infrastructure.webSocket.ChatEventPublisher;
import com.sportsify.chat.infrastructure.webSocket.WebSocketSessionRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.mockito.Mockito.*;

/**
 * ChatEventHandler 단위 테스트
 *
 * 검증 목표:
 * - 도메인 이벤트 수신 후 WebSocket 브로드캐스트가 수행되는지 확인
 * - 이벤트 유형별 추가 동작(구독 취소)이 올바르게 트리거되는지 확인
 *
 * Mocking 이유:
 * - ChatEventPublisher: SimpMessagingTemplate → 실제 WebSocket 브로커 불필요
 * - WebSocketSessionRegistry: 세션 저장소 → 실제 WebSocket 세션 없이 구독 취소 동작 검증
 *
 * 실패 포인트:
 * - switch 패턴 매칭에서 payload 타입이 누락되면 구독 취소가 발생하지 않음
 * - publishToRoom 미호출 시 클라이언트가 이벤트를 수신하지 못함
 */
@ExtendWith(MockitoExtension.class)
class ChatEventHandlerTest {

    private static final Long ROOM_ID = 100L;
    private static final Instant NOW = Instant.parse("2026-05-18T12:00:00Z");

    @InjectMocks
    private ChatEventHandler chatEventHandler;

    @Mock
    private ChatEventPublisher publisher;

    @Mock
    private WebSocketSessionRegistry webSocketSessionRegistry;

    // ──────────────────────── 일반 이벤트 ────────────────────────

    /**
     * 메시지 전송과 같은 일반 이벤트는 방 전체에 브로드캐스트되어야 하며,
     * 세션 구독 취소 동작은 발생하지 않아야 한다.
     *
     * 실패 포인트: publishToRoom이 호출되지 않으면 클라이언트가 실시간 메시지를 수신하지 못함
     */
    @Test
    @DisplayName("일반 이벤트 수신 시 방에 브로드캐스트하고 구독 취소는 발생하지 않는다")
    void sendEvent_일반이벤트_방브로드캐스트() {
        EventEnvelope<MessageSentPayload> event = new EventEnvelope<>(
                EventType.MESSAGE_SENT.name(), ROOM_ID, NOW,
                new MessageSentPayload(1L, null, 2L, "TEXT", "안녕하세요")
        );

        chatEventHandler.sendEvent(event);

        verify(publisher).publishToRoom(ROOM_ID, event);
        verifyNoInteractions(webSocketSessionRegistry);
    }

    // ──────────────────────── BAN 이벤트 ────────────────────────

    /**
     * MEMBER_BANNED 이벤트 수신 시 BAN된 멤버의 해당 방 구독을 강제 취소해야 한다.
     * 구독 취소 없이 BAN만 처리하면 BAN된 사용자가 계속 실시간 메시지를 수신하는 보안 취약점이 생김.
     *
     * 실패 포인트: revokeRoomSubscriptionByMember 미호출 시 BAN된 사용자가 메시지를 계속 수신
     */
    @Test
    @DisplayName("MEMBER_BANNED 이벤트 수신 시 해당 멤버의 방 구독이 취소된다")
    void sendEvent_BAN이벤트_멤버구독취소() {
        Long bannedMemberId = 42L;
        EventEnvelope<MemberBannedPayload> event = new EventEnvelope<>(
                EventType.MEMBER_BANNED.name(), ROOM_ID, NOW,
                new MemberBannedPayload(bannedMemberId)
        );

        chatEventHandler.sendEvent(event);

        verify(publisher).publishToRoom(ROOM_ID, event);
        verify(webSocketSessionRegistry).revokeRoomSubscriptionByMember(bannedMemberId, ROOM_ID);
        verify(webSocketSessionRegistry, never()).revokeAllRoomSubscriptions(any());
    }

    // ──────────────────────── 방 삭제 이벤트 ────────────────────────

    /**
     * ROOM_DELETED 이벤트 수신 시 해당 방의 모든 멤버 구독을 일괄 취소해야 한다.
     * 삭제된 방에 구독이 남아 있으면 클라이언트가 방이 삭제된 줄 모르고 메시지를 보낼 수 있음.
     *
     * 실패 포인트: revokeAllRoomSubscriptions 미호출 시 삭제된 방 구독이 남아있음
     */
    @Test
    @DisplayName("ROOM_DELETED 이벤트 수신 시 방의 전체 구독이 취소된다")
    void sendEvent_방삭제이벤트_전체구독취소() {
        EventEnvelope<RoomDeletePayload> event = new EventEnvelope<>(
                EventType.ROOM_DELETED.name(), ROOM_ID, NOW,
                new RoomDeletePayload()
        );

        chatEventHandler.sendEvent(event);

        verify(publisher).publishToRoom(ROOM_ID, event);
        verify(webSocketSessionRegistry).revokeAllRoomSubscriptions(ROOM_ID);
        verify(webSocketSessionRegistry, never()).revokeRoomSubscriptionByMember(any(), any());
    }

    // ──────────────────────── 방 아카이브 이벤트 ────────────────────────

    /**
     * ROOM_ARCHIVED 이벤트 수신 시에도 방 구독 전체 취소가 발생해야 한다.
     * 아카이브된 방은 메시지 수신을 중단해야 하므로 WebSocket 구독도 함께 해제.
     *
     * 실패 포인트: RoomArchivedPayload가 switch case에서 누락되면 구독이 남아 있음
     */
    @Test
    @DisplayName("ROOM_ARCHIVED 이벤트 수신 시 방의 전체 구독이 취소된다")
    void sendEvent_방아카이브이벤트_전체구독취소() {
        EventEnvelope<RoomArchivedPayload> event = new EventEnvelope<>(
                EventType.ROOM_ARCHIVED.name(), ROOM_ID, NOW,
                new RoomArchivedPayload()
        );

        chatEventHandler.sendEvent(event);

        verify(publisher).publishToRoom(ROOM_ID, event);
        verify(webSocketSessionRegistry).revokeAllRoomSubscriptions(ROOM_ID);
    }
}
