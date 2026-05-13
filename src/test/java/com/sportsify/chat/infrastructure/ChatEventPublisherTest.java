package com.sportsify.chat.infrastructure;

import com.sportsify.chat.infrastructure.webSocket.ChatEventPublisher;
import com.sportsify.chat.infrastructure.webSocket.WebSocketSessionRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class ChatEventPublisherTest {

    @Mock SimpMessagingTemplate template;
    @Mock WebSocketSessionRegistry sessionRegistry;
    @InjectMocks ChatEventPublisher publisher;

    @Test
    @DisplayName("publishToRoom은 /topic/rooms/{roomId}로 MESSAGE 봉투를 전송한다")
    void publishToRoom_올바른목적지_MESSAGE봉투전송() {
        Object payload = Map.of("content", "안녕하세요");

        publisher.publishToRoom(1L, payload);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(template).convertAndSend(eq("/topic/rooms/1"), captor.capture());

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
        verify(template).convertAndSend(eq("/topic/rooms/1/typing"), captor.capture());

        Map<?, ?> envelope = (Map<?, ?>) captor.getValue();
        assertThat(envelope.get("kind")).isEqualTo("TYPING");
        assertThat(envelope.get("roomId")).isEqualTo(1L);
        assertThat(envelope.get("payload")).isEqualTo(payload);
        assertThat(envelope.get("occurredAt")).isNotNull();
    }

    @Test
    @DisplayName("publishToUser는 세션별 큐로 USER 봉투를 전송한다")
    void publishToUser_활성세션_USER봉투전송() {
        Object payload = Map.of("error", "권한 없음");
        given(sessionRegistry.getSessionIds(3L)).willReturn(Set.of("sid-1"));

        publisher.publishToUser(3L, payload, "/queue/errors");

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(template).convertAndSend(eq("/queue/errors-usersid-1"), captor.capture());

        Map<?, ?> envelope = (Map<?, ?>) captor.getValue();
        assertThat(envelope.get("kind")).isEqualTo("USER");
        assertThat(envelope.get("payload")).isEqualTo(payload);
        assertThat(envelope.get("occurredAt")).isNotNull();
    }

    @Test
    @DisplayName("publishToUser는 활성 세션이 없으면 메시지를 전송하지 않는다")
    void publishToUser_세션없음_전송안함() {
        given(sessionRegistry.getSessionIds(3L)).willReturn(Set.of());

        publisher.publishToUser(3L, Map.of("error", "권한 없음"), "/queue/errors");

        verifyNoMoreInteractions(template);
    }
}
