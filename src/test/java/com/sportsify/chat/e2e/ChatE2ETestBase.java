package com.sportsify.chat.e2e;

import com.sportsify.chat.config.ChatIntegrationTestFixture;
import com.sportsify.config.TestContainersConfig;
import com.sportsify.infrastructure.security.JwtProvider;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import javax.crypto.SecretKey;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestContainersConfig.class)
abstract class ChatE2ETestBase {

    protected static final int TIMEOUT_SEC = 10;
    protected static final int NO_EVENT_WAIT_SEC = 3;

    protected final List<StompSession> sessions = new CopyOnWriteArrayList<>();
    private final Map<StompSession, CompletableFuture<Throwable>> errorFutures = new ConcurrentHashMap<>();
    private final Set<StompSession> ackEnabledSessions = ConcurrentHashMap.newKeySet();

    @LocalServerPort
    protected int port;

    @Autowired
    protected JwtProvider jwtProvider;

    @Autowired
    protected ChatIntegrationTestFixture fixture;

    protected RestClient restClient;
    protected WebSocketStompClient stompClient;

    @Value("${jwt.secret}")
    private String jwtSecret;


    @BeforeEach
    void setUp() {
        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new JacksonJsonMessageConverter());
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.afterPropertiesSet();
        stompClient.setTaskScheduler(scheduler);
        restClient = RestClient.create();
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        sessions.forEach(s -> {
            if (s.isConnected()) s.disconnect();
        });
        long deadline = System.currentTimeMillis() + 5_000;
        for (StompSession s : sessions) {
            while (s.isConnected() && System.currentTimeMillis() < deadline) {
                Thread.sleep(50);
            }
        }
        sessions.clear();
        errorFutures.clear();
        ackEnabledSessions.clear();
        stompClient.stop();
        fixture.deleteAll();
    }

    protected CompletableFuture<Throwable> errorFutureOf(StompSession session) {
        return errorFutures.get(session);
    }

    protected StompSession connect(long memberId) throws Exception {
        StompSession session = sessionConnect(memberId, null, new StompSessionHandlerAdapter() {});
        ackEnabledSessions.add(session);
        return session;
    }

    protected StompSession connectWithJwt(long memberId, String jwt) throws Exception {
        StompSession session = sessionConnect(memberId, jwt, new StompSessionHandlerAdapter() {});
        ackEnabledSessions.add(session);
        return session;
    }

    protected StompSession connectWithErr(long memberId) throws Exception {
        CompletableFuture<Throwable> errorFuture = new CompletableFuture<>();

        StompSessionHandlerAdapter adapter = new StompSessionHandlerAdapter() {
            @Override
            public void handleTransportError(StompSession s, Throwable e) {
                errorFuture.complete(e);
            }
        };

        StompSession session = sessionConnect(memberId, null, adapter);
        errorFutures.put(session, errorFuture);
        return session;
    }

    private StompSession sessionConnect(
            long memberId,
            String jwt,
            StompSessionHandlerAdapter adapter
    ) throws Exception {
        StompHeaders headers = new StompHeaders();
        String token = jwt != null ? jwt : jwtProvider.createAccessToken(memberId, "USER");
        headers.add("Authorization", "Bearer " + token);

        StompSession session = stompClient.connectAsync(
                wsUrl(),
                new WebSocketHttpHeaders(),
                headers,
                adapter
        ).get(TIMEOUT_SEC, TimeUnit.SECONDS);

        sessions.add(session);
        return session;
    }

    @SuppressWarnings("unchecked")
    protected BlockingQueue<Map<String, Object>> subscribeRoom(StompSession session, long roomId) {
        StompHeaders headers = new StompHeaders();
        headers.setDestination("/topic/rooms/" + roomId);
        return sessionSubscribe(headers, session);
    }


    @SuppressWarnings("unchecked")
    protected BlockingQueue<Map<String, Object>> subscribeRoomWithReplay(
            StompSession session, long roomId, long lastMessageId) {
        StompHeaders headers = new StompHeaders();
        headers.setDestination("/topic/rooms/" + roomId);
        headers.add("lastMessageId", String.valueOf(lastMessageId));
        return sessionSubscribe(headers, session);
    }

    @SuppressWarnings("unchecked")
    protected BlockingQueue<Map<String, Object>> subscribeTyping(StompSession session, long roomId) {
        StompHeaders headers = new StompHeaders();
        headers.setDestination("/topic/rooms/" + roomId + "/typing");
        return sessionSubscribe(headers, session);
    }

    @SuppressWarnings("unchecked")
    protected BlockingQueue<Map<String, Object>> subscribeReplay(StompSession session) {
        StompHeaders headers = new StompHeaders();
        headers.setDestination("/user/queue/replay");
        return sessionSubscribe(headers, session);
    }

    @SuppressWarnings("unchecked")
    protected BlockingQueue<Map<String, Object>> subscribeError(StompSession session) {
        StompHeaders headers = new StompHeaders();
        headers.setDestination("/user/queue/session-errors");
        return sessionSubscribe(headers, session);
    }

    @SuppressWarnings("unchecked")
    protected BlockingQueue<Map<String, Object>> subscribeMessageErrors(StompSession session) {
        StompHeaders headers = new StompHeaders();
        headers.setDestination("/user/queue/errors");
        return sessionSubscribe(headers, session);
    }

    @SuppressWarnings("unchecked")
    private BlockingQueue<Map<String, Object>> sessionSubscribe(StompHeaders headers, StompSession session) {
        BlockingQueue<Map<String, Object>> queue = new LinkedBlockingQueue<>();
        session.subscribe(headers, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders h) {
                return Map.class;
            }

            @Override
            public void handleFrame(StompHeaders h, Object p) {
                queue.offer((Map<String, Object>) p);
            }
        });
        if (ackEnabledSessions.contains(session)) {
            try {
                Map<String, Object> ack = queue.poll(TIMEOUT_SEC, TimeUnit.SECONDS);
                if (ack == null) {
                    throw new IllegalStateException("Subscribe ack timeout for: " + headers.getDestination());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted waiting for subscribe ack");
            }
        }
        return queue;
    }

    protected void sendMessage(StompSession session, long roomId, String content) {
        session.send("/app/chat.send", Map.of(
                "clientMessageId", "cid-" + System.nanoTime(),
                "roomId", roomId,
                "type", "TEXT",
                "content", content
        ));
    }

    protected void restPost(String path, Object body, long memberId) {
        restClient.post()
                .uri(url(path))
                .header("Authorization", "Bearer " + jwtProvider.createAccessToken(memberId, "USER"))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body != null ? body : Map.of())
                .retrieve()
                .toBodilessEntity();
    }

    protected void restPatch(String path, long memberId) {
        restClient.patch()
                .uri(url(path))
                .header("Authorization", "Bearer " + jwtProvider.createAccessToken(memberId, "USER"))
                .retrieve()
                .toBodilessEntity();
    }

    protected StompSession connectAnonymous() throws Exception {
        StompSession session = stompClient.connectAsync(
                wsUrl(), new WebSocketHttpHeaders(), new StompHeaders(),
                new StompSessionHandlerAdapter() {
                }
        ).get(TIMEOUT_SEC, TimeUnit.SECONDS);
        sessions.add(session);
        return session;
    }

    protected String url(String path) {
        return "http://localhost:" + port + path;
    }

    protected String wsUrl() {
        return "ws://localhost:" + port + "/ws/chat";
    }


    protected String createShortLivedToken(long memberId, String role, long expiryMs) {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .subject(String.valueOf(memberId))
                .claim("role", role)
                .claim("type", "access")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiryMs))
                .signWith(key)
                .compact();
    }
}
