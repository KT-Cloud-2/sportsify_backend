package com.sportsify.notification.application;

import com.sportsify.common.notification.NotificationEventType;
import com.sportsify.notification.application.service.NotificationEventProcessor;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ScheduledNotificationProcessorTest {

    @Mock private ScheduledEventClaimService claimService;
    @Mock private NotificationEventProcessor eventProcessor;

    private ScheduledNotificationProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new ScheduledNotificationProcessor(claimService, eventProcessor);
    }

    @Test
    @DisplayName("만기 이벤트가 없으면 fanout을 호출하지 않는다")
    void processDue_만기이벤트없음_fanout미호출() {
        given(claimService.claimDueEvents()).willReturn(List.of());

        processor.processDue();

        verify(eventProcessor, never()).fanout(any(), any(), any());
    }

    @Test
    @DisplayName("만기 이벤트를 조회해 eventProcessor.fanout에 위임한다")
    void processDue_만기이벤트존재_fanout위임() {
        NotificationEvent event = NotificationEvent.withId(1L, NotificationEventType.TICKET_OPEN, "{}");
        given(claimService.claimDueEvents()).willReturn(List.of(event));

        processor.processDue();

        verify(eventProcessor).fanout(eq(event), eq(NotificationEventType.TICKET_OPEN), eq("{}"));
    }

    @Test
    @DisplayName("하나의 이벤트 fanout 실패 시 나머지 이벤트는 계속 처리된다")
    void processDue_일부실패_나머지계속처리() {
        NotificationEvent event1 = NotificationEvent.withId(1L, NotificationEventType.TICKET_OPEN, "{}");
        NotificationEvent event2 = NotificationEvent.withId(2L, NotificationEventType.GAME_START, "{}");
        given(claimService.claimDueEvents()).willReturn(List.of(event1, event2));
        willThrow(new RuntimeException("fanout 실패")).given(eventProcessor)
                .fanout(eq(event1), any(), any());

        processor.processDue();

        verify(eventProcessor, times(2)).fanout(any(), any(), any());
        verify(eventProcessor).fanout(eq(event2), eq(NotificationEventType.GAME_START), eq("{}"));
    }
}
