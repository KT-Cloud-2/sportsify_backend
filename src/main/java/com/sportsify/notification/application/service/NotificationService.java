package com.sportsify.notification.application.service;

import com.sportsify.common.exception.BusinessException;
import com.sportsify.common.exception.ErrorCode;
import com.sportsify.notification.application.dto.NotificationResult;
import com.sportsify.notification.application.port.SseNotificationPort;
import com.sportsify.notification.domain.model.Notification;
import com.sportsify.notification.domain.model.NotificationEvent;
import com.sportsify.notification.domain.repository.NotificationEventRepository;
import com.sportsify.notification.domain.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationEventRepository eventRepository;
    private final SseNotificationPort sseNotificationPort;

    @Transactional(readOnly = true)
    public Page<NotificationResult> getNotifications(Long memberId, Pageable pageable) {
        Page<Notification> notifications = notificationRepository.findByMemberIdOrderByCreatedAtDesc(memberId, pageable);
        Map<Long, NotificationEvent> eventMap = buildEventMap(notifications.getContent());
        return notifications.map(n -> toResult(n, eventMap));
    }

    @Transactional
    public void markRead(Long memberId, Long notificationId) {
        Notification notification = notificationRepository.findByIdAndMemberId(notificationId, memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND));
        if (notification.isAlreadyRead()) {
            throw new BusinessException(ErrorCode.NOTIFICATION_ALREADY_READ);
        }
        notification.markRead();
    }

    @Transactional
    public void markAllRead(Long memberId) {
        notificationRepository.markAllReadByMemberId(memberId);
    }

    public SseEmitter subscribe(Long memberId) {
        return sseNotificationPort.subscribe(memberId);
    }

    private Map<Long, NotificationEvent> buildEventMap(List<Notification> notifications) {
        List<Long> eventIds = notifications.stream()
                .map(Notification::getEventId)
                .toList();
        return eventRepository.findAllById(eventIds).stream()
                .collect(Collectors.toMap(NotificationEvent::getId, Function.identity()));
    }

    private NotificationResult toResult(Notification notification, Map<Long, NotificationEvent> eventMap) {
        NotificationEvent event = eventMap.get(notification.getEventId());
        if (event == null) {
            throw new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND);
        }
        return NotificationResult.of(notification, event);
    }
}
