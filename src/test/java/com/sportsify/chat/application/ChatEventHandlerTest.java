package com.sportsify.chat.application;

import com.sportsify.chat.application.event.ChatEventHandler;
import com.sportsify.chat.domain.model.chatRoom.ChatRoomId;
import com.sportsify.chat.domain.model.chatRoom.ChatRoomType;
import com.sportsify.chat.domain.model.chatRoom.MemberId;
import com.sportsify.chat.domain.model.chatRoomMember.ChatRoomMember;
import com.sportsify.chat.domain.model.event.EventEnvelope;
import com.sportsify.chat.domain.model.event.EventType;
import com.sportsify.chat.domain.model.event.chatRoom.RoomArchivedPayload;
import com.sportsify.chat.domain.model.event.chatRoom.RoomDeletePayload;
import com.sportsify.chat.domain.model.event.chatRoom.RoomUnarchivedPayload;
import com.sportsify.chat.domain.model.event.chatRoom.RoomUpdatePayload;
import com.sportsify.chat.domain.model.event.chatRoomMember.MemberBannedPayload;
import com.sportsify.chat.domain.model.event.chatRoomMember.MemberInvitePayload;
import com.sportsify.chat.domain.model.event.chatRoomMember.MemberJoinPayload;
import com.sportsify.chat.domain.model.event.chatRoomMember.MemberLeftPayload;
import com.sportsify.chat.domain.model.event.message.MessageDeletePayLoad;
import com.sportsify.chat.domain.model.event.message.MessageSentPayload;
import com.sportsify.chat.domain.model.message.*;
import com.sportsify.chat.domain.repository.ChatRoomMemberRepository;
import com.sportsify.chat.domain.repository.MessageRepository;
import com.sportsify.chat.domain.repository.RoomMemberNotifyCache;
import com.sportsify.chat.infrastructure.webSocket.ChatEventPublisher;
import com.sportsify.chat.infrastructure.webSocket.WebSocketSessionRegistry;
import com.sportsify.common.notification.NotificationEventPublisher;
import com.sportsify.common.notification.NotificationEventType;
import com.sportsify.common.notification.payload.ChatMentionPayload;
import com.sportsify.common.notification.payload.NotificationPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

