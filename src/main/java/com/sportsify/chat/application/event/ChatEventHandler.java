package com.sportsify.chat.application.event;


import com.sportsify.chat.domain.model.chatRoom.ChatRoomId;
import com.sportsify.chat.domain.model.chatRoom.ChatRoomType;
import com.sportsify.chat.domain.model.chatRoomMember.ChatRoomMember;
import com.sportsify.chat.domain.model.event.EventEnvelope;
import com.sportsify.chat.domain.model.event.EventType;
import com.sportsify.chat.domain.model.event.chatRoom.RoomArchivedPayload;
import com.sportsify.chat.domain.model.event.chatRoom.RoomDeletePayload;
import com.sportsify.chat.domain.model.event.chatRoomMember.*;
import com.sportsify.chat.domain.model.event.message.MessagePayload;
import com.sportsify.chat.domain.model.event.message.MessageSentPayload;
import com.sportsify.chat.domain.model.message.Message;
import com.sportsify.chat.domain.model.message.MessageContent;
import com.sportsify.chat.domain.repository.ChatRoomMemberRepository;
import com.sportsify.chat.domain.repository.MessageRepository;
import com.sportsify.chat.domain.repository.RoomMemberNotifyCache;
import com.sportsify.chat.infrastructure.webSocket.ChatEventPublisher;
import com.sportsify.chat.infrastructure.webSocket.WebSocketSessionRegistry;
import com.sportsify.common.notification.NotificationEventPublisher;
import com.sportsify.common.notification.NotificationEventType;
import com.sportsify.common.notification.payload.ChatMentionPayload;
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
import java.util.stream.Collectors;

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

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendEvent(EventEnvelope<?> event) {
        Object payload = event.payload();
        boolean isDirect = event.roomType() == ChatRoomType.DIRECT;

        if (payload instanceof MessagePayload) {
            publisher.publishToRoom(event.roomId(), event);
            if (payload instanceof MessageSentPayload sentPayload && isDirect) {
                sendMessageNotification(event.roomId(), event.roomName(), sentPayload);
            }
            return;
        }

        String userId = switch (payload) {
            case MemberJoinPayload p -> String.valueOf(p.memberId());
            case MemberLeftPayload p -> String.valueOf(p.memberId());
            case MemberBannedPayload p -> String.valueOf(p.memberId());
            case MemberRejectedPayload p -> String.valueOf(p.memberId());
            case MemberInvitePayload p -> String.valueOf(p.invitedId());
            default -> null;
        };
        EventEnvelope<?> toPublish = event;
        String alertText = EventType.valueOf(event.event()).formatAlert(userId);
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
            case MemberJoinPayload p -> {
                if (isDirect) roomMemberNotifyCache.put(event.roomId(), p.memberId(), true);
            }
            case MemberLeftPayload p -> {
                if (isDirect) roomMemberNotifyCache.remove(event.roomId(), p.memberId());
            }
            case MemberInvitePayload p -> {
                sendInviteNotification(event.roomId(), event.roomName(), p);
            }
            default -> {
            }
        }
    }

    private void sendMessageNotification(Long roomId, String roomName, MessageSentPayload payload) {
        try {
            Set<Long> notifiableIds = resolveNotifiableMemberIds(roomId);
            if (notifiableIds.isEmpty()) return;

            String resolvedRoomName = roomName != null ? roomName : "채팅방";
            Set<Long> activeSubscribers = webSocketSessionRegistry.getSubscribedMemberIds(roomId);

            notifiableIds.stream()
                    .filter(memberId -> !memberId.equals(payload.senderId()))
                    .filter(memberId -> !activeSubscribers.contains(memberId))
                    .forEach(memberId -> {
                        ChatMentionPayload notifyPayload = buildMentionPayload(
                                memberId, roomId, resolvedRoomName, payload.senderId(), payload
                        );
                        notificationEventPublisher.publish(NotificationEventType.CHAT_MENTION, notifyPayload);
                    });
        } catch (Exception e) {
            log.warn("메시지 알림 발행 실패 roomId={}", roomId, e);
        }
    }

    private void sendInviteNotification(Long roomId, String roomName, MemberInvitePayload payload) {
        try {
            String resolvedRoomName = roomName != null ? roomName : "채팅방";
            notificationEventPublisher.publish(
                    NotificationEventType.CHAT_INVITED,
                    ChatMentionPayload.ofText(
                            payload.invitedId(), roomId, resolvedRoomName,
                            payload.inviterId(),
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
                            .collect(Collectors.toSet());
                });
    }

    private ChatMentionPayload buildMentionPayload(
            Long memberId, Long roomId, String roomName,
            Long senderId, MessageSentPayload payload
    ) {
        return switch (payload.type()) {
            case "IMAGE" -> ChatMentionPayload.ofImage(memberId, roomId, roomName, senderId);
            case "FILE" -> ChatMentionPayload.ofFile(memberId, roomId, roomName, senderId);
            default -> ChatMentionPayload.ofText(memberId, roomId, roomName, senderId, payload.content());
        };
    }
}
