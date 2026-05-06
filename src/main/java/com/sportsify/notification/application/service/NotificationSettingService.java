package com.sportsify.notification.application.service;

import com.sportsify.common.exception.BusinessException;
import com.sportsify.common.exception.ErrorCode;
import com.sportsify.notification.application.dto.NotificationChannelResult;
import com.sportsify.notification.application.dto.NotificationSettingResult;
import com.sportsify.notification.domain.model.NotificationChannel;
import com.sportsify.notification.domain.model.NotificationChannelType;
import com.sportsify.notification.domain.model.NotificationSetting;
import com.sportsify.notification.domain.repository.NotificationChannelRepository;
import com.sportsify.notification.domain.repository.NotificationSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationSettingService {

    private final NotificationSettingRepository settingRepository;
    private final NotificationChannelRepository channelRepository;

    @Transactional(readOnly = true)
    public NotificationSettingResult getSetting(Long memberId) {
        NotificationSetting setting = settingRepository.findByMemberId(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_SETTING_NOT_FOUND));
        return NotificationSettingResult.from(setting);
    }

    @Transactional
    public NotificationSettingResult updateSetting(Long memberId, boolean ticketOpenAlert, boolean gameStartAlert, boolean paymentAlert, boolean chatMentionAlert) {
        NotificationSetting setting = settingRepository.findByMemberId(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_SETTING_NOT_FOUND));
        setting.update(ticketOpenAlert, gameStartAlert, paymentAlert, chatMentionAlert);
        return NotificationSettingResult.from(setting);
    }

    @Transactional(readOnly = true)
    public List<NotificationChannelResult> getChannels(Long memberId) {
        return channelRepository.findByMemberIdAndEnabledTrue(memberId)
                .stream()
                .map(NotificationChannelResult::from)
                .toList();
    }

    @Transactional
    public NotificationChannelResult registerChannel(Long memberId, NotificationChannelType channelType, String channelTarget) {
        if (channelRepository.existsByMemberIdAndChannelType(memberId, channelType)) {
            throw new BusinessException(ErrorCode.NOTIFICATION_CHANNEL_ALREADY_EXISTS);
        }
        NotificationChannel channel = channelRepository.save(
                NotificationChannel.create(memberId, channelType, channelTarget)
        );
        return NotificationChannelResult.from(channel);
    }

    @Transactional
    public void deleteChannel(Long memberId, Long channelId) {
        NotificationChannel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_CHANNEL_NOT_FOUND));
        if (!channel.getMemberId().equals(memberId)) {
            throw new BusinessException(ErrorCode.NOTIFICATION_CHANNEL_NOT_FOUND);
        }
        channelRepository.delete(channel);
    }

    @Transactional
    public NotificationChannelResult toggleChannel(Long memberId, Long channelId) {
        NotificationChannel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_CHANNEL_NOT_FOUND));
        if (!channel.getMemberId().equals(memberId)) {
            throw new BusinessException(ErrorCode.NOTIFICATION_CHANNEL_NOT_FOUND);
        }
        channel.toggle();
        return NotificationChannelResult.from(channel);
    }
}
