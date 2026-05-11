package com.sportsify.chat.infrastructure.webSocket;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class ChatEventPublisher {
    private final SimpMessagingTemplate template;

    public void publishToRoom(long roomId, Object payload) {
        String destination = "/topic/rooms/" + roomId;
        template.convertAndSend(destination, envelope("MESSAGE", roomId, payload));
    }

    public void publishToRoomTyping(long roomId, Object payload) {
        String destination = "/topic/rooms/" + roomId + "/typing";
        template.convertAndSend(destination, envelope("TYPING", roomId, payload));
    }

    public void publishToUser(long userId, Object payload, String queue) {
        template.convertAndSendToUser(String.valueOf(userId), queue, envelope("USER", userId, payload));
    }

    private Object envelope(String kind, Long roomId, Object payload) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("kind", kind);
        if (roomId != null) m.put("roomId", roomId);
        m.put("occurredAt", java.time.OffsetDateTime.now());
        m.put("payload", payload);
        return m;
    }
}
