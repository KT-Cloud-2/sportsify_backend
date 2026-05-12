package com.sportsify.notification.application.service;

import com.sportsify.common.notification.NotificationEventType;
import com.sportsify.notification.domain.model.NotificationEvent;
import com.sportsify.notification.domain.repository.NotificationSettingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationFanoutService {

    private static final int CHUNK_SIZE = 500;

    private final NotificationSettingRepository settingRepository;
    private final NotificationDispatcher dispatcher;

    public boolean fanout(NotificationEvent event, NotificationEventType eventType, String payload) {
        if (eventType == NotificationEventType.CHAT_MENTION) {
            return fanoutChatMention(event, payload);
        }
        return fanoutBroadcast(event, eventType, payload);
    }

    private boolean fanoutChatMention(NotificationEvent event, String payload) {
        try {
            Long memberId = ChatMentionPayloadParser.extractMemberId(payload);
            return processChunk(event, List.of(memberId), payload);
        } catch (Exception e) {
            log.error("CHAT_MENTION payload에서 memberId 추출 실패 payload={}", payload, e);
            return true;
        }
    }

    private boolean fanoutBroadcast(NotificationEvent event, NotificationEventType eventType, String payload) {
        boolean anyFailed = false;
        int page = 0;
        Slice<Long> chunk;

        do {
            chunk = resolveTargetMemberIds(eventType, PageRequest.of(page, CHUNK_SIZE));
            if (processChunk(event, chunk.getContent(), payload)) {
                anyFailed = true;
            }
            page++;
        } while (chunk.hasNext());

        return anyFailed;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean processChunk(NotificationEvent event, List<Long> memberIds, String payload) {
        if (memberIds.isEmpty()) {
            return false;
        }
        boolean anyFailed = false;
        for (Long memberId : memberIds) {
            if (dispatcher.dispatchToMember(event, memberId, payload)) {
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
            case CHAT_MENTION -> throw new IllegalStateException("CHAT_MENTION은 fanoutChatMention으로 처리");
        };
    }
}
