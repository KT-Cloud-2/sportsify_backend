package com.sportsify.notification.application.service;

import com.sportsify.common.exception.BusinessException;
import com.sportsify.common.exception.ErrorCode;
import com.sportsify.notification.application.dto.NotificationResult;
import com.sportsify.notification.infrastructure.sse.SseEmitterManager;
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

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationEventRepository eventRepository;
    private final SseEmitterManager sseEmitterManager;

    @Transactional(readOnly = true)
    public Page<NotificationResult> getNotifications(Long memberId, Pageable pageable) {
        return notificationRepository.findByMemberIdOrderByCreatedAtDesc(memberId, pageable)
                .map(n -> {
                    NotificationEvent event = eventRepository.findById(n.getEventId())
                            .orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND));
                    return NotificationResult.of(n, event);
                });
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
        return sseEmitterManager.subscribe(memberId);
    }
}
