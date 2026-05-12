package com.sportsify.notification.application;

import com.sportsify.common.notification.NotificationEventType;
import com.sportsify.notification.application.service.NotificationEventProcessor;
import com.sportsify.notification.application.service.NotificationFanoutService;
import com.sportsify.notification.domain.model.NotificationEvent;
import com.sportsify.notification.domain.repository.NotificationEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationEventProcessorTest {

    @Mock private NotificationEventRepository eventRepository;
    @Mock private NotificationFanoutService fanoutService;

    private NotificationEventProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new NotificationEventProcessor(eventRepository, fanoutService);
    }

    @Test
    @DisplayName("이벤트를 저장하고 fanout을 실행한다")
    void process_이벤트저장_fanout실행() {
        NotificationEvent event = NotificationEvent.withId(1L, NotificationEventType.PAYMENT_COMPLETED, "{}");
        given(eventRepository.save(any())).willReturn(event);
        given(fanoutService.fanout(any(), any(), any())).willReturn(false);
        given(eventRepository.findById(1L)).willReturn(Optional.of(event));

        processor.process(NotificationEventType.PAYMENT_COMPLETED, "{}");

        verify(eventRepository).save(any());
        verify(fanoutService).fanout(event, NotificationEventType.PAYMENT_COMPLETED, "{}");
    }

    @Test
    @DisplayName("fanout 전부 성공 시 이벤트 상태가 PUBLISHED로 마킹된다")
    void process_fanout성공_PUBLISHED마킹() {
        NotificationEvent event = NotificationEvent.withId(1L, NotificationEventType.TICKET_OPEN, "{}");
        given(eventRepository.save(any())).willReturn(event);
        given(fanoutService.fanout(any(), any(), any())).willReturn(false);
        given(eventRepository.findById(1L)).willReturn(Optional.of(event));

        processor.process(NotificationEventType.TICKET_OPEN, "{}");

        verify(eventRepository).findById(1L);
    }

    @Test
    @DisplayName("fanout 일부 실패 시 이벤트 상태가 FAILED로 마킹된다")
    void process_fanout실패_FAILED마킹() {
        NotificationEvent event = NotificationEvent.withId(1L, NotificationEventType.PAYMENT_COMPLETED, "{}");
        given(eventRepository.save(any())).willReturn(event);
        given(fanoutService.fanout(any(), any(), any())).willReturn(true);
        given(eventRepository.findById(1L)).willReturn(Optional.of(event));

        processor.process(NotificationEventType.PAYMENT_COMPLETED, "{}");

        verify(eventRepository).findById(1L);
    }
}
