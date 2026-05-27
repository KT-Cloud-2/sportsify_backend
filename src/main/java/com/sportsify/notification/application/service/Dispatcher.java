package com.sportsify.notification.application.service;

import com.sportsify.notification.application.port.SseNotificationPort;
import com.sportsify.notification.application.sender.NotificationSender;
import com.sportsify.notification.domain.model.*;
import com.sportsify.notification.domain.repository.NotificationChannelRepository;
import com.sportsify.notification.domain.repository.NotificationHistoryRepository;
import com.sportsify.notification.domain.repository.NotificationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class Dispatcher {

    private final NotificationRepository notificationRepository;
    private final NotificationChannelRepository channelRepository;
    private final NotificationHistoryRepository historyRepository;
    private final SseNotificationPort sseNotificationPort;
    private final Map<NotificationChannelType, NotificationSender> senderMap;

    public Dispatcher(
            NotificationRepository notificationRepository,
            NotificationChannelRepository channelRepository,
            NotificationHistoryRepository historyRepository,
            SseNotificationPort sseNotificationPort,
            List<NotificationSender> senders
    ) {
        this.notificationRepository = notificationRepository;
        this.channelRepository = channelRepository;
        this.historyRepository = historyRepository;
        this.sseNotificationPort = sseNotificationPort;
        this.senderMap = senders.stream()
                .collect(Collectors.toMap(NotificationSender::channelType, Function.identity()));
    }

    public boolean toMember(NotificationEvent event, Long memberId, String payload) {
        if (notificationRepository.existsByEventIdAndMemberId(event.getId(), memberId)) return false;

        Notification notification = notificationRepository.save(Notification.create(memberId, event.getId()));
        scheduleSse(memberId, event.getTypeName());

        boolean anyFailed = false;
        for (NotificationChannel channel : channelRepository.findByMemberIdAndEnabledTrue(memberId)) {
            if (!sendToChannel(notification.getId(), channel, event.getTypeName(), payload)) {
                anyFailed = true;
            }
        }
        return anyFailed;
    }

    private void scheduleSse(Long memberId, String eventTypeName) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            sseNotificationPort.send(memberId, eventTypeName);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                sseNotificationPort.send(memberId, eventTypeName);
            }
        });
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
