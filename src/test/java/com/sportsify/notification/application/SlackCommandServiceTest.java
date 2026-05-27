package com.sportsify.notification.application;

import com.sportsify.common.notification.NotificationEventType;
import com.sportsify.notification.application.port.NotificationStreamQueryPort;
import com.sportsify.notification.application.service.SlackCommandService;
import com.sportsify.notification.infrastructure.publisher.RedisStreamNotificationEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SlackCommandServiceTest {

    @Mock private NotificationStreamQueryPort streamQueryPort;
    @Mock private RedisStreamNotificationEventPublisher streamPublisher;

    private SlackCommandService service;

    @BeforeEach
    void setUp() {
        service = new SlackCommandService(streamQueryPort, streamPublisher);
    }

    @Test
    @DisplayName("알 수 없는 명령어이면 안내 메시지를 반환한다")
    void handle_알수없는명령어_안내메시지반환() {
        String result = service.handle("unknown command");

        assertThat(result).contains("알 수 없는 명령어");
        verify(streamPublisher, never()).republish(any(), any());
    }

    @Test
    @DisplayName("streamKey 파라미터가 없으면 사용법을 반환한다")
    void handle_streamKey없음_사용법반환() {
        String result = service.handle("notify event id=1-0");

        assertThat(result).contains("사용법");
        verify(streamPublisher, never()).republish(any(), any());
    }

    @Test
    @DisplayName("id 파라미터가 없으면 사용법을 반환한다")
    void handle_id없음_사용법반환() {
        String result = service.handle("notify event streamKey=payment.completed");

        assertThat(result).contains("사용법");
        verify(streamPublisher, never()).republish(any(), any());
    }

    @Test
    @DisplayName("유효하지 않은 streamKey이면 오류 메시지를 반환한다")
    void handle_유효하지않은streamKey_오류반환() {
        String result = service.handle("notify event streamKey=unknown.stream id=1-0");

        assertThat(result).contains("유효하지 않은 streamKey");
        verify(streamPublisher, never()).republish(any(), any());
    }

    @Test
    @DisplayName("Redis Stream에 메시지가 없으면 찾을 수 없다는 메시지를 반환한다")
    void handle_메시지없음_찾을수없음반환() {
        given(streamQueryPort.findPayload(eq(NotificationEventType.PAYMENT_COMPLETED), eq("1-0")))
                .willReturn(Optional.empty());

        String result = service.handle("notify event streamKey=payment.completed id=1-0");

        assertThat(result).contains("찾을 수 없습니다");
        verify(streamPublisher, never()).republish(any(), any());
    }

    @Test
    @DisplayName("유효한 명령어이면 해당 메시지를 재발행하고 완료 메시지를 반환한다")
    void handle_정상명령어_재발행완료() {
        given(streamQueryPort.findPayload(eq(NotificationEventType.PAYMENT_COMPLETED), eq("1-0")))
                .willReturn(Optional.of("{\"orderId\":1}"));

        String result = service.handle("notify event streamKey=payment.completed id=1-0");

        assertThat(result).contains("재발송 완료");
        verify(streamPublisher).republish(eq(NotificationEventType.PAYMENT_COMPLETED), eq("{\"orderId\":1}"));
    }
}
