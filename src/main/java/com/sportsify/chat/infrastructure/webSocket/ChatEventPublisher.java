package com.sportsify.chat.infrastructure.webSocket;

import com.sportsify.chat.domain.model.event.ErrorEventType;
import com.sportsify.chat.infrastructure.webSocket.dto.RoomSubscriptionRevokedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

import java.security.Principal;
import java.util.Map;

@Component
@Slf4j
@RequiredArgsConstructor
public class ChatEventPublisher {

    public static final String ROOM_TOPIC_PREFIX = "/topic/rooms/";
    public static final String TYPING_SUFFIX = "/typing";

    private final SimpMessagingTemplate template;
    private final WebSocketMetrics webSocketMetrics;

    public void publishToRoom(long roomId, Object payload) {
        template.convertAndSend(ROOM_TOPIC_PREFIX + roomId, payload);
        webSocketMetrics.recordMessageOut();
    }

    public void publishToRoomTyping(long roomId, Object payload) {
        template.convertAndSend(ROOM_TOPIC_PREFIX + roomId + TYPING_SUFFIX, payload);
    }

    public void publishToUser(long userId, Object payload, String queue) {
        template.convertAndSendToUser(String.valueOf(userId), queue, payload);
    }

    @EventListener
    public void onTokenExpired(TokenExpiredEvent event) {
        publishToUser(event.memberId(), Map.of("type", ErrorEventType.TOKEN_EXPIRED), "/queue/session-errors");
    }

    @EventListener
    public void onRoomSubscriptionRevoked(RoomSubscriptionRevokedEvent event) {
        log.info(
                "[BAN SEND] memberId={}, sessionId={}, destination={}",
                event.memberId(),
                event.sessionId(),
                event.roomId()
        );
        publishToUser(event.memberId(), Map.of("type", ErrorEventType.KICKED_FROM_ROOM, "roomId", event.roomId()), "/queue/session-errors");
    }
}
