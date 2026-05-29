package com.sportsify.notification.application;

import com.sportsify.common.notification.NotificationEventType;
import com.sportsify.notification.application.service.EventStatusService;
import com.sportsify.notification.application.service.FanoutService;
import com.sportsify.notification.application.service.ScheduledEventClaimService;
import com.sportsify.notification.application.service.ScheduledNotificationProcessor;
import com.sportsify.notification.domain.model.NotificationEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScheduledNotificationProcessorTest {

    @Mock private ScheduledEventClaimService claimService;
    @Mock private FanoutService fanoutService;
    @Mock private EventStatusService eventStatusService;

    private ScheduledNotificationProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new ScheduledNotificationProcessor(claimService, fanoutService, eventStatusService);
    }

    @Test
    @DisplayName("만기 이벤트가 없으면 fanout을 호출하지 않는다")
    void processDue_만기이벤트없음_fanout미호출() {
        given(claimService.claimDueEvents()).willReturn(List.of());

        processor.processDue();

        verify(fanoutService, never()).fanout(any(), any(), any());
    }

    @Test
    @DisplayName("fanout 성공 시 PUBLISHED로 마킹한다")
    void processDue_fanout성공_PUBLISHED마킹() {
        NotificationEvent event = NotificationEvent.withId(1L, NotificationEventType.TICKET_OPEN, "{}");
        given(claimService.claimDueEvents()).willReturn(List.of(event));
        given(fanoutService.fanout(any(), any(), any())).willReturn(false);

        processor.processDue();

        verify(fanoutService).fanout(eq(event), eq(NotificationEventType.TICKET_OPEN), eq("{}"));
        verify(eventStatusService).markEventStatus(1L, false);
    }

    @Test
    @DisplayName("fanout 실패 시 FAILED로 마킹한다")
    void processDue_fanout실패_FAILED마킹() {
        NotificationEvent event = NotificationEvent.withId(2L, NotificationEventType.PAYMENT_COMPLETED, "{}");
        given(claimService.claimDueEvents()).willReturn(List.of(event));
        given(fanoutService.fanout(any(), any(), any())).willReturn(true);

        processor.processDue();

        verify(eventStatusService).markEventStatus(2L, true);
    }

    @Test
    @DisplayName("예외 발생 시 FAILED로 마킹하고 나머지 이벤트는 계속 처리된다")
    void processDue_예외발생_FAILED마킹_나머지계속처리() {
        NotificationEvent event1 = NotificationEvent.withId(5L, NotificationEventType.TICKET_OPEN, "{}");
        NotificationEvent event2 = NotificationEvent.withId(6L, NotificationEventType.GAME_START, "{}");
        given(claimService.claimDueEvents()).willReturn(List.of(event1, event2));
        willThrow(new RuntimeException("fanout 실패")).given(fanoutService).fanout(eq(event1), any(), any());
        given(fanoutService.fanout(eq(event2), any(), any())).willReturn(false);

        processor.processDue();

        verify(eventStatusService).markEventStatus(5L, true);
        verify(fanoutService).fanout(eq(event2), eq(NotificationEventType.GAME_START), eq("{}"));
    }
}
