package com.sportsify.notification.application.service;

import com.sportsify.notification.application.port.SseNotificationPort;
import com.sportsify.notification.application.sender.NotificationSender;
import com.sportsify.notification.domain.model.*;
import com.sportsify.notification.domain.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class NotificationEventProcessor {

    private static final int MAX_RETRY = 3;

    private final NotificationEventRepository eventRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationSettingRepository settingRepository;
    private final NotificationChannelRepository channelRepository;
    private final NotificationHistoryRepository historyRepository;
    private final SseNotificationPort sseNotificationPort;
    private final Map<NotificationChannelType, NotificationSender> senderMap;

    public NotificationEventProcessor(
            NotificationEventRepository eventRepository,
            NotificationRepository notificationRepository,
            NotificationSettingRepository settingRepository,
            NotificationChannelRepository channelRepository,
            NotificationHistoryRepository historyRepository,
            SseNotificationPort sseNotificationPort,
            List<NotificationSender> senders
    ) {
        this.eventRepository = eventRepository;
        this.notificationRepository = notificationRepository;
        this.settingRepository = settingRepository;
        this.channelRepository = channelRepository;
        this.historyRepository = historyRepository;
        this.sseNotificationPort = sseNotificationPort;
        this.senderMap = senders.stream()
                .collect(Collectors.toMap(NotificationSender::channelType, Function.identity()));
    }

    @Transactional
    public void process(NotificationEventType eventType, String payload) {
        NotificationEvent event = eventRepository.save(NotificationEvent.create(eventType, payload));
        List<Long> targetMemberIds = resolveTargetMemberIds(eventType);

        if (targetMemberIds.isEmpty()) {
            event.markPublished();
            return;
        }

        boolean anyFailed = processForMembers(event, targetMemberIds, payload);

        if (anyFailed) {
            event.markFailed();
            return;
        }
        event.markPublished();
    }

    private boolean processForMembers(NotificationEvent event, List<Long> memberIds, String payload) {
        boolean anyFailed = false;
        for (Long memberId : memberIds) {
            boolean failed = processForMember(event, memberId, payload);
            if (failed) {
                anyFailed = true;
            }
        }
        return anyFailed;
    }

    private boolean processForMember(NotificationEvent event, Long memberId, String payload) {
        if (notificationRepository.existsByEventIdAndMemberId(event.getId(), memberId)) {
            return false;
        }
        Notification notification = notificationRepository.save(Notification.create(memberId, event.getId()));
        sseNotificationPort.send(memberId, event.getEventType().name());

        List<NotificationChannel> channels = channelRepository.findByMemberIdAndEnabledTrue(memberId);
        return channels.stream()
                .anyMatch(channel -> !sendWithRetry(notification.getId(), channel, event.getEventType().name(), payload));
    }

    private List<Long> resolveTargetMemberIds(NotificationEventType eventType) {
        return switch (eventType) {
            case TICKET_OPEN -> settingRepository.findMemberIdsByTicketOpenAlertTrue();
            case GAME_START -> settingRepository.findMemberIdsByGameStartAlertTrue();
            case PAYMENT_COMPLETED -> settingRepository.findMemberIdsByPaymentAlertTrue();
            case CHAT_MENTION -> settingRepository.findMemberIdsByChatMentionAlertTrue();
        };
    }

    private boolean sendWithRetry(Long notificationId, NotificationChannel channel, String subject, String body) {
        Optional<NotificationSender> sender = Optional.ofNullable(senderMap.get(channel.getChannelType()));
        if (sender.isEmpty()) {
            return true;
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
