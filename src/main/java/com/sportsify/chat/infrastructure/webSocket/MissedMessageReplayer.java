package com.sportsify.chat.infrastructure.webSocket;

import com.sportsify.chat.domain.model.chatRoom.ChatRoomId;
import com.sportsify.chat.domain.model.event.EventEnvelope;
import com.sportsify.chat.domain.model.event.EventType;
import com.sportsify.chat.domain.model.event.message.MessageSentPayload;
import com.sportsify.chat.domain.model.message.Message;
import com.sportsify.chat.domain.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class MissedMessageReplayer {

    private static final int REPLAY_LIMIT = 10;

    private final MessageRepository messageRepository;
    private final ChatEventPublisher chatEventPublisher;

    @EventListener
    @Async
    public void onSubscribe(SessionSubscribeEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String destination = accessor.getDestination();
        if (destination == null || !destination.startsWith(ChatEventPublisher.ROOM_TOPIC_PREFIX)) return;

        String lastMessageIdHeader = accessor.getFirstNativeHeader("lastMessageId");
        if (lastMessageIdHeader == null) return;

        String sid = accessor.getSessionId();

        String[] parts = destination.split("/");
        if (parts.length < 4) return;

        long roomId;
        long afterMessageId;
        try {
            roomId = Long.parseLong(parts[3]);
            afterMessageId = Long.parseLong(lastMessageIdHeader);
        } catch (NumberFormatException e) {
            return;
        }

        List<Message> missed = messageRepository.findByRoomAfter(ChatRoomId.of(roomId), afterMessageId, REPLAY_LIMIT + 1);
        if (missed.isEmpty()) return;
        boolean hasMore = missed.size() > REPLAY_LIMIT;
        List<Message> toSend = hasMore ? missed.subList(0, REPLAY_LIMIT) : missed;

        Message lastMessage = toSend.getLast();
        List<EventEnvelope<MessageSentPayload>> envelopes = toSend.stream()
                .map(m -> {
                    EventType type = (hasMore && m == lastMessage) ? EventType.REPLAY_OVERFLOW : EventType.REPLAY_MESSAGE;
                    return EventEnvelope.of(type, m.getRoomId(), m.getCreatedAt(), MessageSentPayload.from(m, null));
                })
                .toList();

        chatEventPublisher.publishReplayToSession(sid, new ReplayBatch(envelopes));

        log.debug("Replayed {} messages to sid={} for roomId={}", toSend.size(), sid, roomId);
    }
}
