package com.sportsify.notification.application;

import com.sportsify.common.notification.NotificationEventType;
import com.sportsify.notification.application.service.ChunkService;
import com.sportsify.notification.application.service.FanoutService;
import com.sportsify.notification.application.service.PayloadParser;
import com.sportsify.notification.domain.model.NotificationEvent;
import com.sportsify.notification.domain.repository.NotificationSettingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.SliceImpl;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FanoutServiceTest {

    @Mock private NotificationSettingRepository settingRepository;
    @Mock private ChunkService chunkService;
    @Mock private PayloadParser payloadParser;

    private FanoutService fanoutService;

    @BeforeEach
    void setUp() {
        fanoutService = new FanoutService(settingRepository, chunkService, payloadParser);
    }

    @Test
    @DisplayName("TICKET_OPEN은 ticketOpenAlert ON 회원 전체에게 fan-out한다")
    void fanout_티켓오픈_전체발송() {
        NotificationEvent event = NotificationEvent.withId(1L, NotificationEventType.TICKET_OPEN, "{}");
        given(settingRepository.findMemberIdsByTicketOpenAlertTrue(any()))
                .willReturn(new SliceImpl<>(List.of(1L, 2L)));

        fanoutService.fanout(event, NotificationEventType.TICKET_OPEN, "{}");

        ArgumentCaptor<List<Long>> captor = memberIdsCaptor();
        verify(chunkService).processChunk(eq(event), captor.capture(), any());
        assertThat(captor.getValue()).containsExactly(1L, 2L);
    }

    @Test
    @DisplayName("GAME_START는 gameStartAlert ON 회원 전체에게 fan-out한다")
    void fanout_경기시작_전체발송() {
        NotificationEvent event = NotificationEvent.withId(2L, NotificationEventType.GAME_START, "{}");
        given(settingRepository.findMemberIdsByGameStartAlertTrue(any()))
                .willReturn(new SliceImpl<>(List.of(10L)));

        fanoutService.fanout(event, NotificationEventType.GAME_START, "{}");

        ArgumentCaptor<List<Long>> captor = memberIdsCaptor();
        verify(chunkService).processChunk(eq(event), captor.capture(), any());
        assertThat(captor.getValue()).containsExactly(10L);
    }

    @Test
    @DisplayName("PAYMENT_COMPLETED는 payload의 memberId 단 한 명에게만 발송한다")
    void fanout_결제완료_단건발송() {
        NotificationEvent event = NotificationEvent.withId(3L, NotificationEventType.PAYMENT_COMPLETED, "{}");
        String payload = "{\"paymentId\":99,\"memberId\":5,\"amount\":10000}";
        given(payloadParser.extractMemberId(eq(payload), anyString())).willReturn(5L);

        fanoutService.fanout(event, NotificationEventType.PAYMENT_COMPLETED, payload);

        ArgumentCaptor<List<Long>> captor = memberIdsCaptor();
        verify(chunkService).processChunk(eq(event), captor.capture(), eq(payload));
        assertThat(captor.getValue()).containsExactly(5L);
    }

    @Test
    @DisplayName("PAYMENT_COMPLETED payload에 memberId 없으면 발송하지 않고 실패 반환한다")
    void fanout_결제완료_memberId없음_실패() {
        NotificationEvent event = NotificationEvent.withId(4L, NotificationEventType.PAYMENT_COMPLETED, "{}");
        String invalidPayload = "{\"paymentId\":99,\"amount\":10000}";
        given(payloadParser.extractMemberId(eq(invalidPayload), anyString()))
                .willThrow(new IllegalArgumentException("invalid memberId"));

        boolean result = fanoutService.fanout(event, NotificationEventType.PAYMENT_COMPLETED, invalidPayload);

        verify(chunkService, never()).processChunk(any(), any(), any());
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("CHAT_MENTION은 payload의 memberId 단 한 명에게만 발송한다")
    void fanout_채팅알림_단건발송() {
        NotificationEvent event = NotificationEvent.withId(5L, NotificationEventType.CHAT_MENTION, "{}");
        String payload = "{\"roomId\":7,\"memberId\":42}";
        given(payloadParser.extractMemberId(eq(payload), anyString())).willReturn(42L);

        fanoutService.fanout(event, NotificationEventType.CHAT_MENTION, payload);

        ArgumentCaptor<List<Long>> captor = memberIdsCaptor();
        verify(chunkService).processChunk(eq(event), captor.capture(), eq(payload));
        assertThat(captor.getValue()).containsExactly(42L);
    }

    @Test
    @DisplayName("CHAT_MENTION payload에 memberId 없으면 발송하지 않고 실패 반환한다")
    void fanout_채팅알림_memberId없음_실패() {
        NotificationEvent event = NotificationEvent.withId(6L, NotificationEventType.CHAT_MENTION, "{}");
        String invalidPayload = "{\"roomId\":7}";
        given(payloadParser.extractMemberId(eq(invalidPayload), anyString()))
                .willThrow(new IllegalArgumentException("invalid memberId"));

        boolean result = fanoutService.fanout(event, NotificationEventType.CHAT_MENTION, invalidPayload);

        verify(chunkService, never()).processChunk(any(), any(), any());
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("대상 회원이 없으면 chunkService를 빈 리스트로 호출한다")
    void fanout_대상없음_빈리스트로chunk호출() {
        NotificationEvent event = NotificationEvent.withId(7L, NotificationEventType.TICKET_OPEN, "{}");
        given(settingRepository.findMemberIdsByTicketOpenAlertTrue(any()))
                .willReturn(new SliceImpl<>(List.of()));

        fanoutService.fanout(event, NotificationEventType.TICKET_OPEN, "{}");

        verify(chunkService).processChunk(eq(event), eq(List.of()), any());
    }

    @SuppressWarnings("unchecked")
    private ArgumentCaptor<List<Long>> memberIdsCaptor() {
        return ArgumentCaptor.forClass((Class<List<Long>>) (Class<?>) List.class);
    }
}
