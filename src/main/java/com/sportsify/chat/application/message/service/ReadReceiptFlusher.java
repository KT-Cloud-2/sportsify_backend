package com.sportsify.chat.application.message.service;

import com.sportsify.chat.application.message.config.RedisKeySchema;
import com.sportsify.chat.domain.model.chatRoom.ChatRoomId;
import com.sportsify.chat.domain.model.chatRoom.MemberId;
import com.sportsify.chat.domain.model.event.EventEnvelope;
import com.sportsify.chat.domain.model.event.EventType;
import com.sportsify.chat.domain.model.event.message.ReadReceiptPayload;
import com.sportsify.chat.domain.model.message.MessageId;
import com.sportsify.chat.domain.repository.ChatRoomMemberRepository;
import com.sportsify.chat.infrastructure.webSocket.ChatEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReadReceiptFlusher {


    private final StringRedisTemplate redisTemplate;
    private final ChatRoomMemberRepository chatRoomMemberRepo;
    private final ChatEventPublisher chatEventPublisher;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${chat.read-receipt.flush-interval-ms:5000}")
    public void flush() {
        try (Cursor<String> cursor = redisTemplate.scan(
                ScanOptions.scanOptions().match(RedisKeySchema.SCAN_PATTERN).count(200).build())) {
            cursor.forEachRemaining(this::flushOne);
        } catch (Exception e) {
            log.error("Read receipt flush failed", e);
        }
    }

    private void flushOne(String key) {
        try {
            String raw = redisTemplate.opsForValue().getAndDelete(key);
            if (raw == null) return;

            // key format: chat:read:{roomId}:{memberId}
            String[] parts = key.split(":");
            if (parts.length != 4) return;

            ChatRoomId roomId = ChatRoomId.of(Long.parseLong(parts[2]));
            long memberId = Long.parseLong(parts[3]);
            long messageId = Long.parseLong(raw);
            Instant now = Instant.now(clock);

            Boolean updated = transactionTemplate.execute(status ->
                    chatRoomMemberRepo.updateLastReadMessageIfGreater(
                            roomId, MemberId.of(memberId),
                            MessageId.of(messageId), now));

            if (Boolean.TRUE.equals(updated)) {
                chatEventPublisher.publishToRoom(roomId.value(),
                        EventEnvelope.of(EventType.READ_RECEIPT, roomId, now, new ReadReceiptPayload(memberId, messageId)));
            }
        } catch (NumberFormatException e) {
            log.warn("Skipping malformed read receipt: key={}", key, e);
        } catch (Exception e) {
            log.error("Failed to flush read receipt: key={}", key, e);
        }
    }
}
