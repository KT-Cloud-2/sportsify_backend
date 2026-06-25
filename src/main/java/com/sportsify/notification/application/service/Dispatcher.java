package com.sportsify.notification.application.service;

import com.sportsify.notification.application.dto.NotificationResult;
import com.sportsify.notification.application.port.SseNotificationPort;
import com.sportsify.notification.application.sender.NotificationSender;
import com.sportsify.notification.domain.model.*;
import com.sportsify.notification.domain.repository.NotificationChannelRepository;
import com.sportsify.notification.domain.repository.NotificationHistoryRepository;
import com.sportsify.notification.domain.repository.NotificationRepository;
import com.sportsify.notification.domain.repository.NotificationSettingRepository;
import com.sportsify.notification.presentation.dto.NotificationResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.concurrent.Executor;

@Slf4j
@Service
public class Dispatcher {

    private final NotificationRepository notificationRepository;
    private final NotificationChannelRepository channelRepository;
    private final NotificationHistoryRepository historyRepository;
    private final NotificationSettingRepository settingRepository;
    private final SseNotificationPort sseNotificationPort;
    private final Executor sseVirtualThreadExecutor;
    private final Map<NotificationChannelType, NotificationSender> senderMap;

    public Dispatcher(
            NotificationRepository notificationRepository,
            NotificationChannelRepository channelRepository,
            NotificationHistoryRepository historyRepository,
            NotificationSettingRepository settingRepository,
            SseNotificationPort sseNotificationPort,
            Executor sseVirtualThreadExecutor,
            List<NotificationSender> senders
    ) {
        this.notificationRepository = notificationRepository;
        this.channelRepository = channelRepository;
        this.historyRepository = historyRepository;
        this.settingRepository = settingRepository;
        this.sseNotificationPort = sseNotificationPort;
        this.sseVirtualThreadExecutor = sseVirtualThreadExecutor;
        this.senderMap = senders.stream()
                .collect(Collectors.toMap(NotificationSender::channelType, Function.identity()));
    }

    public boolean toMember(NotificationEvent event, Long memberId, String payload) {
        if (notificationRepository.existsByEventIdAndMemberId(event.getId(), memberId)) return false;
        boolean enabled = settingRepository.findByMemberId(memberId)
                .map(s -> s.isEnabledFor(event.getEventType()))
                .orElse(true);
        if (!enabled) return false;

        Notification notification = notificationRepository.save(Notification.create(memberId, event.getId()));
        NotificationResponse ssePayload = NotificationResponse.from(NotificationResult.of(notification, event));

        Map<Boolean, List<NotificationChannel>> partitioned = channelRepository.findByMemberIdAndEnabledTrue(memberId)
                .stream()
                .collect(Collectors.partitioningBy(c -> c.getChannelType() == NotificationChannelType.EMAIL));
        List<NotificationChannel> nonEmailChannels = partitioned.get(false);
        List<NotificationChannel> emailChannels = partitioned.get(true);

        boolean anyFailed = false;
        for (NotificationChannel channel : nonEmailChannels) {
            if (!sendToChannel(notification.getId(), channel, event.getTypeName(), payload)) {
                anyFailed = true;
            }
        }

        scheduleSseAndEmail(memberId, ssePayload, notification.getId(), emailChannels, event.getTypeName(), payload);
        return anyFailed;
    }

    private void scheduleSseAndEmail(Long memberId, NotificationResponse ssePayload,
                                     Long notificationId, List<NotificationChannel> emailChannels,
                                     String subject, String body) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            sseNotificationPort.send(memberId, ssePayload);
            dispatchEmailsAsync(notificationId, emailChannels, subject, body);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                sseNotificationPort.send(memberId, ssePayload);
                dispatchEmailsAsync(notificationId, emailChannels, subject, body);
            }
        });
    }

    private void dispatchEmailsAsync(Long notificationId, List<NotificationChannel> emailChannels,
                                     String subject, String body) {
        if (emailChannels.isEmpty()) return;
        sseVirtualThreadExecutor.execute(() -> {
            for (NotificationChannel channel : emailChannels) {
                sendToChannel(notificationId, channel, subject, body);
            }
        });
    }

    public NotificationHistory sendToChannelAndBuildHistory(Long notificationId, NotificationChannel channel, String subject, String body) {
        NotificationSender sender = senderMap.get(channel.getChannelType());
        if (sender == null) {
            log.warn("지원하지 않는 채널 타입 channelType={} notificationId={}", channel.getChannelType(), notificationId);
            return NotificationHistory.failed(notificationId, channel.getChannelType(), "지원하지 않는 채널 타입");
        }
        try {
            sender.send(channel.getChannelTarget(), subject, body);
            return NotificationHistory.sent(notificationId, channel.getChannelType());
        } catch (Exception e) {
            return NotificationHistory.failed(notificationId, channel.getChannelType(), e.getMessage());
        }
    }

    private boolean sendToChannel(Long notificationId, NotificationChannel channel, String subject, String body) {
        NotificationSender sender = senderMap.get(channel.getChannelType());
        if (sender == null) {
            log.warn("지원하지 않는 채널 타입 channelType={} notificationId={}", channel.getChannelType(), notificationId);
            historyRepository.save(NotificationHistory.failed(notificationId, channel.getChannelType(), "지원하지 않는 채널 타입"));
            return false;
        }
        return attemptSend(notificationId, channel, subject, body, sender);
    }

    private boolean attemptSend(Long notificationId, NotificationChannel channel, String subject, String body, NotificationSender sender) {
        try {
            sender.send(channel.getChannelTarget(), subject, body);
            historyRepository.save(NotificationHistory.sent(notificationId, channel.getChannelType()));
            return true;
        } catch (Exception e) {
            historyRepository.save(NotificationHistory.failed(notificationId, channel.getChannelType(), e.getMessage()));
            return false;
        }
    }
}
