package com.sportsify.chat.infrastructure;

import com.sportsify.chat.infrastructure.webSocket.ChatEventPublisher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ChatEventPublisherTest {

    @Mock SimpMessagingTemplate template;
    @InjectMocks ChatEventPublisher publisher;

    @Test
    @DisplayName("publishToRoom은 /topic/rooms/{roomId}로 MESSAGE 봉투를 전송한다")
    void publishToRoom_올바른목적지_MESSAGE봉투전송() {
        Object payload = Map.of("content", "안녕하세요");

        publisher.publishToRoom(1L, payload);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(template).convertAndSend(eq(ChatEventPublisher.ROOM_TOPIC_PREFIX + 1), captor.capture());

        Map<?, ?> envelope = (Map<?, ?>) captor.getValue();
        assertThat(envelope.get("kind")).isEqualTo("MESSAGE");
        assertThat(envelope.get("roomId")).isEqualTo(1L);
        assertThat(envelope.get("payload")).isEqualTo(payload);
        assertThat(envelope.get("occurredAt")).isNotNull();
    }

    @Test
    @DisplayName("publishToRoomTyping은 /topic/rooms/{roomId}/typing으로 TYPING 봉투를 전송한다")
    void publishToRoomTyping_올바른목적지_TYPING봉투전송() {
        Object payload = Map.of("memberId", 2L, "typing", true);

        publisher.publishToRoomTyping(1L, payload);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(template).convertAndSend(eq(ChatEventPublisher.ROOM_TOPIC_PREFIX + 1 + ChatEventPublisher.TYPING_SUFFIX), captor.capture());

        Map<?, ?> envelope = (Map<?, ?>) captor.getValue();
        assertThat(envelope.get("kind")).isEqualTo("TYPING");
        assertThat(envelope.get("roomId")).isEqualTo(1L);
        assertThat(envelope.get("payload")).isEqualTo(payload);
        assertThat(envelope.get("occurredAt")).isNotNull();
    }

    @Test
    @DisplayName("publishToUser는 convertAndSendToUser로 해당 유저 큐에 전송한다")
    void publishToUser_convertAndSendToUser로전송() {
        Object payload = Map.of("error", "권한 없음");

        publisher.publishToUser(3L, payload, "/queue/errors");

        verify(template).convertAndSendToUser(eq("3"), eq("/queue/errors"), eq(payload));
    }
}
