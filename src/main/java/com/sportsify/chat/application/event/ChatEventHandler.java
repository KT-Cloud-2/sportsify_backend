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
    @Async("send event broadcastExecutor")
    public void sendEvent(EventEnvelope<?> event) {
        publisher.publishToRoom(
                event.roomId(), event
        );
    }


    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async("ban event broadExecutor")
    public void handleMemberBanned(EventEnvelope<MemberBannedPayload> event) {
        webSocketSessionRegistry.forceDisconnectByMemberInRoom(event.payload().memberId(), event.roomId(), "Banned");
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async("room delete broadExecutor")
    public void handleRoomDelete(EventEnvelope<RoomDeletePayload> event) {
        webSocketSessionRegistry.forceDisconnectAllInRoom(event.roomId(), "Room deleted");
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async("room archived broadExecutor")
    public void handleRoomArchived(EventEnvelope<RoomArchivedPayload> event) {
        webSocketSessionRegistry.forceDisconnectAllInRoom(event.roomId(), "Room archived");
    }


}
