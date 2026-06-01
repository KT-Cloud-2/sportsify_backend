package com.sportsify.notification.infrastructure;

import com.sportsify.common.notification.NotificationEventType;
import com.sportsify.common.notification.payload.ChatMentionPayload;
import com.sportsify.common.notification.payload.GameStartPayload;
import com.sportsify.common.notification.payload.NotificationPayload;
import com.sportsify.common.notification.payload.PaymentCompletedPayload;
import com.sportsify.common.notification.payload.TicketOpenPayload;
import com.sportsify.notification.infrastructure.publisher.RedisStreamNotificationEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RedisStreamNotificationEventPublisherTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private StreamOperations<String, Object, Object> streamOps;
    @Mock private ObjectMapper objectMapper;

    private RedisStreamNotificationEventPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new RedisStreamNotificationEventPublisher(redisTemplate, objectMapper);
    }

    private void stubStream() {
        given(redisTemplate.opsForStream()).willReturn(streamOps);
    }

    @Test
    @DisplayName("TICKET_OPEN 이벤트를 올바른 스트림 키로 발행한다")
    void publish_티켓오픈_올바른스트림키() throws Exception {
        stubStream();
        TicketOpenPayload payload = new TicketOpenPayload(1L, "KIA", "삼성", LocalDateTime.now(), LocalDateTime.now().plusHours(2));
        given(objectMapper.writeValueAsString(payload)).willReturn("{}");

        publisher.publish(NotificationEventType.TICKET_OPEN, payload);

        verify(streamOps).add(eq("ticket.opened"), any(Map.class));
    }

    @Test
    @DisplayName("PAYMENT_COMPLETED 이벤트를 올바른 스트림 키로 발행한다")
    void publish_결제완료_올바른스트림키() throws Exception {
        stubStream();
        PaymentCompletedPayload payload = new PaymentCompletedPayload(99L, 1L, 50000);
        given(objectMapper.writeValueAsString(payload)).willReturn("{}");

        publisher.publish(NotificationEventType.PAYMENT_COMPLETED, payload);

        verify(streamOps).add(eq("payment.completed"), any(Map.class));
    }

    @Test
    @DisplayName("GAME_START 이벤트를 올바른 스트림 키로 발행한다")
    void publish_경기시작_올바른스트림키() throws Exception {
        stubStream();
        GameStartPayload payload = new GameStartPayload(5L, "KIA", "삼성", LocalDateTime.now());
        given(objectMapper.writeValueAsString(payload)).willReturn("{}");

        publisher.publish(NotificationEventType.GAME_START, payload);

        verify(streamOps).add(eq("game.starting"), any(Map.class));
    }

    @Test
    @DisplayName("CHAT_MENTION 이벤트를 올바른 스트림 키로 발행한다")
    void publish_채팅알림_올바른스트림키() throws Exception {
        stubStream();
        ChatMentionPayload payload = ChatMentionPayload.ofText(7L, 42L, "LG vs 두산", 10L, "야 내일 경기 보러가자");
        given(objectMapper.writeValueAsString(payload)).willReturn("{}");

        publisher.publish(NotificationEventType.CHAT_MENTION, payload);

        verify(streamOps).add(eq("chat.mentioned"), any(Map.class));
    }

    @Test
    @DisplayName("payload가 payload 키로 스트림에 담긴다")
    void publish_payload_키확인() throws Exception {
        stubStream();
        PaymentCompletedPayload dto = new PaymentCompletedPayload(1L, 42L, 50000);
        String expectedJson = "{\"paymentId\":1,\"memberId\":42,\"amount\":50000}";
        given(objectMapper.writeValueAsString(dto)).willReturn(expectedJson);

        ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass(Map.class);
        publisher.publish(NotificationEventType.PAYMENT_COMPLETED, dto);

        verify(streamOps).add(eq("payment.completed"), captor.capture());
        assertThat(captor.getValue()).containsEntry("payload", expectedJson);
    }

    @Test
    @DisplayName("배치 발행 시 각 payload가 스트림에 각각 추가된다")
    void publish_배치_각페이로드추가() throws Exception {
        stubStream();
        PaymentCompletedPayload p1 = new PaymentCompletedPayload(1L, 10L, 10000);
        PaymentCompletedPayload p2 = new PaymentCompletedPayload(2L, 20L, 20000);
        given(objectMapper.writeValueAsString(p1)).willReturn("{\"id\":1}");
        given(objectMapper.writeValueAsString(p2)).willReturn("{\"id\":2}");

        publisher.publish(NotificationEventType.PAYMENT_COMPLETED, List.of(p1, p2));

        verify(streamOps, times(2)).add(eq("payment.completed"), any(Map.class));
    }

    @Test
    @DisplayName("직렬화 실패 시 IllegalArgumentException이 발생한다")
    void publish_직렬화실패_예외발생() throws Exception {
        NotificationPayload badPayload = new NotificationPayload() {};
        given(objectMapper.writeValueAsString(badPayload)).willThrow(new RuntimeException("직렬화 오류"));

        assertThatThrownBy(() -> publisher.publish(NotificationEventType.PAYMENT_COMPLETED, badPayload))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("알림 payload 직렬화 실패");
    }

    @Test
    @DisplayName("빈 리스트 발행 시 스트림에 아무것도 추가하지 않는다")
    void publish_빈리스트_추가없음() {
        publisher.publish(NotificationEventType.PAYMENT_COMPLETED, List.of());

        verify(streamOps, never()).add(any(), any(Map.class));
    }
}
