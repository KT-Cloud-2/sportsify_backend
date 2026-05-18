package com.sportsify.chat.infrastructure;

import com.sportsify.chat.infrastructure.webSocket.ChatEventPublisher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Map;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ChatEventPublisherTest {

    @Mock SimpMessagingTemplate template;
    @InjectMocks ChatEventPublisher publisher;

    @Test
    @DisplayName("publishToRoom은 /topic/rooms/{roomId}로 페이로드를 그대로 전송한다")
    void publishToRoom_올바른목적지_페이로드전송() {
        Object payload = Map.of("content", "안녕하세요");

        publisher.publishToRoom(1L, payload);

        verify(template).convertAndSend(eq(ChatEventPublisher.ROOM_TOPIC_PREFIX + 1), eq(payload));
    }

    @Test
    @DisplayName("publishToRoomTyping은 /topic/rooms/{roomId}/typing으로 페이로드를 그대로 전송한다")
    void publishToRoomTyping_올바른목적지_페이로드전송() {
        Object payload = Map.of("memberId", 2L, "typing", true);

        publisher.publishToRoomTyping(1L, payload);

        verify(template).convertAndSend(eq(ChatEventPublisher.ROOM_TOPIC_PREFIX + 1 + ChatEventPublisher.TYPING_SUFFIX), eq(payload));
    }

    @Test
    @DisplayName("publishToUser는 convertAndSendToUser로 해당 유저 큐에 전송한다")
    void publishToUser_convertAndSendToUser로전송() {
        Object payload = Map.of("error", "권한 없음");

        publisher.publishToUser(3L, payload, "/queue/errors");

        verify(template).convertAndSendToUser(eq("3"), eq("/queue/errors"), eq(payload));
    }
}
