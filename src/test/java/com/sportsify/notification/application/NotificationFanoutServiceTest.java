package com.sportsify.notification.application;

import com.sportsify.common.notification.NotificationEventType;
import com.sportsify.notification.application.service.NotificationDispatcher;
import com.sportsify.notification.application.service.NotificationFanoutService;
import com.sportsify.notification.domain.model.NotificationEvent;
import com.sportsify.notification.domain.repository.NotificationSettingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.SliceImpl;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationFanoutServiceTest {

    @Mock private NotificationSettingRepository settingRepository;
    @Mock private NotificationDispatcher dispatcher;

    private NotificationFanoutService fanoutService;

    @BeforeEach
    void setUp() {
        fanoutService = new NotificationFanoutService(settingRepository, dispatcher);
    }

    @Test
    @DisplayName("TICKET_OPEN은 ticketOpenAlert ON 회원 전체에게 fan-out한다")
    void fanout_티켓오픈_전체발송() {
        NotificationEvent event = NotificationEvent.withId(1L, NotificationEventType.TICKET_OPEN, "{}");
        given(settingRepository.findMemberIdsByTicketOpenAlertTrue(any()))
                .willReturn(new SliceImpl<>(List.of(1L, 2L)));

        fanoutService.fanout(event, NotificationEventType.TICKET_OPEN, "{}");

        verify(dispatcher).dispatchToMember(eq(event), eq(1L), any());
        verify(dispatcher).dispatchToMember(eq(event), eq(2L), any());
    }

    @Test
    @DisplayName("GAME_START는 gameStartAlert ON 회원 전체에게 fan-out한다")
    void fanout_경기시작_전체발송() {
        NotificationEvent event = NotificationEvent.withId(2L, NotificationEventType.GAME_START, "{}");
        given(settingRepository.findMemberIdsByGameStartAlertTrue(any()))
                .willReturn(new SliceImpl<>(List.of(10L)));

        fanoutService.fanout(event, NotificationEventType.GAME_START, "{}");

        verify(dispatcher).dispatchToMember(eq(event), eq(10L), any());
    }

    @Test
    @DisplayName("PAYMENT_COMPLETED는 paymentAlert ON 회원 전체에게 fan-out한다")
    void fanout_결제완료_전체발송() {
        NotificationEvent event = NotificationEvent.withId(3L, NotificationEventType.PAYMENT_COMPLETED, "{}");
        given(settingRepository.findMemberIdsByPaymentAlertTrue(any()))
                .willReturn(new SliceImpl<>(List.of(5L)));

        fanoutService.fanout(event, NotificationEventType.PAYMENT_COMPLETED, "{}");

        verify(dispatcher).dispatchToMember(eq(event), eq(5L), any());
    }

    @Test
    @DisplayName("CHAT_MENTION은 payload의 memberId 단 한 명에게만 발송한다")
    void fanout_채팅알림_단건발송() {
        NotificationEvent event = NotificationEvent.withId(4L, NotificationEventType.CHAT_MENTION, "{}");
        String payload = "{\"roomId\":7,\"memberId\":42}";

        fanoutService.fanout(event, NotificationEventType.CHAT_MENTION, payload);

        verify(dispatcher).dispatchToMember(eq(event), eq(42L), eq(payload));
        verify(settingRepository, never()).findMemberIdsByChatMentionAlertTrue(any());
    }

    @Test
    @DisplayName("CHAT_MENTION payload에 memberId 없으면 발송하지 않고 실패 반환한다")
    void fanout_채팅알림_memberId없음_실패() {
        NotificationEvent event = NotificationEvent.withId(5L, NotificationEventType.CHAT_MENTION, "{}");
        String invalidPayload = "{\"roomId\":7}";

        boolean result = fanoutService.fanout(event, NotificationEventType.CHAT_MENTION, invalidPayload);

        verify(dispatcher, never()).dispatchToMember(any(), any(), any());
        assert result;
    }

    @Test
    @DisplayName("대상 회원이 없으면 dispatcher를 호출하지 않는다")
    void fanout_대상없음_dispatcher미호출() {
        NotificationEvent event = NotificationEvent.withId(6L, NotificationEventType.TICKET_OPEN, "{}");
        given(settingRepository.findMemberIdsByTicketOpenAlertTrue(any()))
                .willReturn(new SliceImpl<>(List.of()));

        fanoutService.fanout(event, NotificationEventType.TICKET_OPEN, "{}");

        verify(dispatcher, never()).dispatchToMember(any(), any(), any());
    }
}
