package com.sportsify.notification.application;

import com.sportsify.common.notification.NotificationEventType;
import com.sportsify.notification.application.port.SseNotificationPort;
import com.sportsify.notification.application.sender.NotificationSender;
import com.sportsify.notification.application.service.Dispatcher;
import com.sportsify.notification.domain.model.Notification;
import com.sportsify.notification.domain.model.NotificationChannel;
import com.sportsify.notification.domain.model.NotificationChannelType;
import com.sportsify.notification.domain.model.NotificationEvent;
import com.sportsify.notification.domain.repository.NotificationChannelRepository;
import com.sportsify.notification.domain.repository.NotificationHistoryRepository;
import com.sportsify.notification.domain.repository.NotificationRepository;
import com.sportsify.notification.domain.repository.NotificationSettingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sportsify.notification.domain.model.NotificationSetting;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DispatcherTest {

    @Mock private NotificationRepository notificationRepository;
    @Mock private NotificationChannelRepository channelRepository;
    @Mock private NotificationHistoryRepository historyRepository;
    @Mock private NotificationSettingRepository settingRepository;
    @Mock private SseNotificationPort sseNotificationPort;
    @Mock private NotificationSender emailSender;
    @Mock private NotificationSender slackSender;

    private Dispatcher dispatcher;

    private NotificationEvent event;
    private NotificationChannel emailChannel;
    private Notification notification;

    @BeforeEach
    void setUp() {
        given(emailSender.channelType()).willReturn(NotificationChannelType.EMAIL);
        lenient().when(settingRepository.findByMemberId(anyLong())).thenReturn(Optional.empty());
        dispatcher = new Dispatcher(
                notificationRepository, channelRepository, historyRepository,
                settingRepository, sseNotificationPort, List.of(emailSender)
        );

        event = NotificationEvent.withId(1L, NotificationEventType.PAYMENT_COMPLETED, "{}");
        emailChannel = NotificationChannel.create(10L, NotificationChannelType.EMAIL, "user@example.com");
        notification = Notification.withId(100L, 10L, 1L);
    }

    @Test
    @DisplayName("중복 알림이면 발송하지 않고 false를 반환한다")
    void dispatchToMember_중복알림_스킵() {
        given(notificationRepository.existsByEventIdAndMemberId(1L, 10L)).willReturn(true);

        boolean result = dispatcher.toMember(event, 10L, "{}");

        assertThat(result).isFalse();
        verify(notificationRepository, never()).save(any());
        verify(sseNotificationPort, never()).send(anyLong(), any());
    }

    @Test
    @DisplayName("채널이 없어도 SSE는 전송하고 false를 반환한다")
    void dispatchToMember_채널없음_SSE전송후성공() {
        given(notificationRepository.existsByEventIdAndMemberId(1L, 10L)).willReturn(false);
        given(notificationRepository.save(any())).willReturn(notification);
        given(channelRepository.findByMemberIdAndEnabledTrue(10L)).willReturn(List.of());

        boolean result = dispatcher.toMember(event, 10L, "{}");

        assertThat(result).isFalse();
        verify(sseNotificationPort).send(eq(10L), eq("PAYMENT_COMPLETED"));
        verify(historyRepository, never()).save(any());
    }

    @Test
    @DisplayName("이메일 채널 발송 성공 시 SENT 이력을 저장하고 false를 반환한다")
    void dispatchToMember_이메일발송성공_SENT이력저장() {
        given(notificationRepository.existsByEventIdAndMemberId(1L, 10L)).willReturn(false);
        given(notificationRepository.save(any())).willReturn(notification);
        given(channelRepository.findByMemberIdAndEnabledTrue(10L)).willReturn(List.of(emailChannel));
        willDoNothing().given(emailSender).send(any(), any(), any());

        boolean result = dispatcher.toMember(event, 10L, "{}");

        assertThat(result).isFalse();
        verify(emailSender).send(eq("user@example.com"), eq("PAYMENT_COMPLETED"), eq("{}"));
        verify(historyRepository).save(any());
    }

    @Test
    @DisplayName("발송 실패 시 FAILED 이력을 저장하고 true를 반환한다")
    void dispatchToMember_발송실패_FAILED이력저장() {
        given(notificationRepository.existsByEventIdAndMemberId(1L, 10L)).willReturn(false);
        given(notificationRepository.save(any())).willReturn(notification);
        given(channelRepository.findByMemberIdAndEnabledTrue(10L)).willReturn(List.of(emailChannel));
        willThrow(new RuntimeException("SMTP 오류")).given(emailSender).send(any(), any(), any());

        boolean result = dispatcher.toMember(event, 10L, "{}");

        assertThat(result).isTrue();
        verify(emailSender).send(any(), any(), any());
        verify(historyRepository).save(any());
    }

    @Test
    @DisplayName("알림 수신 거부 설정이면 발송하지 않고 false를 반환한다")
    void dispatchToMember_알림수신거부_스킵() {
        NotificationSetting setting = NotificationSetting.createDefault(10L);
        setting.update(false, false, false, false);
        given(notificationRepository.existsByEventIdAndMemberId(1L, 10L)).willReturn(false);
        given(settingRepository.findByMemberId(10L)).willReturn(Optional.of(setting));

        boolean result = dispatcher.toMember(event, 10L, "{}");

        assertThat(result).isFalse();
        verify(notificationRepository, never()).save(any());
    }

    @Test
    @DisplayName("지원하지 않는 채널 타입이면 FAILED 이력을 저장하고 true를 반환한다")
    void dispatchToMember_지원하지않는채널_FAILED이력저장() {
        NotificationChannel slackChannel = NotificationChannel.create(10L, NotificationChannelType.SLACK, "webhook-url");
        given(notificationRepository.existsByEventIdAndMemberId(1L, 10L)).willReturn(false);
        given(notificationRepository.save(any())).willReturn(notification);
        given(channelRepository.findByMemberIdAndEnabledTrue(10L)).willReturn(List.of(slackChannel));

        boolean result = dispatcher.toMember(event, 10L, "{}");

        assertThat(result).isTrue();
        verify(emailSender, never()).send(any(), any(), any());
        verify(historyRepository).save(any());
    }

    @Nested
    @DisplayName("복수 채널 발송")
    class 복수채널_발송 {

        @BeforeEach
        void setUp() {
            given(slackSender.channelType()).willReturn(NotificationChannelType.SLACK);
            dispatcher = new Dispatcher(
                    notificationRepository, channelRepository, historyRepository,
                    settingRepository, sseNotificationPort, List.of(emailSender, slackSender)
            );
        }

        @Test
        @DisplayName("여러 채널 중 일부만 실패해도 anyFailed는 true를 반환한다")
        void dispatchToMember_복수채널_일부실패_true반환() {
            NotificationChannel slackChannel = NotificationChannel.create(10L, NotificationChannelType.SLACK, "webhook-url");
            given(notificationRepository.existsByEventIdAndMemberId(1L, 10L)).willReturn(false);
            given(notificationRepository.save(any())).willReturn(notification);
            given(channelRepository.findByMemberIdAndEnabledTrue(10L)).willReturn(List.of(emailChannel, slackChannel));
            willDoNothing().given(emailSender).send(any(), any(), any());
            willThrow(new RuntimeException("Slack 오류")).given(slackSender).send(any(), any(), any());

            boolean result = dispatcher.toMember(event, 10L, "{}");

            assertThat(result).isTrue();
            verify(historyRepository, times(2)).save(any());
        }
    }
}
