package com.sportsify.notification.application.service;

import com.sportsify.common.notification.NotificationEventType;
import com.sportsify.notification.domain.model.NotificationEvent;
import com.sportsify.notification.domain.repository.NotificationSettingRepository;
import com.sportsify.notification.infrastructure.config.NotificationProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FanoutService {

    private final NotificationSettingRepository settingRepository;
    private final ChunkService chunkService;
    private final BufferedChunkService bufferedChunkService;
    private final PayloadParser payloadParser;
    private final NotificationProperties properties;

    public boolean fanout(NotificationEvent event, NotificationEventType eventType, String payload) {
        if (eventType.isSingleTarget()) {
            return fanoutSingleTarget(event, eventType, payload);
        }
        return fanoutBroadcast(event, eventType, payload);
    }

    public void fanoutBuffered(NotificationEvent event, NotificationEventType eventType, String payload,
                               String streamKey, RecordId recordId) {
        if (eventType.isSingleTarget()) {
            fanoutSingleTargetBuffered(event, eventType, payload, streamKey, recordId);
            return;
        }
        fanoutBroadcastBuffered(event, eventType, payload, streamKey, recordId);
    }

    private boolean fanoutSingleTarget(NotificationEvent event, NotificationEventType eventType, String payload) {
        try {
            Long memberId = payloadParser.extractMemberId(payload, event.getTypeName());
            return chunkService.processChunk(event, List.of(memberId), payload);
        } catch (Exception e) {
            log.error("{} payload에서 memberId 추출 실패", event.getTypeName(), e);
            return true;
        }
    }

    private void fanoutSingleTargetBuffered(NotificationEvent event, NotificationEventType eventType, String payload,
                                             String streamKey, RecordId recordId) {
        try {
            Long memberId = payloadParser.extractMemberId(payload, event.getTypeName());
            bufferedChunkService.enqueueChunk(event, List.of(memberId), payload, streamKey, recordId);
        } catch (Exception e) {
            log.error("{} payload에서 memberId 추출 실패 (buffered)", event.getTypeName(), e);
        }
    }

    private boolean fanoutBroadcast(NotificationEvent event, NotificationEventType eventType, String payload) {
        boolean anyFailed = false;
        int page = 0;
        Slice<Long> chunk;

        do {
            chunk = resolveTargetMemberIds(eventType, PageRequest.of(page, properties.fanout().chunkSize()));
            try {
                if (chunkService.processChunk(event, chunk.getContent(), payload)) {
                    anyFailed = true;
                }
            } catch (Exception e) {
                log.error("청크 처리 실패 eventType={} page={}", eventType, page, e);
                anyFailed = true;
            }
            page++;
        } while (chunk.hasNext());

        return anyFailed;
    }

    private void fanoutBroadcastBuffered(NotificationEvent event, NotificationEventType eventType, String payload,
                                          String streamKey, RecordId recordId) {
        int page = 0;
        Slice<Long> chunk;
        do {
            chunk = resolveTargetMemberIds(eventType, PageRequest.of(page, properties.fanout().chunkSize()));
            try {
                bufferedChunkService.enqueueChunk(event, chunk.getContent(), payload, streamKey, recordId);
            } catch (Exception e) {
                log.error("버퍼 청크 enqueue 실패 eventType={} page={}", eventType, page, e);
            }
            page++;
        } while (chunk.hasNext());
    }

    private Slice<Long> resolveTargetMemberIds(NotificationEventType eventType, PageRequest pageable) {
        return switch (eventType) {
            case TICKET_OPEN -> settingRepository.findMemberIdsByTicketOpenAlertTrue(pageable);
            case GAME_START -> settingRepository.findMemberIdsByGameStartAlertTrue(pageable);
            // 새 브로드캐스트 이벤트 추가 시 여기에 케이스 추가 필요
            default -> throw new IllegalStateException("단건 발송 이벤트는 fanoutSingleTarget으로 처리: " + eventType);
        };
    }
}
