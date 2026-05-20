package com.sportsify.chat.application.event;


import com.sportsify.chat.domain.model.chatRoom.ChatRoomId;
import com.sportsify.chat.domain.model.event.EventEnvelope;
import com.sportsify.chat.domain.model.event.EventType;
import com.sportsify.chat.domain.model.event.chatRoom.RoomArchivedPayload;
import com.sportsify.chat.domain.model.event.chatRoom.RoomDeletePayload;
import com.sportsify.chat.domain.model.event.chatRoomMember.MemberBannedPayload;
import com.sportsify.chat.domain.model.event.message.MessagePayload;
import com.sportsify.chat.domain.model.message.Message;
import com.sportsify.chat.domain.model.message.MessageContent;
import com.sportsify.chat.domain.repository.MessageRepository;
import com.sportsify.chat.infrastructure.webSocket.ChatEventPublisher;
import com.sportsify.chat.infrastructure.webSocket.WebSocketSessionRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatEventHandler {

    private final ChatEventPublisher publisher;
    private final WebSocketSessionRegistry webSocketSessionRegistry;
    private final MessageRepository messageRepo;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendEvent(EventEnvelope<?> event) {
        Object payload = event.payload();
        if (payload instanceof MessagePayload) {
            publisher.publishToRoom(event.roomId(), event);
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
            case MemberBannedPayload p -> webSocketSessionRegistry.revokeRoomSubscriptionByMember(p.memberId(), event.roomId());
            case RoomDeletePayload _, RoomArchivedPayload _ -> webSocketSessionRegistry.revokeAllRoomSubscriptions(event.roomId());
            default -> {
            }
        }
    }

}
