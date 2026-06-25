package com.sportsify.chat.infrastructure.webSocket;

import com.sportsify.chat.domain.model.event.ErrorEventType;
import com.sportsify.chat.infrastructure.webSocket.dto.RoomSubscriptionRevokedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Slf4j
@RequiredArgsConstructor
public class ChatEventPublisher {

    public static final String ROOM_TOPIC_PREFIX = "/topic/rooms/";

    private final WebSocketMetrics webSocketMetrics;
    private final DirectRoomMessageSender directRoomMessageSender;

    public void publishToRoom(long roomId, Object payload) {
        // SimpleBroker 경유 (DefaultSubscriptionRegistry ReadWriteLock 경쟁 발생)
//        template.convertAndSend(ROOM_TOPIC_PREFIX + roomId, payload);
        directRoomMessageSender.sendToRoom(roomId, payload);
        webSocketMetrics.recordMessageOut();
    }

    public void publishToRoomTyping(long roomId, Object payload) {
        directRoomMessageSender.sendToRoom(roomId, payload);
    }

    public void publishToUser(long userId, Object payload, String queue) {
        directRoomMessageSender.sendToUser(userId, queue, payload);
    }

    @EventListener
    public void onTokenExpired(TokenExpiredEvent event) {
        publishToUser(event.memberId(), Map.of("type", ErrorEventType.TOKEN_EXPIRED), "/user/queue/session-errors");
    }

    @EventListener
    public void onRoomSubscriptionRevoked(RoomSubscriptionRevokedEvent event) {
        log.info(
                "[BAN SEND] memberId={}, sessionId={}, destination={}",
                event.memberId(),
                event.sessionId(),
                event.roomId()
        );
        publishToUser(event.memberId(), Map.of("type", ErrorEventType.KICKED_FROM_ROOM, "roomId", event.roomId()), "/user/queue/session-errors");
    }
}
