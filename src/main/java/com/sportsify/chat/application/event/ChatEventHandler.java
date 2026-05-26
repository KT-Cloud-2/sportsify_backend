package com.sportsify.chat.application.event;


import com.sportsify.chat.domain.model.chatRoom.ChatRoom;
import com.sportsify.chat.domain.model.chatRoom.ChatRoomId;
import com.sportsify.chat.domain.model.chatRoom.ChatRoomType;
import com.sportsify.chat.domain.model.chatRoomMember.ChatRoomMember;
import com.sportsify.chat.domain.model.event.EventEnvelope;
import com.sportsify.chat.domain.model.event.EventType;
import com.sportsify.chat.domain.model.event.chatRoom.RoomArchivedPayload;
import com.sportsify.chat.domain.model.event.chatRoom.RoomDeletePayload;
import com.sportsify.chat.domain.model.event.chatRoomMember.MemberBannedPayload;
import com.sportsify.chat.domain.model.event.chatRoomMember.MemberInvitePayload;
import com.sportsify.chat.domain.model.event.chatRoomMember.MemberJoinPayload;
import com.sportsify.chat.domain.model.event.chatRoomMember.MemberLeftPayload;
import com.sportsify.chat.domain.model.event.message.MessagePayload;
import com.sportsify.chat.domain.model.event.message.MessageSentPayload;
import com.sportsify.chat.domain.model.message.Message;
import com.sportsify.chat.domain.model.message.MessageContent;
import com.sportsify.chat.domain.repository.ChatRoomMemberRepository;
import com.sportsify.chat.domain.repository.ChatRoomRepository;
import com.sportsify.chat.domain.repository.MessageRepository;
import com.sportsify.chat.domain.repository.RoomMemberNotifyCache;
import com.sportsify.chat.infrastructure.webSocket.ChatEventPublisher;
import com.sportsify.chat.infrastructure.webSocket.WebSocketSessionRegistry;
import com.sportsify.common.notification.NotificationEventPublisher;
import com.sportsify.common.notification.NotificationEventType;
import com.sportsify.common.notification.payload.ChatMentionPayload;
import com.sportsify.member.domain.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatEventHandler {

    private final ChatEventPublisher publisher;
    private final WebSocketSessionRegistry webSocketSessionRegistry;
    private final MessageRepository messageRepo;
    private final RoomMemberNotifyCache roomMemberNotifyCache;
    private final NotificationEventPublisher notificationEventPublisher;
    private final ChatRoomMemberRepository chatRoomMemberRepo;
    private final ChatRoomRepository chatRoomRepo;
    private final MemberRepository memberRepo;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendEvent(EventEnvelope<?> event) {
        Object payload = event.payload();
        boolean isDirect = event.roomType() == ChatRoomType.DIRECT;

        if (payload instanceof MessagePayload) {
            publisher.publishToRoom(event.roomId(), event);
            if (payload instanceof MessageSentPayload sentPayload && isDirect) {
                sendMessageNotification(event.roomId(), sentPayload);
            }
            return;
        }

        String alertText = switch (EventType.valueOf(event.event())) {
            case MEMBER_JOINED -> "새 멤버가 채팅방에 참여했습니다.";
            case MEMBER_LEFT -> "멤버가 채팅방을 나갔습니다.";
            case MEMBER_INVITED -> "새 멤버를 초대했습니다.";
            case MEMBER_BANNED -> "멤버가 강퇴되었습니다.";
            case MEMBER_REJECTED -> "초대가 거절되었습니다.";
            case ROOM_UPDATED -> "채팅방 정보가 변경되었습니다.";
            case ROOM_DELETED -> "채팅방이 삭제되었습니다.";
            case ROOM_ARCHIVED -> "채팅방이 보관되었습니다.";
            case ROOM_UNARCHIVED -> "채팅방 보관이 해제되었습니다.";
            default -> null;
        };

        EventEnvelope<?> toPublish = event;
        if (alertText != null) {
            Message alert = messageRepo.save(Message.createAlert(
                    ChatRoomId.of(event.roomId()),
                    MessageContent.of(alertText),
                    event.occurredAt()
            ));
            toPublish = event.withAlertMessageId(alert.getId().value());
        }
        publisher.publishToRoom(event.roomId(), toPublish);

        switch (payload) {
            case MemberBannedPayload p -> {
                webSocketSessionRegistry.revokeRoomSubscriptionByMember(p.memberId(), event.roomId());
                if (isDirect) roomMemberNotifyCache.remove(event.roomId(), p.memberId());
            }
            case RoomDeletePayload _, RoomArchivedPayload _ -> {
                webSocketSessionRegistry.revokeAllRoomSubscriptions(event.roomId());
                if (isDirect) roomMemberNotifyCache.evict(event.roomId());
            }
            case MemberJoinPayload p -> { if (isDirect) roomMemberNotifyCache.put(event.roomId(), p.memberId(), true); }
            case MemberLeftPayload p -> { if (isDirect) roomMemberNotifyCache.remove(event.roomId(), p.memberId()); }
            case MemberInvitePayload p -> { if (isDirect) sendInviteNotification(event.roomId(), p); }
            default -> {}
        }
    }

    private void sendMessageNotification(Long roomId, MessageSentPayload payload) {
        try {
            Set<Long> notifiableIds = resolveNotifiableMemberIds(roomId);
            if (notifiableIds.isEmpty()) return;

            String roomName = chatRoomRepo.findById(ChatRoomId.of(roomId))
                    .map(r -> r.getName().value())
                    .orElse("채팅방");
            String senderName = memberRepo.findById(payload.senderId())
                    .map(m -> m.getNickname() != null ? m.getNickname() : "사용자")
                    .orElse("사용자");

            Set<Long> activeSubscribers = webSocketSessionRegistry.getSubscribedMemberIds(roomId);

            notifiableIds.stream()
                    .filter(memberId -> !memberId.equals(payload.senderId()))
                    .filter(memberId -> !activeSubscribers.contains(memberId))
                    .forEach(memberId -> {
                        ChatMentionPayload notifyPayload = buildMentionPayload(
                                memberId, roomId, roomName, payload.senderId(), senderName, payload
                        );
                        notificationEventPublisher.publish(NotificationEventType.CHAT_MENTION, notifyPayload);
                    });
        } catch (Exception e) {
            log.warn("메시지 알림 발행 실패 roomId={}", roomId, e);
        }
    }

    private void sendInviteNotification(Long roomId, MemberInvitePayload payload) {
        try {
            String roomName = chatRoomRepo.findById(ChatRoomId.of(roomId))
                    .map(r -> r.getName().value())
                    .orElse("채팅방");
            String inviterName = memberRepo.findById(payload.inviterId())
                    .map(m -> m.getNickname() != null ? m.getNickname() : "사용자")
                    .orElse("사용자");

            notificationEventPublisher.publish(
                    NotificationEventType.CHAT_INVITED,
                    ChatMentionPayload.ofText(
                            payload.invitedId(), roomId, roomName,
                            payload.inviterId(), inviterName,
                            "채팅방에 초대되었습니다."
                    )
            );
        } catch (Exception e) {
            log.warn("초대 알림 발행 실패 roomId={} invitedId={}", roomId, payload.invitedId(), e);
        }
    }

    private Set<Long> resolveNotifiableMemberIds(Long roomId) {
        return roomMemberNotifyCache.getNotifiableMemberIds(roomId)
                .orElseGet(() -> {
                    List<ChatRoomMember> members = chatRoomMemberRepo.findActiveByRoom(ChatRoomId.of(roomId));
                    roomMemberNotifyCache.populate(roomId, members);
                    return members.stream()
                            .filter(ChatRoomMember::isNotificationEnabled)
                            .map(m -> m.getMemberId().value())
                            .collect(java.util.stream.Collectors.toSet());
                });
    }

    private ChatMentionPayload buildMentionPayload(
            Long memberId, Long roomId, String roomName,
            Long senderId, String senderName, MessageSentPayload payload
    ) {
        return switch (payload.type()) {
            case "IMAGE" -> ChatMentionPayload.ofImage(memberId, roomId, roomName, senderId, senderName);
            case "FILE" -> ChatMentionPayload.ofFile(memberId, roomId, roomName, senderId, senderName);
            default -> ChatMentionPayload.ofText(memberId, roomId, roomName, senderId, senderName, payload.content());
        };
    }
}
