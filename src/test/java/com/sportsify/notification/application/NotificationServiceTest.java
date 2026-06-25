package com.sportsify.notification.application;

import com.sportsify.common.exception.BusinessException;
import com.sportsify.common.exception.ErrorCode;
import com.sportsify.notification.application.port.SseNotificationPort;
import com.sportsify.notification.application.service.NotificationService;
import com.sportsify.notification.domain.model.Notification;
import com.sportsify.notification.domain.model.NotificationSetting;
import com.sportsify.notification.domain.repository.NotificationChannelRepository;
import com.sportsify.notification.domain.repository.NotificationEventRepository;
import com.sportsify.notification.domain.repository.NotificationRepository;
import com.sportsify.notification.domain.repository.NotificationSettingRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @InjectMocks
    private NotificationService notificationService;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private NotificationEventRepository eventRepository;

    @Mock
    private NotificationSettingRepository settingRepository;

    @Mock
    private NotificationChannelRepository channelRepository;

    @Mock
    private SseNotificationPort sseNotificationPort;

    // ─── subscribe ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("SSE 구독")
    class SSE구독 {

        @Test
        @DisplayName("설정이 존재하면 저장된 설정으로 SSE 구독을 시작한다")
        void subscribe_설정존재_저장된설정사용() {
            // GIVEN
            NotificationSetting saved = NotificationSetting.createDefault(1L);
            SseEmitter emitter = new SseEmitter();
            given(settingRepository.findByMemberId(1L)).willReturn(Optional.of(saved));
            given(sseNotificationPort.subscribe(eq(1L), eq(saved), any())).willReturn(emitter);

            // WHEN
            SseEmitter result = notificationService.subscribe(1L);

            // THEN
            assertThat(result).isSameAs(emitter);
            verify(settingRepository).findByMemberId(1L);
        }

        @Test
        @DisplayName("설정이 없으면 기본값으로 SSE 구독을 시작하고 DB에 저장하지 않는다")
        void subscribe_설정없음_기본설정사용() {
            // GIVEN
            SseEmitter emitter = new SseEmitter();
            given(settingRepository.findByMemberId(1L)).willReturn(Optional.empty());
            given(sseNotificationPort.subscribe(eq(1L), any(NotificationSetting.class), any())).willReturn(emitter);

            // WHEN
            SseEmitter result = notificationService.subscribe(1L);

            // THEN
            assertThat(result).isSameAs(emitter);
            verify(settingRepository, never()).save(any());
        }
    }

    // ─── markRead ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("읽지 않은 알림을 읽음 처리한다")
    void markRead_성공() {
        Notification notification = Notification.create(1L, 10L);
        given(notificationRepository.findByIdAndMemberId(1L, 1L)).willReturn(Optional.of(notification));

        notificationService.markRead(1L, 1L);

        assertThat(notification.isAlreadyRead()).isTrue();
    }

    @Test
    @DisplayName("이미 읽은 알림을 다시 읽음 처리하면 NOTIFICATION_ALREADY_READ 예외가 발생한다")
    void markRead_이미읽음_예외() {
        Notification notification = Notification.create(1L, 10L);
        notification.markRead();
        given(notificationRepository.findByIdAndMemberId(1L, 1L)).willReturn(Optional.of(notification));

        assertThatThrownBy(() -> notificationService.markRead(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOTIFICATION_ALREADY_READ);
    }

    @Test
    @DisplayName("존재하지 않는 알림 읽음 처리 시 NOTIFICATION_NOT_FOUND 예외가 발생한다")
    void markRead_없는알림_예외() {
        given(notificationRepository.findByIdAndMemberId(1L, 1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.markRead(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOTIFICATION_NOT_FOUND);
    }
}
