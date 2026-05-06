package com.sportsify.notification.application;

import com.sportsify.notification.application.sender.NotificationSender;
import com.sportsify.notification.application.service.NotificationEventProcessor;
import com.sportsify.notification.application.sse.SseEmitterManager;
import com.sportsify.notification.domain.model.*;
import com.sportsify.notification.domain.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationEventProcessorTest {

    @Mock private NotificationEventRepository eventRepository;
    @Mock private NotificationRepository notificationRepository;
    @Mock private NotificationSettingRepository settingRepository;
    @Mock private NotificationChannelRepository channelRepository;
    @Mock private NotificationHistoryRepository historyRepository;
    @Mock private SseEmitterManager sseEmitterManager;
    @Mock private NotificationSender emailSender;

    private NotificationEventProcessor processor;

    @BeforeEach
    void setUp() {
        given(emailSender.channelType()).willReturn(NotificationChannelType.EMAIL);
        processor = new NotificationEventProcessor(
            eventRepository, notificationRepository, settingRepository,
            channelRepository, historyRepository, sseEmitterManager,
            List.of(emailSender)
        );
    }

    @Test
    @DisplayName("알림 설정이 OFF인 회원은 알림이 생성되지 않는다")
    void process_알림설정OFF_스킵() {
        NotificationEvent event = NotificationEvent.create(NotificationEventType.TICKET_OPEN, "{}");
        given(eventRepository.save(any())).willReturn(event);
        given(settingRepository.findMemberIdsByTicketOpenAlertTrue()).willReturn(List.of());

        processor.process(NotificationEventType.TICKET_OPEN, "{}");

        verify(notificationRepository, never()).save(any());
    }

    @Test
    @DisplayName("이미 동일 eventId+memberId 알림이 존재하면 발송하지 않는다")
    void process_중복알림_스킵() {
        NotificationEvent event = notificationEventWithId(10L, NotificationEventType.PAYMENT_COMPLETED);
        given(eventRepository.save(any())).willReturn(event);
        given(settingRepository.findMemberIdsByPaymentAlertTrue()).willReturn(List.of(1L));
        given(notificationRepository.existsByEventIdAndMemberId(10L, 1L)).willReturn(true);

        processor.process(NotificationEventType.PAYMENT_COMPLETED, "{}");

        verify(notificationRepository, never()).save(any());
    }

    @Test
    @DisplayName("발송 성공 시 NotificationHistory에 SENT 상태로 저장된다")
    void process_발송성공_SENT저장() {
        NotificationEvent event = notificationEventWithId(10L, NotificationEventType.PAYMENT_COMPLETED);
        Notification notification = notificationWithId(100L, 1L, 10L);
        NotificationChannel channel = NotificationChannel.create(1L, NotificationChannelType.EMAIL, "a@test.com");

        given(eventRepository.save(any())).willReturn(event);
        given(settingRepository.findMemberIdsByPaymentAlertTrue()).willReturn(List.of(1L));
        given(notificationRepository.existsByEventIdAndMemberId(10L, 1L)).willReturn(false);
        given(notificationRepository.save(any())).willReturn(notification);
        given(channelRepository.findByMemberIdAndEnabledTrue(1L)).willReturn(List.of(channel));
        doNothing().when(emailSender).send(any(), any(), any());

        processor.process(NotificationEventType.PAYMENT_COMPLETED, "{}");

        verify(historyRepository).save(argThat(h -> h.getStatus() == NotificationSendStatus.SENT));
    }

    @Test
    @DisplayName("발송 3회 실패 시 NotificationHistory에 FAILED 상태로 저장된다")
    void process_발송3회실패_FAILED저장() {
        NotificationEvent event = notificationEventWithId(10L, NotificationEventType.PAYMENT_COMPLETED);
        Notification notification = notificationWithId(100L, 1L, 10L);
        NotificationChannel channel = NotificationChannel.create(1L, NotificationChannelType.EMAIL, "a@test.com");

        given(eventRepository.save(any())).willReturn(event);
        given(settingRepository.findMemberIdsByPaymentAlertTrue()).willReturn(List.of(1L));
        given(notificationRepository.existsByEventIdAndMemberId(10L, 1L)).willReturn(false);
        given(notificationRepository.save(any())).willReturn(notification);
        given(channelRepository.findByMemberIdAndEnabledTrue(1L)).willReturn(List.of(channel));
        doThrow(new RuntimeException("SMTP 오류")).when(emailSender).send(any(), any(), any());

        processor.process(NotificationEventType.PAYMENT_COMPLETED, "{}");

        verify(emailSender, times(3)).send(any(), any(), any());
        verify(historyRepository).save(argThat(h -> h.getStatus() == NotificationSendStatus.FAILED));
    }

    @Test
    @DisplayName("chatMentionAlert가 ON인 회원에게 CHAT_MENTION 알림이 발송된다")
    void process_채팅멘션알림ON_발송됨() {
        NotificationEvent event = notificationEventWithId(20L, NotificationEventType.CHAT_MENTION);
        Notification notification = notificationWithId(200L, 1L, 20L);

        given(eventRepository.save(any())).willReturn(event);
        given(settingRepository.findMemberIdsByChatMentionAlertTrue()).willReturn(List.of(1L));
        given(notificationRepository.existsByEventIdAndMemberId(20L, 1L)).willReturn(false);
        given(notificationRepository.save(any())).willReturn(notification);
        given(channelRepository.findByMemberIdAndEnabledTrue(1L)).willReturn(List.of());

        processor.process(NotificationEventType.CHAT_MENTION, "{\"roomId\":3}");

        verify(notificationRepository).save(any());
        verify(sseEmitterManager).send(1L, "CHAT_MENTION");
    }

    @Test
    @DisplayName("chatMentionAlert가 OFF인 회원에게 CHAT_MENTION 알림이 발송되지 않는다")
    void process_채팅멘션알림OFF_스킵() {
        NotificationEvent event = notificationEventWithId(21L, NotificationEventType.CHAT_MENTION);
        given(eventRepository.save(any())).willReturn(event);
        given(settingRepository.findMemberIdsByChatMentionAlertTrue()).willReturn(List.of());

        processor.process(NotificationEventType.CHAT_MENTION, "{}");

        verify(notificationRepository, never()).save(any());
        verify(sseEmitterManager, never()).send(any(), any());
    }

    private NotificationEvent notificationEventWithId(Long id, NotificationEventType type) {
        NotificationEvent event = NotificationEvent.create(type, "{}");
        setId(event, id);
        return event;
    }

    private Notification notificationWithId(Long id, Long memberId, Long eventId) {
        Notification notification = Notification.create(memberId, eventId);
        setId(notification, id);
        return notification;
    }

    private void setId(Object entity, Long id) {
        try {
            var field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
