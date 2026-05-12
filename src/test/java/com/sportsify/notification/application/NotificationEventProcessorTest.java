package com.sportsify.notification.application;

import com.sportsify.common.notification.NotificationEventType;
import com.sportsify.notification.application.service.NotificationEventProcessor;
import com.sportsify.notification.application.service.NotificationEventStatusService;
import com.sportsify.notification.application.service.NotificationFanoutService;
import com.sportsify.notification.domain.model.NotificationEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationEventProcessorTest {

    @Mock private NotificationEventStatusService statusService;
    @Mock private NotificationFanoutService fanoutService;

    private NotificationEventProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new NotificationEventProcessor(statusService, fanoutService);
    }

    @Test
    @DisplayName("이벤트를 저장하고 fanout을 실행한다")
    void process_이벤트저장_fanout실행() {
        NotificationEvent event = NotificationEvent.withId(1L, NotificationEventType.PAYMENT_COMPLETED, "{}");
        given(statusService.saveEvent(any(), any())).willReturn(event);
        given(fanoutService.fanout(any(), any(), any())).willReturn(false);

        processor.process(NotificationEventType.PAYMENT_COMPLETED, "{}");

        verify(statusService).saveEvent(NotificationEventType.PAYMENT_COMPLETED, "{}");
        verify(fanoutService).fanout(event, NotificationEventType.PAYMENT_COMPLETED, "{}");
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
}
