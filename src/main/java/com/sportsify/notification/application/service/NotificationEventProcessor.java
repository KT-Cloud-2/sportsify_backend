package com.sportsify.notification.application.service;

import com.sportsify.notification.application.port.SseNotificationPort;
import com.sportsify.notification.application.sender.NotificationSender;
import com.sportsify.notification.domain.model.*;
import com.sportsify.notification.domain.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
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
    private static final int CHUNK_SIZE = 500;

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
    public NotificationEvent saveEvent(NotificationEventType eventType, String payload) {
        return eventRepository.save(NotificationEvent.create(eventType, payload));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markEventStatus(Long eventId, boolean anyFailed) {
        eventRepository.findById(eventId).ifPresent(event -> {
            if (anyFailed) {
                event.markFailed();
            } else {
                event.markPublished();
            }
        });
    }

    public void process(NotificationEventType eventType, String payload) {
        NotificationEvent event = saveEvent(eventType, payload);

        boolean anyFailed = dispatchInChunks(event, eventType, payload);
        markEventStatus(event.getId(), anyFailed);
    }

    private boolean dispatchInChunks(NotificationEvent event, NotificationEventType eventType, String payload) {
        boolean anyFailed = false;
        int page = 0;
        Slice<Long> chunk;

        do {
            chunk = resolveTargetMemberIds(eventType, PageRequest.of(page, CHUNK_SIZE));
            boolean chunkFailed = processChunk(event, chunk.getContent(), payload);
            if (chunkFailed) {
                anyFailed = true;
            }
            page++;
        } while (chunk.hasNext());

        if (page == 1 && chunk.getContent().isEmpty()) {
            return false;
        }
        return anyFailed;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean processChunk(NotificationEvent event, List<Long> memberIds, String payload) {
        if (memberIds.isEmpty()) {
            return false;
        }
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
        boolean anyFailed = false;
        for (NotificationChannel channel : channels) {
            if (!sendWithRetry(notification.getId(), channel, event.getEventType().name(), payload)) {
                anyFailed = true;
            }
        }
        return anyFailed;
    }

    private Slice<Long> resolveTargetMemberIds(NotificationEventType eventType, PageRequest pageable) {
        return switch (eventType) {
            case TICKET_OPEN -> settingRepository.findMemberIdsByTicketOpenAlertTrue(pageable);
            case GAME_START -> settingRepository.findMemberIdsByGameStartAlertTrue(pageable);
            case PAYMENT_COMPLETED -> settingRepository.findMemberIdsByPaymentAlertTrue(pageable);
            case CHAT_MENTION -> settingRepository.findMemberIdsByChatMentionAlertTrue(pageable);
        };
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
