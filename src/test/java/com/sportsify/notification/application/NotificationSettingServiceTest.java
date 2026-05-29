package com.sportsify.notification.application;

import com.sportsify.common.exception.BusinessException;
import com.sportsify.common.exception.ErrorCode;
import com.sportsify.notification.application.service.NotificationSettingService;
import com.sportsify.notification.domain.model.*;
import com.sportsify.notification.domain.repository.NotificationChannelRepository;
import com.sportsify.notification.domain.repository.NotificationSettingRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationSettingServiceTest {

    @InjectMocks
    private NotificationSettingService settingService;

    @Mock
    private NotificationSettingRepository settingRepository;

    @Mock
    private NotificationChannelRepository channelRepository;

    @Test
    @DisplayName("알림 설정이 없으면 NOTIFICATION_SETTING_NOT_FOUND 예외가 발생한다")
    void getSetting_없으면_예외() {
        given(settingRepository.findByMemberId(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> settingService.getSetting(1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOTIFICATION_SETTING_NOT_FOUND);
    }

    @Test
    @DisplayName("이미 등록된 채널 타입으로 등록 시 NOTIFICATION_CHANNEL_ALREADY_EXISTS 예외가 발생한다")
    void registerChannel_중복채널_예외() {
        given(channelRepository.existsByMemberIdAndChannelType(1L, NotificationChannelType.EMAIL)).willReturn(true);

        assertThatThrownBy(() -> settingService.registerChannel(1L, NotificationChannelType.EMAIL, "a@test.com"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOTIFICATION_CHANNEL_ALREADY_EXISTS);
    }

    @Test
    @DisplayName("존재하지 않는 채널 삭제 시 NOTIFICATION_CHANNEL_NOT_FOUND 예외가 발생한다")
    void deleteChannel_없음_예외() {
        given(channelRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> settingService.deleteChannel(1L, 99L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOTIFICATION_CHANNEL_NOT_FOUND);
    }

    @Test
    @DisplayName("다른 회원의 채널 삭제 시 NOTIFICATION_CHANNEL_NOT_FOUND 예외가 발생한다")
    void deleteChannel_다른회원채널_예외() {
        NotificationChannel channel = NotificationChannel.create(2L, NotificationChannelType.EMAIL, "other@test.com");
        given(channelRepository.findById(1L)).willReturn(Optional.of(channel));

        assertThatThrownBy(() -> settingService.deleteChannel(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOTIFICATION_CHANNEL_NOT_FOUND);
    }

    @Test
    @DisplayName("채널 등록 성공 시 저장된 채널 정보를 반환한다")
    void registerChannel_성공() {
        NotificationChannel channel = NotificationChannel.create(1L, NotificationChannelType.EMAIL, "a@test.com");
        given(channelRepository.existsByMemberIdAndChannelType(1L, NotificationChannelType.EMAIL)).willReturn(false);
        given(channelRepository.countByMemberIdForUpdate(1L)).willReturn(0);
        given(channelRepository.save(any())).willReturn(channel);

        var result = settingService.registerChannel(1L, NotificationChannelType.EMAIL, "a@test.com");

        assertThat(result.channelType()).isEqualTo(NotificationChannelType.EMAIL);
        assertThat(result.channelTarget()).isEqualTo("a@test.com");
    }

    @Test
    @DisplayName("채널이 이미 2개 등록된 경우 NOTIFICATION_CHANNEL_LIMIT_EXCEEDED 예외가 발생한다")
    void registerChannel_최대개수초과_예외() {
        given(channelRepository.existsByMemberIdAndChannelType(1L, NotificationChannelType.SLACK)).willReturn(false);
        given(channelRepository.countByMemberIdForUpdate(1L)).willReturn(2);

        assertThatThrownBy(() -> settingService.registerChannel(1L, NotificationChannelType.SLACK, "webhook"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOTIFICATION_CHANNEL_LIMIT_EXCEEDED);
    }
}
