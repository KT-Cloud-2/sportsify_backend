package com.sportsify.chat.application.message.service;

import com.sportsify.chat.domain.model.event.EventEnvelope;
import com.sportsify.chat.domain.model.event.EventType;
import com.sportsify.chat.domain.model.event.message.ReadReceiptPayload;
import com.sportsify.chat.domain.repository.ChatRoomMemberRepository;
import com.sportsify.chat.domain.repository.ReadCache;
import com.sportsify.chat.infrastructure.webSocket.ChatEventPublisher;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReadReceiptFlusher {

    private final ReadCache readCache;
    private final ChatRoomMemberRepository chatRoomMemberRepo;
    private final ChatEventPublisher chatEventPublisher;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    @PreDestroy
    public void destroy() {
        flush();
    }

    @Scheduled(fixedDelayString = "${chat.read-receipt.flush-interval-ms:5000}")
    public void flush() {
        try {
            readCache.drainAll().forEach(this::flushOne);
        } catch (IllegalStateException e) {
            log.debug("Read receipt flush skipped: connection unavailable ({})", e.getMessage());
        } catch (Exception e) {
            log.error("Read receipt flush failed", e);
        }
    }

    private void flushOne(ReadCache.ReadEntry entry) {
        try {
            Instant now = Instant.now(clock);
            Boolean updated = transactionTemplate.execute(status ->
                    chatRoomMemberRepo.updateLastReadMessageIfGreater(
                            entry.roomId(), entry.memberId(),
                            entry.messageId(), now));

            if (Boolean.TRUE.equals(updated)) {
                chatEventPublisher.publishToRoom(entry.roomId().value(),
                        EventEnvelope.of(EventType.READ_RECEIPT, entry.roomId(), now,
                                new ReadReceiptPayload(entry.memberId().value(), entry.messageId().value())));
            }
        } catch (Exception e) {
            log.error("Failed to flush read receipt: entry={}", entry, e);
        }
    }
}
