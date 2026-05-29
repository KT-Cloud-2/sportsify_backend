package com.sportsify.notification.application.service;

import com.sportsify.common.exception.BusinessException;
import com.sportsify.common.exception.ErrorCode;
import com.sportsify.notification.application.dto.NotificationChannelResult;
import com.sportsify.notification.application.dto.NotificationSettingResult;
import com.sportsify.notification.application.dto.UpdateNotificationSettingCommand;
import com.sportsify.notification.domain.model.NotificationChannel;
import com.sportsify.notification.domain.model.NotificationChannelType;
import com.sportsify.notification.domain.model.NotificationSetting;
import com.sportsify.notification.domain.repository.NotificationChannelRepository;
import com.sportsify.notification.domain.repository.NotificationSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationSettingService {

    private static final int MAX_CHANNELS_PER_MEMBER = 2;

    private final NotificationSettingRepository settingRepository;
    private final NotificationChannelRepository channelRepository;

    @Transactional(readOnly = true)
    public NotificationSettingResult getSetting(Long memberId) {
        NotificationSetting setting = settingRepository.findByMemberId(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_SETTING_NOT_FOUND));
        return NotificationSettingResult.from(setting);
    }

    @Transactional
    public NotificationSettingResult updateSetting(Long memberId, UpdateNotificationSettingCommand command) {
        NotificationSetting setting = settingRepository.findByMemberId(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_SETTING_NOT_FOUND));
        setting.update(command.ticketOpenAlert(), command.gameStartAlert(), command.paymentAlert(), command.chatMentionAlert());
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
        if (channelRepository.countByMemberIdForUpdate(memberId) >= MAX_CHANNELS_PER_MEMBER) {
            throw new BusinessException(ErrorCode.NOTIFICATION_CHANNEL_LIMIT_EXCEEDED);
        }
        try {
            NotificationChannel channel = channelRepository.save(
                    NotificationChannel.create(memberId, channelType, channelTarget)
            );
            return NotificationChannelResult.from(channel);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.NOTIFICATION_CHANNEL_ALREADY_EXISTS);
        }
    }

    @Transactional
    public void deleteChannel(Long memberId, Long channelId) {
        NotificationChannel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_CHANNEL_NOT_FOUND));
        channel.validateOwner(memberId);
        channelRepository.delete(channel);
    }

    @Transactional
    public NotificationChannelResult toggleChannel(Long memberId, Long channelId) {
        NotificationChannel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_CHANNEL_NOT_FOUND));
        channel.validateOwner(memberId);
        channel.toggle();
        return NotificationChannelResult.from(channel);
    }
}
