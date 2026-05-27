package com.sportsify.notification.application;

import com.sportsify.common.notification.NotificationEventType;
import com.sportsify.notification.application.service.EventProcessor;
import com.sportsify.notification.application.service.EventStatusService;
import com.sportsify.notification.application.service.FanoutService;
import com.sportsify.notification.domain.model.NotificationEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EventProcessorTest {

    @Mock private EventStatusService statusService;
    @Mock private FanoutService fanoutService;

    private EventProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new EventProcessor(statusService, fanoutService);
    }

    @Test
    @DisplayName("이벤트 저장 시 statusService.saveEvent를 호출한다")
    void process_이벤트저장_fanout실행() {
        NotificationEvent event = NotificationEvent.withId(1L, NotificationEventType.PAYMENT_COMPLETED, "{}");
        given(statusService.saveEvent(any(), any())).willReturn(event);
        given(fanoutService.fanout(any(), any(), any())).willReturn(false);

        processor.process(NotificationEventType.PAYMENT_COMPLETED, "{}");

        verify(statusService).saveEvent(NotificationEventType.PAYMENT_COMPLETED, "{}");
    }

    @Test
    @DisplayName("fanout 전부 성공 시 PUBLISHED로 마킹된다")
    void process_fanout성공_PUBLISHED마킹() {
        NotificationEvent event = NotificationEvent.withId(1L, NotificationEventType.TICKET_OPEN, "{}");
        given(statusService.saveEvent(any(), any())).willReturn(event);
        given(fanoutService.fanout(any(), any(), any())).willReturn(false);

        processor.process(NotificationEventType.TICKET_OPEN, "{}");

        verify(statusService).markEventStatus(1L, false);
    }

    @Test
    @DisplayName("fanout 일부 실패 시 FAILED로 마킹된다")
    void process_fanout실패_FAILED마킹() {
        NotificationEvent event = NotificationEvent.withId(1L, NotificationEventType.PAYMENT_COMPLETED, "{}");
        given(statusService.saveEvent(any(), any())).willReturn(event);
        given(fanoutService.fanout(any(), any(), any())).willReturn(true);

        processor.process(NotificationEventType.PAYMENT_COMPLETED, "{}");

        verify(statusService).markEventStatus(1L, true);
    }

    @Test
    @DisplayName("fanout에서 예외 발생해도 이벤트 상태를 FAILED로 마킹한다")
    void process_fanout예외_FAILED마킹() {
        NotificationEvent event = NotificationEvent.withId(1L, NotificationEventType.TICKET_OPEN, "{}");
        given(statusService.saveEvent(any(), any())).willReturn(event);
        willThrow(new RuntimeException("예기치 못한 예외")).given(fanoutService).fanout(any(), any(), any());

        processor.process(NotificationEventType.TICKET_OPEN, "{}");

        ArgumentCaptor<Boolean> captor = ArgumentCaptor.forClass(Boolean.class);
        verify(statusService).markEventStatus(eq(1L), captor.capture());
        assertThat(captor.getValue()).isTrue();
    }

    @Test
    @DisplayName("fanout 직접 호출 시 발송 성공하면 PUBLISHED로 마킹된다")
    void fanout_성공_PUBLISHED마킹() {
        NotificationEvent event = NotificationEvent.withId(2L, NotificationEventType.PAYMENT_COMPLETED, "{}");
        given(fanoutService.fanout(any(), any(), any())).willReturn(false);

        processor.fanout(event, NotificationEventType.PAYMENT_COMPLETED, "{}");

        verify(statusService).markEventStatus(2L, false);
    }

    @Test
    @DisplayName("fanout 직접 호출 시 발송 실패하면 FAILED로 마킹된다")
    void fanout_실패_FAILED마킹() {
        NotificationEvent event = NotificationEvent.withId(3L, NotificationEventType.PAYMENT_COMPLETED, "{}");
        given(fanoutService.fanout(any(), any(), any())).willReturn(true);

        processor.fanout(event, NotificationEventType.PAYMENT_COMPLETED, "{}");

        verify(statusService).markEventStatus(3L, true);
    }

    @Test
    @DisplayName("fanout 직접 호출 중 예외 발생 시 FAILED로 마킹된다")
    void fanout_예외_FAILED마킹() {
        NotificationEvent event = NotificationEvent.withId(4L, NotificationEventType.PAYMENT_COMPLETED, "{}");
        willThrow(new RuntimeException("예외")).given(fanoutService).fanout(any(), any(), any());

        processor.fanout(event, NotificationEventType.PAYMENT_COMPLETED, "{}");

        ArgumentCaptor<Boolean> captor = ArgumentCaptor.forClass(Boolean.class);
        verify(statusService).markEventStatus(eq(4L), captor.capture());
        assertThat(captor.getValue()).isTrue();
    }
}
