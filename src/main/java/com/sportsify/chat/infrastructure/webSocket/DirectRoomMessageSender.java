package com.sportsify.chat.infrastructure.webSocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.SubscribableChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.List;

@Slf4j
@Component
public class DirectRoomMessageSender {

    private static final String DESTINATION_PREFIX = "/topic/rooms/";
    private final WebSocketSessionRegistry registry;
    private final ObjectMapper objectMapper;
    private final SubscribableChannel clientOutboundChannel;
//    private final Executor broadcastExecutor;

    public DirectRoomMessageSender(
            WebSocketSessionRegistry registry,
            ObjectMapper objectMapper,
            SubscribableChannel clientOutboundChannel
//            @Qualifier("broadcastExecutor") Executor broadcastExecutor
    ) {
        this.registry = registry;
        this.objectMapper = objectMapper;
        this.clientOutboundChannel = clientOutboundChannel;
//        this.broadcastExecutor = broadcastExecutor;
    }

    public void sendToRoom(Long roomId, Object payload) {
        String destination = DESTINATION_PREFIX + roomId;
        List<WebSocketSessionRegistry.SessionSendTarget> targets = registry.getTargets(roomId);
        if (targets.isEmpty()) return;

        byte[] serializedPayload;
        try {
            serializedPayload = objectMapper.writeValueAsBytes(payload);
        } catch (Exception e) {
            log.error("[DirectRoomMessageSender] JSON 직렬화 실패 roomId={}", roomId, e);
            return;
        }

        for (WebSocketSessionRegistry.SessionSendTarget target : targets) {
            try {
                HashMap<String, Object> map = new HashMap<>(4);
                map.put(SimpMessageHeaderAccessor.MESSAGE_TYPE_HEADER, SimpMessageType.MESSAGE);
                map.put(SimpMessageHeaderAccessor.DESTINATION_HEADER, destination);
                map.put(SimpMessageHeaderAccessor.SESSION_ID_HEADER, target.sessionId());
                map.put(SimpMessageHeaderAccessor.SUBSCRIPTION_ID_HEADER, target.subscriptionId());
                Message<byte[]> msg = MessageBuilder.createMessage(serializedPayload, new MessageHeaders(map));
                clientOutboundChannel.send(msg);
            } catch (Exception e) {
                log.warn("[DirectRoomMessageSender] 아웃바운드 채널 인입 실패 sid={}, roomId={}", target.sessionId(), roomId, e);
            }
        }
    }

    public void sendToUser(Long userId, String destination, Object payload) {
        List<WebSocketSessionRegistry.SessionQueueTarget> targets = registry.getUserQueueTargets(userId, destination);
        if (targets.isEmpty()) return;

        byte[] serializedPayload;
        try {
            serializedPayload = objectMapper.writeValueAsBytes(payload);
        } catch (Exception e) {
            log.error("[DirectRoomMessageSender] JSON 직렬화 실패 userId={}", userId, e);
            return;
        }

        for (WebSocketSessionRegistry.SessionQueueTarget target : targets) {
            try {
                HashMap<String, Object> map = new HashMap<>(4);
                map.put(SimpMessageHeaderAccessor.MESSAGE_TYPE_HEADER, SimpMessageType.MESSAGE);
                map.put(SimpMessageHeaderAccessor.DESTINATION_HEADER, destination);
                map.put(SimpMessageHeaderAccessor.SESSION_ID_HEADER, target.sessionId());
                map.put(SimpMessageHeaderAccessor.SUBSCRIPTION_ID_HEADER, target.subscriptionId());
                Message<byte[]> msg = MessageBuilder.createMessage(serializedPayload, new MessageHeaders(map));
                clientOutboundChannel.send(msg);
            } catch (Exception e) {
                log.warn("[DirectRoomMessageSender] user 메시지 전송 실패 userId={}, destination={}", userId, destination, e);
            }
        }
    }
}

