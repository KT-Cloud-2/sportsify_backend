package com.sportsify.notification.application.service;

import com.sportsify.common.notification.NotificationEventType;
import com.sportsify.notification.application.port.SseNotificationPort;
import com.sportsify.notification.domain.model.*;
import com.sportsify.notification.domain.repository.NotificationChannelRepository;
import com.sportsify.notification.domain.repository.NotificationHistoryRepository;
import com.sportsify.notification.domain.repository.NotificationRepository;
import com.sportsify.notification.presentation.dto.NotificationResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BulkPersistService {

    private final NotificationRepository notificationRepository;
    private final NotificationHistoryRepository historyRepository;
    private final NotificationChannelRepository channelRepository;
    private final SseNotificationPort sseNotificationPort;
    private final Dispatcher dispatcher;
    private final Executor sseVirtualThreadExecutor;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean persist(NotificationEvent event, List<Notification> notifications, String payload) {
        List<Notification> saved = notificationRepository.saveAll(notifications);
        Map<Long, List<NotificationChannel>> channelsByMember = channelRepository
                .findByMemberIdInAndEnabledTrue(saved.stream().map(Notification::getMemberId).toList())
                .stream()
                .collect(Collectors.groupingBy(NotificationChannel::getMemberId));

        List<NotificationHistory> histories = new ArrayList<>();
        List<NotificationChannel> emailChannelsDeferred = new ArrayList<>();
        List<Long> emailNotificationIds = new ArrayList<>();
        boolean anyFailed = false;

        for (Notification notification : saved) {
            List<NotificationChannel> channels = channelsByMember.getOrDefault(notification.getMemberId(), List.of());
            for (NotificationChannel channel : channels) {
                if (channel.getChannelType() == NotificationChannelType.EMAIL) {
                    emailChannelsDeferred.add(channel);
                    emailNotificationIds.add(notification.getId());
                    continue;
                }
                NotificationHistory history = dispatcher.sendToChannelAndBuildHistory(
                        notification.getId(), channel, event.getTypeName(), payload
                );
                histories.add(history);
                if (history.isFailed()) {
                    anyFailed = true;
                }
            }
        }

        if (!histories.isEmpty()) {
            historyRepository.saveAll(histories);
        }

        if (!emailChannelsDeferred.isEmpty()) {
            sseVirtualThreadExecutor.execute(() -> {
                List<NotificationHistory> emailHistories = new ArrayList<>();
                for (int i = 0; i < emailChannelsDeferred.size(); i++) {
                    emailHistories.add(dispatcher.sendToChannelAndBuildHistory(
                            emailNotificationIds.get(i), emailChannelsDeferred.get(i), event.getTypeName(), payload
                    ));
                }
                historyRepository.saveAll(emailHistories);
            });
        }

        return anyFailed;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void persistBuffered(List<NotificationBufferItem> items) {
        Map<Long, NotificationBufferItem> itemByMemberId = items.stream()
                .collect(Collectors.toMap(i -> i.notification().getMemberId(),
                        Function.identity(), (a, _) -> a));

        List<Notification> notifications = notificationRepository.saveAll(items.stream()
                .map(NotificationBufferItem::notification)
                .toList());

        List<Long> memberIds = notifications.stream().map(Notification::getMemberId).toList();

        Map<Long, List<NotificationChannel>> channelsByMember = channelRepository
                .findByMemberIdInAndEnabledTrue(memberIds)
                .stream()
                .collect(Collectors.groupingBy(NotificationChannel::getMemberId));

        List<NotificationHistory> histories = new ArrayList<>();
        List<NotificationChannel> emailChannelsDeferred = new ArrayList<>();
        List<Long> emailNotificationIds = new ArrayList<>();
        List<String[]> emailSubjectPayloads = new ArrayList<>();

        notifications.forEach(notification -> {
            NotificationBufferItem item = itemByMemberId.get(notification.getMemberId());
            channelsByMember.getOrDefault(notification.getMemberId(), List.of()).forEach(channel -> {
                if (channel.getChannelType() == NotificationChannelType.EMAIL) {
                    emailChannelsDeferred.add(channel);
                    emailNotificationIds.add(notification.getId());
                    emailSubjectPayloads.add(new String[]{item.subject(), item.payload()});
                    return;
                }
                histories.add(dispatcher.sendToChannelAndBuildHistory(notification.getId(), channel, item.subject(), item.payload()));
            });
            sendSseAfterPersist(notification, item);
        });

        if (!histories.isEmpty()) {
            historyRepository.saveAll(histories);
        }

        if (!emailChannelsDeferred.isEmpty()) {
            sseVirtualThreadExecutor.execute(() -> {
                List<NotificationHistory> emailHistories = new ArrayList<>();
                for (int i = 0; i < emailChannelsDeferred.size(); i++) {
                    emailHistories.add(dispatcher.sendToChannelAndBuildHistory(
                            emailNotificationIds.get(i), emailChannelsDeferred.get(i),
                            emailSubjectPayloads.get(i)[0], emailSubjectPayloads.get(i)[1]
                    ));
                }
                historyRepository.saveAll(emailHistories);
            });
        }
    }

    private void sendSseAfterPersist(Notification notification, NotificationBufferItem item) {
        try {
            NotificationEventType eventType = NotificationEventType.valueOf(item.subject());
            NotificationResponse ssePayload = new NotificationResponse(
                    notification.getId(),
                    eventType,
                    item.payload(),
                    notification.isAlreadyRead(),
                    notification.getCreatedAt().toInstant(ZoneOffset.UTC)
            );
            sseNotificationPort.send(notification.getMemberId(), ssePayload);
        } catch (Exception e) {
            log.warn("SSE 발송 실패 memberId={} eventType={}", notification.getMemberId(), item.subject());
        }
    }
}
