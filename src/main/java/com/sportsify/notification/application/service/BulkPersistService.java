package com.sportsify.notification.application.service;

import com.sportsify.notification.domain.model.*;
import com.sportsify.notification.domain.repository.NotificationChannelRepository;
import com.sportsify.notification.domain.repository.NotificationHistoryRepository;
import com.sportsify.notification.domain.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BulkPersistService {

    private final NotificationRepository notificationRepository;
    private final NotificationHistoryRepository historyRepository;
    private final NotificationChannelRepository channelRepository;
    private final Dispatcher dispatcher;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean persist(NotificationEvent event, List<Notification> notifications, String payload) {
        List<Notification> saved = notificationRepository.saveAll(notifications);
        Map<Long, List<NotificationChannel>> channelsByMember = channelRepository
                .findByMemberIdInAndEnabledTrue(saved.stream().map(Notification::getMemberId).toList())
                .stream()
                .collect(Collectors.groupingBy(NotificationChannel::getMemberId));

        List<NotificationHistory> histories = new ArrayList<>();
        boolean anyFailed = false;

        for (Notification notification : saved) {
            List<NotificationChannel> channels = channelsByMember.getOrDefault(notification.getMemberId(), List.of());
            for (NotificationChannel channel : channels) {
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

        notifications.forEach(notification -> {
            NotificationBufferItem item = itemByMemberId.get(notification.getMemberId());
            channelsByMember.getOrDefault(notification.getMemberId(), List.of()).forEach(channel ->
                    histories.add(dispatcher.sendToChannelAndBuildHistory(notification.getId(), channel, item.subject(), item.payload()))
            );
        });

        if (!histories.isEmpty()) {
            historyRepository.saveAll(histories);
        }
    }
}
