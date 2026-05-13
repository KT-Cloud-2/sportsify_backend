package com.sportsify.notification.application;

import com.sportsify.common.exception.BusinessException;
import com.sportsify.common.exception.ErrorCode;
import com.sportsify.notification.application.port.SseNotificationPort;
import com.sportsify.notification.application.service.NotificationService;
import com.sportsify.notification.domain.model.Notification;
import com.sportsify.notification.domain.model.NotificationEvent;
import com.sportsify.common.notification.NotificationEventType;
import com.sportsify.notification.domain.repository.NotificationEventRepository;
import com.sportsify.notification.domain.repository.NotificationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @InjectMocks
    private NotificationService notificationService;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private NotificationEventRepository eventRepository;

    @Mock
    private SseNotificationPort sseNotificationPort;

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
