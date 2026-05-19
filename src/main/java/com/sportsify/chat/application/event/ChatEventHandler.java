package com.sportsify.chat.application.event;


import com.sportsify.chat.domain.model.event.EventEnvelope;
import com.sportsify.chat.domain.model.event.chatRoom.RoomArchivedPayload;
import com.sportsify.chat.domain.model.event.chatRoom.RoomDeletePayload;
import com.sportsify.chat.domain.model.event.chatRoomMember.MemberBannedPayload;
import com.sportsify.chat.infrastructure.webSocket.ChatEventPublisher;
import com.sportsify.chat.infrastructure.webSocket.WebSocketSessionRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class ChatEventHandler {
    
    private final ChatEventPublisher publisher;
    private final WebSocketSessionRegistry webSocketSessionRegistry;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    public void sendEvent(EventEnvelope<?> event) {
        publisher.publishToRoom(
                event.roomId(), event
        );

        switch (event.payload()) {
            case MemberBannedPayload p -> webSocketSessionRegistry.revokeRoomSubscriptionByMember(p.memberId(), event.roomId());
            case RoomDeletePayload _, RoomArchivedPayload _ -> webSocketSessionRegistry.revokeAllRoomSubscriptions(event.roomId());
            default -> {
            }
        }
    }

}
