package com.sportsify.chat.infrastructure.webSocket;

import com.sportsify.chat.domain.model.event.ErrorEventType;
import com.sportsify.chat.infrastructure.webSocket.dto.RoomSubscriptionRevokedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class ChatEventPublisher {

    public static final String ROOM_TOPIC_PREFIX = "/topic/rooms/";
    public static final String TYPING_SUFFIX = "/typing";
    public static final String SESSION_ERROR_QUEUE_PREFIX = "/queue/errors-user";
    public static final String SESSION_REPLAY_QUEUE_PREFIX = "/queue/replay-user";

    private final SimpMessagingTemplate template;

    public void publishToRoom(long roomId, Object payload) {
        template.convertAndSend(ROOM_TOPIC_PREFIX + roomId, payload);
    }

    public void publishToRoomTyping(long roomId, Object payload) {
        template.convertAndSend(ROOM_TOPIC_PREFIX + roomId + TYPING_SUFFIX, payload);
    }

    public void publishToUser(long userId, Object payload, String queue) {
        template.convertAndSendToUser(String.valueOf(userId), queue, payload);
    }

    public void publishErrorToSession(String sid, Object payload) {
        template.convertAndSend(SESSION_ERROR_QUEUE_PREFIX + sid, payload);
    }

    public void publishReplayToSession(String sid, Object payload) {
        template.convertAndSend(SESSION_REPLAY_QUEUE_PREFIX + sid, payload);
    }

    @EventListener
    public void onTokenExpired(TokenExpiredEvent event) {
        publishErrorToSession(event.sessionId(), Map.of("type", ErrorEventType.TOKEN_EXPIRED));
    }

    @EventListener
    public void onRoomSubscriptionRevoked(RoomSubscriptionRevokedEvent event) {
        publishErrorToSession(event.sessionId(), Map.of("type", ErrorEventType.KICKED_FROM_ROOM, "roomId", event.roomId()));
    }
}