// EventEnvelope 생성자 순서: (event, roomId, occurredAt, payload, alertMessageId, roomType, roomName)
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

    @BeforeEach
    void setUp() {
        lenient().when(roomMemberNotifyCache.getNotifiableMemberIds(any())).thenReturn(Optional.of(Set.of()));
    }

    // ──────────────────────── 메시지 이벤트 ────────────────────────

    @Test
    @DisplayName("GAME 방 MESSAGE_SENT 이벤트 발생 시 알림 없이 메시지가 전송된다")
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

    @Test
    @DisplayName("DIRECT 방 MESSAGE_SENT 이벤트 발생 시 알림과 메시지 전송이 실행된다")
    void sendEvent_메시지이벤트_DIRECT_채팅방_sendMessageNotification_실행() {
        Long senderId = 1L;
        Long receiverId = 2L;
        given(roomMemberNotifyCache.getNotifiableMemberIds(ROOM_ID))
                .willReturn(Optional.of(Set.of(senderId, receiverId)));
        given(webSocketSessionRegistry.getSubscribedMemberIds(ROOM_ID)).willReturn(Set.of());

        EventEnvelope<MessageSentPayload> event = new EventEnvelope<>(
                EventType.MESSAGE_SENT.name(), ROOM_ID, NOW,
                new MessageSentPayload(1L, null, senderId, "TEXT", "안녕하세요", null),
                null, ChatRoomType.DIRECT, null
        );

        chatEventHandler.sendEvent(event);

        verify(publisher).publishToRoom(ROOM_ID, event);
        verify(webSocketSessionRegistry).getSubscribedMemberIds(ROOM_ID);
        verifyNoInteractions(messageRepo);
    }

    @Test
    @DisplayName("DIRECT 방 TEXT 메시지 발신 시 오프라인 수신자에게 CHAT_MENTION 알림을 발행한다")
    void sendEvent_DIRECT_방_TEXT_메시지_오프라인수신자_알림발행() {
        Long senderId = 1L;
        Long receiverId = 2L;
        given(roomMemberNotifyCache.getNotifiableMemberIds(ROOM_ID))
                .willReturn(Optional.of(Set.of(senderId, receiverId)));
        given(webSocketSessionRegistry.getSubscribedMemberIds(ROOM_ID)).willReturn(Set.of());

        EventEnvelope<MessageSentPayload> event = new EventEnvelope<>(
                EventType.MESSAGE_SENT.name(), ROOM_ID, NOW,
                new MessageSentPayload(10L, null, senderId, "TEXT", "안녕하세요", null),
                null, ChatRoomType.DIRECT, "방이름"
        );

        chatEventHandler.sendEvent(event);

        ArgumentCaptor<NotificationPayload> captor = ArgumentCaptor.forClass(NotificationPayload.class);
        verify(notificationEventPublisher).publish(eq(NotificationEventType.CHAT_MENTION), captor.capture());
        ChatMentionPayload captured = (ChatMentionPayload) captor.getValue();
        assertThat(captured.memberId()).isEqualTo(receiverId);
        assertThat(captured.senderId()).isEqualTo(senderId);
        assertThat(captured.roomId()).isEqualTo(ROOM_ID);
        assertThat(captured.message()).isEqualTo("안녕하세요");
    }

    @Test
    @DisplayName("DIRECT 방 IMAGE 메시지 발신 시 이미지 알림 메시지로 발행한다")
    void sendEvent_DIRECT_방_IMAGE_메시지_이미지알림_발행() {
        Long senderId = 1L;
        Long receiverId = 2L;
        given(roomMemberNotifyCache.getNotifiableMemberIds(ROOM_ID))
                .willReturn(Optional.of(Set.of(senderId, receiverId)));
        given(webSocketSessionRegistry.getSubscribedMemberIds(ROOM_ID)).willReturn(Set.of());

        EventEnvelope<MessageSentPayload> event = new EventEnvelope<>(
                EventType.MESSAGE_SENT.name(), ROOM_ID, NOW,
                new MessageSentPayload(10L, null, senderId, "IMAGE", null, null),
                null, ChatRoomType.DIRECT, "방이름"
        );

        chatEventHandler.sendEvent(event);

        verify(notificationEventPublisher).publish(
                eq(NotificationEventType.CHAT_MENTION),
                eq(ChatMentionPayload.ofImage(receiverId, ROOM_ID, "방이름", senderId))
        );
    }

    @Test
    @DisplayName("DIRECT 방 FILE 메시지 발신 시 파일 알림 메시지로 발행한다")
    void sendEvent_DIRECT_방_FILE_메시지_파일알림_발행() {
        Long senderId = 1L;
        Long receiverId = 2L;
        given(roomMemberNotifyCache.getNotifiableMemberIds(ROOM_ID))
                .willReturn(Optional.of(Set.of(senderId, receiverId)));
        given(webSocketSessionRegistry.getSubscribedMemberIds(ROOM_ID)).willReturn(Set.of());

        EventEnvelope<MessageSentPayload> event = new EventEnvelope<>(
                EventType.MESSAGE_SENT.name(), ROOM_ID, NOW,
                new MessageSentPayload(10L, null, senderId, "FILE", null, null),
                null, ChatRoomType.DIRECT, "방이름"
        );

        chatEventHandler.sendEvent(event);

        verify(notificationEventPublisher).publish(
                eq(NotificationEventType.CHAT_MENTION),
                eq(ChatMentionPayload.ofFile(receiverId, ROOM_ID, "방이름", senderId))
        );
    }

    @Test
    @DisplayName("DIRECT 방 메시지 발신 시 온라인(구독 중) 수신자에게는 알림을 발행하지 않는다")
    void sendEvent_DIRECT_방_메시지_온라인수신자_알림미발행() {
        Long senderId = 1L;
        Long receiverId = 2L;
        given(roomMemberNotifyCache.getNotifiableMemberIds(ROOM_ID))
                .willReturn(Optional.of(Set.of(senderId, receiverId)));
        given(webSocketSessionRegistry.getSubscribedMemberIds(ROOM_ID)).willReturn(Set.of(receiverId));

        EventEnvelope<MessageSentPayload> event = new EventEnvelope<>(
                EventType.MESSAGE_SENT.name(), ROOM_ID, NOW,
                new MessageSentPayload(10L, null, senderId, "TEXT", "안녕", null),
                null, ChatRoomType.DIRECT, "방이름"
        );

        chatEventHandler.sendEvent(event);

        verify(notificationEventPublisher, never()).publish(any(NotificationEventType.class), any(NotificationPayload.class));
    }

    @Test
    @DisplayName("DIRECT 방 메시지 발신 시 발신자 본인에게는 알림을 발행하지 않는다")
    void sendEvent_DIRECT_방_메시지_발신자_본인_알림미발행() {
        Long senderId = 1L;
        given(roomMemberNotifyCache.getNotifiableMemberIds(ROOM_ID))
                .willReturn(Optional.of(Set.of(senderId)));
        given(webSocketSessionRegistry.getSubscribedMemberIds(ROOM_ID)).willReturn(Set.of());

        EventEnvelope<MessageSentPayload> event = new EventEnvelope<>(
                EventType.MESSAGE_SENT.name(), ROOM_ID, NOW,
                new MessageSentPayload(10L, null, senderId, "TEXT", "안녕", null),
                null, ChatRoomType.DIRECT, "방이름"
        );

        chatEventHandler.sendEvent(event);

        verify(notificationEventPublisher, never()).publish(any(NotificationEventType.class), any(NotificationPayload.class));
    }

    @Test
    @DisplayName("DIRECT 방 메시지 발신 시 알림 대상이 없으면 알림을 발행하지 않는다")
    void sendEvent_DIRECT_방_메시지_알림대상없음_알림미발행() {
        EventEnvelope<MessageSentPayload> event = new EventEnvelope<>(
                EventType.MESSAGE_SENT.name(), ROOM_ID, NOW,
                new MessageSentPayload(10L, null, 1L, "TEXT", "안녕", null),
                null, ChatRoomType.DIRECT, "방이름"
        );

        chatEventHandler.sendEvent(event);

        verify(notificationEventPublisher, never()).publish(any(NotificationEventType.class), any(NotificationPayload.class));
        verifyNoInteractions(webSocketSessionRegistry);
    }

    @Test
    @DisplayName("알림 발행 중 예외가 발생해도 예외가 전파되지 않고 이벤트 처리가 완료된다")
    void sendEvent_메시지알림_발행예외_전파안함() {
        given(roomMemberNotifyCache.getNotifiableMemberIds(ROOM_ID))
                .willReturn(Optional.of(Set.of(1L, 2L)));
        given(webSocketSessionRegistry.getSubscribedMemberIds(ROOM_ID)).willReturn(Set.of());
        doThrow(new RuntimeException("Redis 장애")).when(notificationEventPublisher).publish(any(NotificationEventType.class), any(NotificationPayload.class));

        EventEnvelope<MessageSentPayload> event = new EventEnvelope<>(
                EventType.MESSAGE_SENT.name(), ROOM_ID, NOW,
                new MessageSentPayload(10L, null, 1L, "TEXT", "안녕", null),
                null, ChatRoomType.DIRECT, "방이름"
        );

        assertThatCode(() -> chatEventHandler.sendEvent(event)).doesNotThrowAnyException();
        verify(publisher).publishToRoom(ROOM_ID, event);
    }

    @Test
    @DisplayName("알림 대상 캐시 미스 시 DB를 조회하고 캐시를 갱신한 뒤 알림을 발행한다")
    void sendEvent_DIRECT_방_메시지_캐시미스_DB조회_캐시갱신() {
        Long senderId = 1L;
        Long receiverId = 2L;
        given(roomMemberNotifyCache.getNotifiableMemberIds(ROOM_ID)).willReturn(Optional.empty());

        ChatRoomMember member = mock(ChatRoomMember.class);
        given(member.isNotificationEnabled()).willReturn(true);
        given(member.getMemberId()).willReturn(MemberId.of(receiverId));
        given(chatRoomMemberRepo.findActiveByRoom(ChatRoomId.of(ROOM_ID))).willReturn(List.of(member));
        given(webSocketSessionRegistry.getSubscribedMemberIds(ROOM_ID)).willReturn(Set.of());

        EventEnvelope<MessageSentPayload> event = new EventEnvelope<>(
                EventType.MESSAGE_SENT.name(), ROOM_ID, NOW,
                new MessageSentPayload(10L, null, senderId, "TEXT", "안녕", null),
                null, ChatRoomType.DIRECT, "방이름"
        );

        chatEventHandler.sendEvent(event);

        verify(chatRoomMemberRepo).findActiveByRoom(ChatRoomId.of(ROOM_ID));
        verify(roomMemberNotifyCache).populate(eq(ROOM_ID), any());
        verify(notificationEventPublisher).publish(eq(NotificationEventType.CHAT_MENTION), any(NotificationPayload.class));
    }

    @Test
    @DisplayName("MESSAGE_DELETED 이벤트 수신 시 알림 저장 없이 방에 브로드캐스트한다")
    void sendEvent_MESSAGE_DELETED_이벤트_알림없이_방브로드캐스트() {
        EventEnvelope<MessageDeletePayLoad> event = new EventEnvelope<>(
                EventType.MESSAGE_DELETED.name(), ROOM_ID, NOW,
                new MessageDeletePayLoad(55L), null, null, null
        );

        chatEventHandler.sendEvent(event);

        verify(publisher).publishToRoom(ROOM_ID, event);
        verifyNoInteractions(messageRepo);
        verifyNoInteractions(webSocketSessionRegistry);
    }

    // ──────────────────────── BAN 이벤트 ────────────────────────

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

    @Test
    @DisplayName("DIRECT 방 BAN 이벤트 수신 시 알림 캐시에서 해당 멤버를 제거한다")
    void sendEvent_DIRECT_방_BAN이벤트_알림캐시_멤버제거() {
        Long bannedMemberId = 42L;
        given(messageRepo.save(any())).willReturn(alertMessage(99L));

        EventEnvelope<MemberBannedPayload> event = new EventEnvelope<>(
                EventType.MEMBER_BANNED.name(), ROOM_ID, NOW,
                new MemberBannedPayload(bannedMemberId), null, ChatRoomType.DIRECT, null
        );

        chatEventHandler.sendEvent(event);

        verify(roomMemberNotifyCache).remove(ROOM_ID, bannedMemberId);
    }

    @Test
    @DisplayName("GAME 방 BAN 이벤트 수신 시 알림 캐시를 수정하지 않는다")
    void sendEvent_GAME_방_BAN이벤트_알림캐시_미수정() {
        given(messageRepo.save(any())).willReturn(alertMessage(99L));

        EventEnvelope<MemberBannedPayload> event = new EventEnvelope<>(
                EventType.MEMBER_BANNED.name(), ROOM_ID, NOW,
                new MemberBannedPayload(42L), null, null, null
        );

        chatEventHandler.sendEvent(event);

        verify(roomMemberNotifyCache, never()).remove(any(), any());
    }

    // ──────────────────────── 방 삭제 이벤트 ────────────────────────

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

    @Test
    @DisplayName("DIRECT 방 삭제 이벤트 수신 시 알림 캐시를 초기화한다")
    void sendEvent_DIRECT_방삭제이벤트_알림캐시_초기화() {
        given(messageRepo.save(any())).willReturn(alertMessage(101L));

        EventEnvelope<RoomDeletePayload> event = new EventEnvelope<>(
                EventType.ROOM_DELETED.name(), ROOM_ID, NOW,
                new RoomDeletePayload(), null, ChatRoomType.DIRECT, null
        );

        chatEventHandler.sendEvent(event);

        verify(roomMemberNotifyCache).evict(ROOM_ID);
    }

    // ──────────────────────── 방 아카이브 이벤트 ────────────────────────

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

    @Test
    @DisplayName("DIRECT 방 보관 이벤트 수신 시 알림 캐시를 초기화한다")
    void sendEvent_DIRECT_방보관이벤트_알림캐시_초기화() {
        given(messageRepo.save(any())).willReturn(alertMessage(102L));

        EventEnvelope<RoomArchivedPayload> event = new EventEnvelope<>(
                EventType.ROOM_ARCHIVED.name(), ROOM_ID, NOW,
                new RoomArchivedPayload(), null, ChatRoomType.DIRECT, null
        );

        chatEventHandler.sendEvent(event);

        verify(roomMemberNotifyCache).evict(ROOM_ID);
    }

    // ──────────────────────── 방 수정 / 언아카이브 이벤트 ────────────────────────

    @Test
    @DisplayName("ROOM_UPDATED 이벤트 수신 시 시스템 메시지를 저장하고 alertMessageId 포함 이벤트를 방에 발행한다")
    void sendEvent_ROOM_UPDATED_이벤트_시스템메시지_저장_방브로드캐스트() {
        EventEnvelope<RoomUpdatePayload> event = new EventEnvelope<>(
                EventType.ROOM_UPDATED.name(), ROOM_ID, NOW,
                new RoomUpdatePayload("새 방 이름", null), null, null, null
        );
        given(messageRepo.save(any())).willReturn(alertMessage(105L));

        chatEventHandler.sendEvent(event);

        verify(messageRepo).save(any());
        ArgumentCaptor<EventEnvelope> captor = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(publisher).publishToRoom(eq(ROOM_ID), captor.capture());
        assertThat(captor.getValue().alertMessageId()).isEqualTo(105L);
        verifyNoInteractions(webSocketSessionRegistry);
    }

    @Test
    @DisplayName("ROOM_UNARCHIVED 이벤트 수신 시 시스템 메시지를 저장하고 alertMessageId 포함 이벤트를 방에 발행한다")
    void sendEvent_ROOM_UNARCHIVED_이벤트_시스템메시지_저장_방브로드캐스트() {
        EventEnvelope<RoomUnarchivedPayload> event = new EventEnvelope<>(
                EventType.ROOM_UNARCHIVED.name(), ROOM_ID, NOW,
                new RoomUnarchivedPayload(), null, null, null
        );
        given(messageRepo.save(any())).willReturn(alertMessage(106L));

        chatEventHandler.sendEvent(event);

        verify(messageRepo).save(any());
        ArgumentCaptor<EventEnvelope> captor = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(publisher).publishToRoom(eq(ROOM_ID), captor.capture());
        assertThat(captor.getValue().alertMessageId()).isEqualTo(106L);
        verifyNoInteractions(webSocketSessionRegistry);
    }

    // ──────────────────────── 멤버 입장 / 퇴장 이벤트 ────────────────────────

    @Test
    @DisplayName("DIRECT 방 멤버 참여 이벤트 수신 시 알림 캐시에 멤버를 등록한다")
    void sendEvent_DIRECT_방_멤버참여이벤트_알림캐시_등록() {
        Long joinedMemberId = 5L;
        given(messageRepo.save(any())).willReturn(alertMessage(103L));

        EventEnvelope<MemberJoinPayload> event = new EventEnvelope<>(
                EventType.MEMBER_JOINED.name(), ROOM_ID, NOW,
                new MemberJoinPayload(joinedMemberId), null, ChatRoomType.DIRECT, null
        );

        chatEventHandler.sendEvent(event);

        verify(roomMemberNotifyCache).put(ROOM_ID, joinedMemberId, true);
    }

    @Test
    @DisplayName("GAME 방 멤버 참여 이벤트 수신 시 알림 캐시에 등록하지 않는다")
    void sendEvent_GAME_방_멤버참여이벤트_알림캐시_미등록() {
        given(messageRepo.save(any())).willReturn(alertMessage(103L));

        EventEnvelope<MemberJoinPayload> event = new EventEnvelope<>(
                EventType.MEMBER_JOINED.name(), ROOM_ID, NOW,
                new MemberJoinPayload(5L), null, null, null
        );

        chatEventHandler.sendEvent(event);

        verify(roomMemberNotifyCache, never()).put(any(), any(), anyBoolean());
    }

    @Test
    @DisplayName("DIRECT 방 멤버 퇴장 이벤트 수신 시 알림 캐시에서 멤버를 제거한다")
    void sendEvent_DIRECT_방_멤버퇴장이벤트_알림캐시_멤버제거() {
        Long leftMemberId = 7L;
        given(messageRepo.save(any())).willReturn(alertMessage(104L));

        EventEnvelope<MemberLeftPayload> event = new EventEnvelope<>(
                EventType.MEMBER_LEFT.name(), ROOM_ID, NOW,
                new MemberLeftPayload(leftMemberId), null, ChatRoomType.DIRECT, null
        );

        chatEventHandler.sendEvent(event);

        verify(roomMemberNotifyCache).remove(ROOM_ID, leftMemberId);
    }

    @Test
    @DisplayName("GAME 방 멤버 퇴장 이벤트 수신 시 알림 캐시를 수정하지 않는다")
    void sendEvent_GAME_방_멤버퇴장이벤트_알림캐시_미수정() {
        given(messageRepo.save(any())).willReturn(alertMessage(104L));

        EventEnvelope<MemberLeftPayload> event = new EventEnvelope<>(
                EventType.MEMBER_LEFT.name(), ROOM_ID, NOW,
                new MemberLeftPayload(7L), null, null, null
        );

        chatEventHandler.sendEvent(event);

        verify(roomMemberNotifyCache, never()).remove(any(), any());
    }


    // ──────────────────────── 초대 알림 발행 ────────────────────────

    @Test
    @DisplayName("DIRECT 방 초대 이벤트 수신 시 초대받은 멤버에게 CHAT_INVITED 알림을 발행한다")
    void sendEvent_DIRECT_방_초대이벤트_CHAT_INVITED_발행() {
        Long inviterId = 1L;
        Long invitedId = 2L;
        given(messageRepo.save(any())).willReturn(alertMessage(10L));

        EventEnvelope<MemberInvitePayload> event = new EventEnvelope<>(
                EventType.MEMBER_INVITED.name(), ROOM_ID, NOW,
                new MemberInvitePayload(inviterId, invitedId),
                null, ChatRoomType.DIRECT, "방이름"
        );

        chatEventHandler.sendEvent(event);

        ArgumentCaptor<NotificationPayload> captor = ArgumentCaptor.forClass(NotificationPayload.class);
        verify(notificationEventPublisher).publish(eq(NotificationEventType.CHAT_INVITED), captor.capture());
        ChatMentionPayload captured = (ChatMentionPayload) captor.getValue();
        assertThat(captured.memberId()).isEqualTo(invitedId);
        assertThat(captured.senderId()).isEqualTo(inviterId);
        assertThat(captured.roomId()).isEqualTo(ROOM_ID);
        assertThat(captured.roomName()).isEqualTo("방이름");
    }

    @Test
    @DisplayName("GAME 방 초대 이벤트 수신 시에도 CHAT_INVITED 알림을 발행한다")
    void sendEvent_GAME_방_초대이벤트_알림발행() {
        Long inviterId = 1L;
        Long invitedId = 2L;
        given(messageRepo.save(any())).willReturn(alertMessage(10L));

        EventEnvelope<MemberInvitePayload> event = new EventEnvelope<>(
                EventType.MEMBER_INVITED.name(), ROOM_ID, NOW,
                new MemberInvitePayload(inviterId, invitedId),
                null, null, null
        );

        chatEventHandler.sendEvent(event);

        verify(notificationEventPublisher).publish(
                eq(NotificationEventType.CHAT_INVITED),
                eq(ChatMentionPayload.ofText(invitedId, ROOM_ID, "채팅방", inviterId, "채팅방에 초대되었습니다."))
        );
    }

    @Test
    @DisplayName("초대 알림 발행 중 예외가 발생해도 예외가 전파되지 않고 이벤트 처리가 완료된다")
    void sendEvent_초대알림_발행예외_전파안함() {
        given(messageRepo.save(any())).willReturn(alertMessage(10L));
        doThrow(new RuntimeException("알림 발행 실패")).when(notificationEventPublisher).publish(any(NotificationEventType.class), any(NotificationPayload.class));

        EventEnvelope<MemberInvitePayload> event = new EventEnvelope<>(
                EventType.MEMBER_INVITED.name(), ROOM_ID, NOW,
                new MemberInvitePayload(1L, 2L),
                null, ChatRoomType.DIRECT, "방이름"
        );

        assertThatCode(() -> chatEventHandler.sendEvent(event)).doesNotThrowAnyException();
        verify(publisher).publishToRoom(eq(ROOM_ID), any());
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
