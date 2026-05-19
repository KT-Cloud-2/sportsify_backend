package com.sportsify.chat.infrastructure.webSocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketSessionRegistry {

    public static final String WS_SESSION_ATTR = "__rawWsSession";
    private static final Duration GRACE_PERIOD = Duration.ofSeconds(30);

    private final ApplicationEventPublisher eventPublisher;
    /**
     * sessionId -> SessionInfo
     */
    private final Map<String, SessionInfo> sessions = new ConcurrentHashMap<>();
    /**
     * sessionId -> raw WebSocketSession
     */
    private final Map<String, WebSocketSession> wsSessions = new ConcurrentHashMap<>();
    /**
     * memberId -> Set<sessionId>
     */
    private final Map<Long, Set<String>> userSessions = new ConcurrentHashMap<>();
    /**
     * roomId -> Set<sessionId>
     */
    private final Map<Long, Set<String>> roomSessions = new ConcurrentHashMap<>();
    private final Clock clock;
    @Lazy
    @Autowired
    @Qualifier("clientInboundChannel")
    private MessageChannel clientInboundChannel;

    public void register(String sid, WebSocketSession ws, Long memberId, String role, Instant tokenExpiresAt, Instant connectedAt) {
        SessionInfo newInfo = new SessionInfo(sid, memberId, role, connectedAt, tokenExpiresAt, null, new ConcurrentHashMap<>());
        SessionInfo old = sessions.put(sid, newInfo);
        if (old != null) {
            removeFromIndexes(old);
        }
        try {
            wsSessions.put(sid, ws);
            userSessions.computeIfAbsent(memberId, _ -> ConcurrentHashMap.newKeySet()).add(sid);
        } catch (Exception e) {
            sessions.remove(sid, newInfo);
            wsSessions.remove(sid);
            throw e;
        }
    }

    public void subscribeRoom(String sid, String subscriptionId, Long roomId) {
        roomSessions.computeIfAbsent(roomId, _ -> ConcurrentHashMap.newKeySet()).add(sid);
        SessionInfo info = sessions.get(sid);
        if (info != null) info.subscribedRooms().put(subscriptionId, roomId);
    }

    public void unsubscribeRoom(String sid, String subscriptionId) {
        SessionInfo info = sessions.get(sid);
        if (info == null) return;
        Long roomId = info.subscribedRooms().remove(subscriptionId);
        if (roomId == null) return;
        roomSessions.computeIfPresent(roomId, (_, set) -> {
            set.remove(sid);
            return set.isEmpty() ? null : set;
        });
    }

    public void forceDisconnect(String sid, String reason) {
        if (sessions.get(sid) == null) return;
        WebSocketSession ws = wsSessions.get(sid);
        if (ws == null || !ws.isOpen()) {
            onSessionEnded(sid);
            return;
        }
        try {
            ws.close(CloseStatus.POLICY_VIOLATION.withReason(reason));
            onSessionEnded(sid);
        } catch (Exception e) {
            log.warn("Failed to close session sid={}", sid, e);
            onSessionEnded(sid);
        }
    }

    public void revokeUser(Long memberId, String reason) {
        List.copyOf(userSessions.getOrDefault(memberId, Set.of()))
                .forEach(sid -> forceDisconnect(sid, reason));
    }

    public void revokeRoomSubscriptionByMember(Long memberId, Long roomId) {
        Set<String> memberSids = userSessions.getOrDefault(memberId, Set.of());
        Set<String> roomSids = roomSessions.getOrDefault(roomId, Set.of());
        List.copyOf(memberSids).stream()
                .filter(roomSids::contains)
                .forEach(sid -> revokeRoomSubscription(sid, roomId));
    }

    public void revokeAllRoomSubscriptions(Long roomId) {
        List.copyOf(roomSessions.getOrDefault(roomId, Set.of()))
                .forEach(sid -> revokeRoomSubscription(sid, roomId));
    }

    public Optional<SessionInfo> get(String sid) {
        return Optional.ofNullable(sessions.get(sid));
    }

    public void enterGracePeriod(String sid) {
        sessions.computeIfPresent(sid, (_, old) ->
                old.graceDeadline() != null ? old : old.withGraceDeadline(Instant.now(clock).plus(GRACE_PERIOD)));
    }

    public void updateExpiry(String sid, Instant newExpiry) {
        sessions.computeIfPresent(sid, (_, old) -> old.withTokenExpiresAt(newExpiry).withGraceDeadline(null));
    }

    /* -------------------- handler  -------------------- */

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        onSessionEnded(StompHeaderAccessor.wrap(event.getMessage()).getSessionId());
    }

    @Scheduled(fixedRate = 60_000)
    public void evictExpiredSessions() {
        Instant now = Instant.now(clock);
        List<String> toNotify = new ArrayList<>();
        List<String> toDisconnect = new ArrayList<>();

        sessions.values().forEach(info -> {
            if (info.graceDeadline() != null && info.graceDeadline().isBefore(now)
                    && info.tokenExpiresAt().isBefore(now)) {
                toDisconnect.add(info.sessionId());
            } else if (info.tokenExpiresAt().isBefore(now) && info.graceDeadline() == null) {
                toNotify.add(info.sessionId());
            }
        });

        toNotify.forEach(sid -> {
            enterGracePeriod(sid);
            eventPublisher.publishEvent(new TokenExpiredEvent(sid));
        });
        toDisconnect.forEach(sid -> {
            SessionInfo current = sessions.get(sid);
            if (current != null && current.graceDeadline() != null
                    && current.graceDeadline().isBefore(now)
                    && current.tokenExpiresAt().isBefore(now)) {
                forceDisconnect(sid, "Token expired");
            }
        });
    }

    /* -------------------- private functions -------------------- */

    private void revokeRoomSubscription(String sid, Long roomId) {
        SessionInfo info = sessions.get(sid);
        if (info == null) return;
        List<String> subIds = info.subscribedRooms().entrySet().stream()
                .filter(e -> e.getValue().equals(roomId))
                .map(Map.Entry::getKey)
                .toList();
        subIds.forEach(subId -> {
            unsubscribeRoom(sid, subId);             // registry 즉시 정리
            forceUnsubscribeFromBroker(sid, subId);  // broker 구독 해제
        });
        eventPublisher.publishEvent(new RoomSubscriptionRevokedEvent(sid, roomId));
    }

    private void forceUnsubscribeFromBroker(String sid, String subscriptionId) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.UNSUBSCRIBE);
        accessor.setSessionId(sid);
        accessor.setSubscriptionId(subscriptionId);
        accessor.setSessionAttributes(new HashMap<>());
        try {
            clientInboundChannel.send(MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders()));
        } catch (Exception e) {
            log.warn("Failed to force unsubscribe from broker sid={} sub={}", sid, subscriptionId, e);
        }
    }

    private void removeFromIndexes(SessionInfo info) {
        userSessions.compute(info.memberId(), (_, set) -> {
            if (set == null) return null;
            set.remove(info.sessionId());
            return set.isEmpty() ? null : set;
        });
        info.subscribedRooms().values().forEach(roomId ->
                roomSessions.compute(roomId, (_, set) -> {
                    if (set == null) return null;
                    set.remove(info.sessionId());
                    return set.isEmpty() ? null : set;
                })
        );
    }

    private void onSessionEnded(String sid) {
        SessionInfo info = sessions.remove(sid);
        if (info == null) return;
        try {
            wsSessions.remove(sid);
            removeFromIndexes(info);
        } catch (Exception e) {
            log.warn("Failed to clean up indexes for sid={}", sid, e);
        }
    }

    /* -------------------- record -------------------- */

    public record SessionInfo(
            String sessionId,
            Long memberId,
            String role,
            Instant connectedAt,
            Instant tokenExpiresAt,
            Instant graceDeadline,
            ConcurrentHashMap<String, Long> subscribedRooms
    ) {
        public Authentication toAuthentication() {
            return new UsernamePasswordAuthenticationToken(
                    memberId, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        }

        public SessionInfo withTokenExpiresAt(Instant expiresAt) {
            return new SessionInfo(sessionId, memberId, role, connectedAt, expiresAt, graceDeadline, subscribedRooms);
        }

        public SessionInfo withGraceDeadline(Instant deadline) {
            return new SessionInfo(sessionId, memberId, role, connectedAt, tokenExpiresAt, deadline, subscribedRooms);
        }
    }
}
