package com.sportsify.notification.application.service;

import com.sportsify.notification.application.port.SseNotificationPort;
import com.sportsify.notification.application.sender.NotificationSender;
import com.sportsify.notification.domain.model.Notification;
import com.sportsify.notification.domain.model.NotificationChannel;
import com.sportsify.notification.domain.model.NotificationChannelType;
import com.sportsify.notification.domain.model.NotificationEvent;
import com.sportsify.notification.domain.model.NotificationHistory;
import com.sportsify.notification.domain.repository.NotificationChannelRepository;
import com.sportsify.notification.domain.repository.NotificationHistoryRepository;
import com.sportsify.notification.domain.repository.NotificationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class NotificationDispatcher {

    private static final int MAX_RETRY = 3;

    private final NotificationRepository notificationRepository;
    private final NotificationChannelRepository channelRepository;
    private final NotificationHistoryRepository historyRepository;
    private final SseNotificationPort sseNotificationPort;
    private final Map<NotificationChannelType, NotificationSender> senderMap;

    public NotificationDispatcher(
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

    public boolean dispatchToMember(NotificationEvent event, Long memberId, String payload) {
        if (notificationRepository.existsByEventIdAndMemberId(event.getId(), memberId)) {
            return false;
        }
        Notification notification = notificationRepository.save(Notification.create(memberId, event.getId()));
        sseNotificationPort.send(memberId, event.getEventType().name());

        List<NotificationChannel> channels = channelRepository.findByMemberIdAndEnabledTrue(memberId);
        boolean anyFailed = false;
        for (NotificationChannel channel : channels) {
            if (!sendWithRetry(notification.getId(), channel, event.getEventType().name(), payload)) {
                anyFailed = true;
            }
        }
        return anyFailed;
    }

    private boolean sendWithRetry(Long notificationId, NotificationChannel channel, String subject, String body) {
        Optional<NotificationSender> sender = Optional.ofNullable(senderMap.get(channel.getChannelType()));
        if (sender.isEmpty()) {
            log.warn("지원하지 않는 채널 타입 channelType={} notificationId={}", channel.getChannelType(), notificationId);
            historyRepository.save(NotificationHistory.failed(notificationId, channel.getChannelType(), "지원하지 않는 채널 타입"));
            return false;
        }
        return attemptSend(notificationId, channel, subject, body, sender.get());
    }

    private boolean attemptSend(Long notificationId, NotificationChannel channel, String subject, String body, NotificationSender sender) {
        for (int attempt = 1; attempt <= MAX_RETRY; attempt++) {
            try {
                sender.send(channel.getChannelTarget(), subject, body);
                historyRepository.save(NotificationHistory.sent(notificationId, channel.getChannelType()));
                return true;
            } catch (Exception e) {
                log.warn("알림 발송 실패 attempt={}/{} channel={} error={}", attempt, MAX_RETRY, channel.getChannelType(), e.getMessage());
                if (attempt == MAX_RETRY) {
                    historyRepository.save(NotificationHistory.failed(notificationId, channel.getChannelType(), e.getMessage()));
                }
            }
        }
        return false;
    }
}
