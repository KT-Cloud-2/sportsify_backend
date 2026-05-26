package com.sportsify.chat.application;

import com.sportsify.chat.application.event.ChatEventHandler;
import com.sportsify.chat.domain.model.chatRoom.ChatRoomId;
import com.sportsify.chat.domain.model.event.EventEnvelope;
import com.sportsify.chat.domain.model.event.EventType;
import com.sportsify.chat.domain.model.event.chatRoom.RoomArchivedPayload;
import com.sportsify.chat.domain.model.event.chatRoom.RoomDeletePayload;
import com.sportsify.chat.domain.model.event.chatRoomMember.MemberBannedPayload;
import com.sportsify.chat.domain.model.event.message.MessageSentPayload;
import com.sportsify.chat.domain.model.message.*;
import com.sportsify.chat.domain.repository.ChatRoomMemberRepository;
import com.sportsify.chat.domain.repository.MessageRepository;
import com.sportsify.chat.domain.repository.RoomMemberNotifyCache;
import com.sportsify.chat.infrastructure.webSocket.ChatEventPublisher;
import com.sportsify.chat.infrastructure.webSocket.WebSocketSessionRegistry;
import com.sportsify.common.notification.NotificationEventPublisher;
import com.sportsify.member.domain.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

/**
 * ChatEventHandler 단위 테스트
 * <p>
 * 검증 목표:
 * - 도메인 이벤트 수신 후 WebSocket 브로드캐스트가 수행되는지 확인
 * - 비 메시지 이벤트 발생 시 알림 메시지가 DB에 저장되고 alertMessageId가 포함되는지 확인
 * - 이벤트 유형별 추가 동작(구독 취소)이 올바르게 트리거되는지 확인
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

    @Mock
    private MessageRepository messageRepo;

    @Mock
    private RoomMemberNotifyCache roomMemberNotifyCache;

    @Mock
    private NotificationEventPublisher notificationEventPublisher;

    @Mock
    private ChatRoomMemberRepository chatRoomMemberRepo;

    @Mock
    private MemberRepository memberRepo;

    @BeforeEach
    void setUp() {
        lenient().when(roomMemberNotifyCache.getNotifiableMemberIds(any())).thenReturn(Optional.of(Set.of()));
    }

    // ──────────────────────── 메시지 이벤트 ────────────────────────

    /**
     * MessagePayload 이벤트(MESSAGE_SENT 등)는 알림 저장 없이 즉시 브로드캐스트되어야 한다.
     * messageRepo 호출이 발생하면 불필요한 SYSTEM 메시지가 쌓인다.
     */
    @Test
    @DisplayName("MESSAGE_SENT 이벤트는 알림 저장 없이 원본 이벤트를 그대로 발행한다")
    void sendEvent_메시지이벤트_알림저장없이_방브로드캐스트() {
        EventEnvelope<MessageSentPayload> event = new EventEnvelope<>(
                EventType.MESSAGE_SENT.name(), ROOM_ID, NOW,
                new MessageSentPayload(1L, null, 2L, "TEXT", "안녕하세요", null), null, null, null
        );

        chatEventHandler.sendEvent(event);

        verify(publisher).publishToRoom(ROOM_ID, event);
        verifyNoInteractions(webSocketSessionRegistry);
        verifyNoInteractions(messageRepo);
    }

    // ──────────────────────── BAN 이벤트 ────────────────────────

    /**
     * MEMBER_BANNED 이벤트 수신 시:
     * 1. 알림 메시지가 저장되어야 한다
     * 2. alertMessageId가 포함된 이벤트가 발행되어야 한다
     * 3. BAN된 멤버의 방 구독이 취소되어야 한다
     * <p>
     * 실패 포인트: alertMessageId 누락 시 클라이언트가 SYSTEM 메시지를 연결하지 못함
     */
    @Test
    @DisplayName("MEMBER_BANNED 이벤트 수신 시 알림 저장 후 alertMessageId 포함 이벤트를 발행하고 멤버 구독을 취소한다")
    void sendEvent_BAN이벤트_알림저장_alertMessageId포함_멤버구독취소() {
        Long bannedMemberId = 42L;
        EventEnvelope<MemberBannedPayload> event = new EventEnvelope<>(
                EventType.MEMBER_BANNED.name(), ROOM_ID, NOW,
                new MemberBannedPayload(bannedMemberId), null, null, null
        );
        given(messageRepo.save(any())).willReturn(alertMessage(99L));

        chatEventHandler.sendEvent(event);

        verify(messageRepo).save(any());
        ArgumentCaptor<EventEnvelope> captor = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(publisher).publishToRoom(eq(ROOM_ID), captor.capture());
        assertThat(captor.getValue().alertMessageId()).isEqualTo(99L);
        verify(webSocketSessionRegistry).revokeRoomSubscriptionByMember(bannedMemberId, ROOM_ID);
        verify(webSocketSessionRegistry, never()).revokeAllRoomSubscriptions(any());
    }

    // ──────────────────────── 방 삭제 이벤트 ────────────────────────

    /**
     * ROOM_DELETED 이벤트 수신 시 alertMessageId 포함 이벤트 발행 + 전체 구독 취소.
     * <p>
     * 실패 포인트: revokeAllRoomSubscriptions 미호출 시 삭제된 방 구독이 남아있음
     */
    @Test
    @DisplayName("ROOM_DELETED 이벤트 수신 시 알림 저장 후 alertMessageId 포함 이벤트를 발행하고 전체 구독을 취소한다")
    void sendEvent_방삭제이벤트_알림저장_전체구독취소() {
        EventEnvelope<RoomDeletePayload> event = new EventEnvelope<>(
                EventType.ROOM_DELETED.name(), ROOM_ID, NOW,
                new RoomDeletePayload(), null, null, null
        );
        given(messageRepo.save(any())).willReturn(alertMessage(101L));

        chatEventHandler.sendEvent(event);

        verify(messageRepo).save(any());
        ArgumentCaptor<EventEnvelope> captor = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(publisher).publishToRoom(eq(ROOM_ID), captor.capture());
        assertThat(captor.getValue().alertMessageId()).isEqualTo(101L);
        verify(webSocketSessionRegistry).revokeAllRoomSubscriptions(ROOM_ID);
        verify(webSocketSessionRegistry, never()).revokeRoomSubscriptionByMember(any(), any());
    }

    // ──────────────────────── 방 아카이브 이벤트 ────────────────────────

    /**
     * ROOM_ARCHIVED 이벤트 수신 시에도 alertMessageId 포함 이벤트 발행 + 전체 구독 취소.
     * <p>
     * 실패 포인트: RoomArchivedPayload가 switch case에서 누락되면 구독이 남아 있음
     */
    @Test
    @DisplayName("ROOM_ARCHIVED 이벤트 수신 시 알림 저장 후 alertMessageId 포함 이벤트를 발행하고 전체 구독을 취소한다")
    void sendEvent_방아카이브이벤트_알림저장_전체구독취소() {
        EventEnvelope<RoomArchivedPayload> event = new EventEnvelope<>(
                EventType.ROOM_ARCHIVED.name(), ROOM_ID, NOW,
                new RoomArchivedPayload(), null, null, null
        );
        given(messageRepo.save(any())).willReturn(alertMessage(102L));

        chatEventHandler.sendEvent(event);

        verify(messageRepo).save(any());
        ArgumentCaptor<EventEnvelope> captor = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(publisher).publishToRoom(eq(ROOM_ID), captor.capture());
        assertThat(captor.getValue().alertMessageId()).isEqualTo(102L);
        verify(webSocketSessionRegistry).revokeAllRoomSubscriptions(ROOM_ID);
    }

    // ──────────────────────── 픽스처 헬퍼 ────────────────────────

    private Message alertMessage(Long id) {
        return Message.restore(
                MessageId.of(id),
                ChatRoomId.of(ROOM_ID),
                null,
                MessageContent.of("테스트 알림"),
                MessageType.SYSTEM,
                MessageStatus.ACTIVE,
                NOW
        );
    }
}
