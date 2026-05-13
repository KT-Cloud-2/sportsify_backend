package com.sportsify.notification.application.service;

import com.sportsify.common.notification.NotificationEventType;
import com.sportsify.notification.domain.model.NotificationEvent;
import com.sportsify.notification.domain.repository.NotificationSettingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationFanoutService {

    private static final int CHUNK_SIZE = 500;

    private final NotificationSettingRepository settingRepository;
    private final NotificationChunkService chunkService;

    public boolean fanout(NotificationEvent event, NotificationEventType eventType, String payload) {
        if (isSingleTarget(eventType)) {
            return fanoutSingleTarget(event, eventType, payload);
        }
        return fanoutBroadcast(event, eventType, payload);
    }

    private boolean isSingleTarget(NotificationEventType eventType) {
        return eventType == NotificationEventType.CHAT_MENTION
                || eventType == NotificationEventType.PAYMENT_COMPLETED;
    }

    private boolean fanoutSingleTarget(NotificationEvent event, NotificationEventType eventType, String payload) {
        try {
            Long memberId = NotificationPayloadParser.extractMemberId(payload, eventType.name());
            return chunkService.processChunk(event, List.of(memberId), payload);
        } catch (Exception e) {
            log.error("{} payload에서 memberId 추출 실패", eventType.name(), e);
            return true;
        }
    }

    private boolean fanoutBroadcast(NotificationEvent event, NotificationEventType eventType, String payload) {
        boolean anyFailed = false;
        int page = 0;
        Slice<Long> chunk;

        do {
            chunk = resolveTargetMemberIds(eventType, PageRequest.of(page, CHUNK_SIZE));
            if (chunkService.processChunk(event, chunk.getContent(), payload)) {
                anyFailed = true;
            }
            page++;
        } while (chunk.hasNext());

        return anyFailed;
    }

    private Slice<Long> resolveTargetMemberIds(NotificationEventType eventType, PageRequest pageable) {
        return switch (eventType) {
            case TICKET_OPEN -> settingRepository.findMemberIdsByTicketOpenAlertTrue(pageable);
            case GAME_START -> settingRepository.findMemberIdsByGameStartAlertTrue(pageable);
            default -> throw new IllegalStateException("단건 발송 이벤트는 fanoutSingleTarget으로 처리: " + eventType);
        };
    }
}
