package com.sportsify.chat.infrastructure.webSocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.TemporalAmount;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class WebSocketSessionRegistry {

    public static final String WS_SESSION_ATTR = "__rawWsSession";
    private final TemporalAmount GRACE_PERIOD = Duration.ofSeconds(30);
    /**
     * WebSocketSession.getAttributes() 에 자기 자신을 보관할 때 쓰는 키
     */
    private final Map<String, SessionInfo> sessions = new ConcurrentHashMap<>();
    /**
     * username(userId) -> sessionId 집합
     */
    private final Map<String, Set<String>> userSessions = new ConcurrentHashMap<>();
    /**
     * roomId -> sessionId 집합
     */
    private final Map<Long, Set<String>> roomSessions = new ConcurrentHashMap<>();
    /**
     * (재연결 유예) username(userId) -> sessionInfo
     */
    private final Map<String, SessionInfo> pendingCleanup = new ConcurrentHashMap<>();

    /**
     * 의도적 강제 종료 대상 마킹
     */
    private final Set<String> hardTerminated = ConcurrentHashMap.newKeySet();

    public void register(String sid, WebSocketSession ws, Authentication authentication, Instant tokenExpiresAt, Instant connectedAt) {
        Set<Long> restoredRooms = ConcurrentHashMap.newKeySet();
        SessionInfo pending = pendingCleanup.remove(authentication.getName());
        if (pending != null) {
            restoredRooms.addAll(pending.subscribedRooms());
            restoredRooms.forEach(roomId ->
                    roomSessions.computeIfAbsent(roomId, _ -> ConcurrentHashMap.newKeySet()).add(sid));
        }
        sessions.put(sid, new SessionInfo(sid, ws, authentication, connectedAt, tokenExpiresAt, restoredRooms));
        userSessions.computeIfAbsent(authentication.getName(), _ -> ConcurrentHashMap.newKeySet()).add(sid);
    }

    public void subscribeRoom(String sid, Long roomId) {
        roomSessions.computeIfAbsent(roomId, _ -> ConcurrentHashMap.newKeySet()).add(sid);
        sessions.computeIfPresent(sid, (_, info) -> {
            info.subscribedRooms().add(roomId);
            return info;
        });
    }

    public void forceDisconnect(String sid, String reason) {
        SessionInfo info = sessions.get(sid);
        if (info == null) return;
        try {
            if (info.wsSession().isOpen()) {
                info.wsSession().close(CloseStatus.POLICY_VIOLATION.withReason(reason));
            }
        } catch (Exception e) {
            log.warn("Failed to close session sid={}", sid, e);
        }
        onSessionEnded(sid);
    }

    public void hardDisconnect(String sid, String reason) {
        hardTerminated.add(sid);
        forceDisconnect(sid, reason);
    }

    public void revokeUser(String username, String reason) {
        List.copyOf(userSessions.getOrDefault(username, Set.of()))
                .forEach(sid -> hardDisconnect(sid, reason));
    }

    public void forceDisconnectByMember(Long memberId, String reason) {
        revokeUser(String.valueOf(memberId), reason);
    }

    public void forceDisconnectByMemberInRoom(Long memberId, Long roomId, String reason) {
        Set<String> memberSids = userSessions.getOrDefault(String.valueOf(memberId), Set.of());
        Set<String> roomSids = roomSessions.getOrDefault(roomId, Set.of());
        List.copyOf(memberSids).stream()
                .filter(roomSids::contains)
                .forEach(sid -> hardDisconnect(sid, reason));
    }

    public void forceDisconnectAllInRoom(Long roomId, String reason) {
        List.copyOf(roomSessions.getOrDefault(roomId, Set.of()))
                .forEach(sid -> hardDisconnect(sid, reason));
    }

    /* -------------------- handler  -------------------- */

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        onSessionEnded(StompHeaderAccessor.wrap(event.getMessage()).getSessionId());
    }

    @Scheduled(fixedRate = 60_000)
    public void evictExpiredSessions() {
        Instant now = Instant.now();
        sessions.values().stream()
                .filter(info -> info.tokenExpiresAt().isBefore(now))
                .toList()
                .forEach(info -> forceDisconnect(info.sessionId(), "Token expired"));
        pendingCleanup.entrySet().stream()
                .filter(e -> e.getValue().tokenExpiresAt().isBefore(now))
                .toList()
                .forEach(e -> pendingCleanup.remove(e.getKey()));
    }

    public Optional<SessionInfo> get(String sid) {
        return Optional.ofNullable(sessions.get(sid));
    }

    public void updateExpiry(String sid, Instant newExpiry) {
        sessions.computeIfPresent(sid, (_, old) -> old.withTokenExpiresAt(newExpiry));
    }

    /* -------------------- private functions -------------------- */


    private void removeFromIndexes(SessionInfo info) {
        String username = info.authentication().getName();
        Set<String> userSet = userSessions.get(username);
        if (userSet != null) {
            userSet.remove(info.sessionId());
            if (userSet.isEmpty()) userSessions.remove(username);
        }
        info.subscribedRooms().forEach(roomId -> {
            Set<String> sids = roomSessions.get(roomId);
            if (sids != null) {
                sids.remove(info.sessionId());
                if (sids.isEmpty()) roomSessions.remove(roomId);
            }
        });
    }

    private void onSessionEnded(String sid) {
        SessionInfo info = sessions.remove(sid);
        if (info == null) return;
        removeFromIndexes(info);
        if (!hardTerminated.remove(sid)) {
            pendingCleanup.put(info.authentication().getName(),
                    info.withTokenExpiresAt(Instant.now().plus(GRACE_PERIOD)));
        }
    }

    /* -------------------- private record -------------------- */

    public record SessionInfo(
            String sessionId,
            WebSocketSession wsSession,
            Authentication authentication,
            Instant connectedAt,
            Instant tokenExpiresAt,
            Set<Long> subscribedRooms  // sessionRooms 맵 대체
    ) {
        public SessionInfo withTokenExpiresAt(Instant expiresAt) {
            return new SessionInfo(sessionId, wsSession, authentication, connectedAt, expiresAt, subscribedRooms);
        }
    }


}
