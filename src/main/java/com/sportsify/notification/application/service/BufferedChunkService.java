package com.sportsify.notification.application.service;

import com.sportsify.notification.application.port.SseNotificationPort;
import com.sportsify.notification.domain.model.Notification;
import com.sportsify.notification.domain.model.NotificationBufferItem;
import com.sportsify.notification.domain.model.NotificationEvent;
import com.sportsify.notification.domain.model.NotificationSetting;
import com.sportsify.notification.domain.repository.NotificationRepository;
import com.sportsify.notification.domain.repository.NotificationSettingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BufferedChunkService {

    private final NotificationRepository notificationRepository;
    private final NotificationSettingRepository settingRepository;
    private final SseNotificationPort sseNotificationPort;
    private final NotificationBatchBuffer buffer;
    private final BulkPersistService bulkPersistService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void enqueueChunk(NotificationEvent event, List<Long> memberIds, String payload,
                             String streamKey, RecordId recordId) {
        if (memberIds.isEmpty()) {
            return;
        }

        Set<Long> alreadyNotified = notificationRepository
                .findExistingMemberIdsByEventId(event.getId(), memberIds);

        Map<Long, NotificationSetting> settingMap = settingRepository
                .findByMemberIdIn(memberIds).stream()
                .collect(Collectors.toMap(NotificationSetting::getMemberId, s -> s));

        List<Notification> ssePending = new ArrayList<>();
        List<Notification> directPersist = new ArrayList<>();

        for (Long memberId : memberIds) {
            if (alreadyNotified.contains(memberId)) {
                continue;
            }
            NotificationSetting setting = settingMap.get(memberId);
            if (setting != null && !setting.isEnabledFor(event.getEventType())) {
                continue;
            }
            Notification notification = Notification.create(memberId, event.getId());
            if (sseNotificationPort.isConnected(memberId)) {
                ssePending.add(notification);
            } else {
                directPersist.add(notification);
            }
        }

        if (!directPersist.isEmpty()) {
            bulkPersistService.persist(event, directPersist, payload);
        }

        for (Notification notification : ssePending) {
            buffer.enqueue(NotificationBufferItem.of(notification, event.getTypeName(), payload, streamKey, recordId));
        }
    }
}
