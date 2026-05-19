# 시나리오 테스트 계획

> 작성자: 강정훈 | 최종 수정: 2026-05-19

---

## 개요

단위/통합 테스트가 개별 컴포넌트를 검증하는 것과 달리, 시나리오 테스트는
**실제 사용자 여정을 하나의 흐름으로** 검증한다.

- 성공 케이스 5개만 다룬다 (실패 케이스는 단위/통합 테스트에서 커버)
- 외부 I/O(`TossPaymentClient`, `JavaMailSender`)는 `@MockitoBean`으로 격리
- 나머지(DB, Redis, 알림 Stream)는 실제 Testcontainers 사용
- `@Tag("external")`로 마킹 → 일반 `./gradlew test`에서 제외, `./gradlew externalTest`로 수동 실행

---

## 테스트 계층 전체 구조

```
단위 테스트        도메인 로직만                Spring 없음
슬라이스 테스트    WebMvcTestSupport 상속       @WebMvcTest, Service는 Mock
통합 테스트        ApiTestSupport 상속          도메인별, 전체 context + 실제 DB
시나리오 테스트    ScenarioTestSupport 상속     여러 도메인 관통, 외부 I/O만 Mock  ← @Tag("external")
```

---

## 성능 및 메모리 최적화 전략

> 근거: [Spring TestContext Caching 공식 문서](https://docs.spring.io/spring-framework/reference/testing/testcontext-framework/ctx-management/caching.html),
> [Testcontainers Manual Lifecycle 공식 문서](https://java.testcontainers.org/test_framework_integration/manual_lifecycle_control/)

### 1. Spring ApplicationContext 1개 공유

Spring TestContext는 캐시 키가 동일하면 컨텍스트를 재사용한다.
**캐시 키가 깨지는 주요 원인: `@MockitoBean` 대상이 클래스마다 다를 때.**

해결책: 모든 시나리오 테스트가 `ScenarioTestSupport` 하나를 상속.
`@MockitoBean`을 베이스 클래스에 집중 선언 → 컨텍스트 1개로 5개 시나리오 전부 실행.

```java
@SpringBootTest
@ActiveProfiles("test")
@Import(TestContainersConfig.class)
@Tag("external")
@TestClassOrder(ClassOrderer.OrderAnnotation.class)
@Sql(scripts = "classpath:db/scenario/seed.sql")   // 참조 데이터 멱등 적재
public abstract class ScenarioTestSupport {

    // 외부 I/O 전부 여기에 집중 선언 → 캐시 키 통일
    @MockitoBean protected TossPaymentClient tossPaymentClient;
    @MockitoBean protected JavaMailSender mailSender;

    @BeforeEach
    void cleanUp(@Autowired JdbcTemplate jdbc) {
        jdbc.execute("""
            TRUNCATE notifications, notification_events, notification_history,
                     payments, orders, order_seats,
                     notification_settings, notification_channels,
                     member_favorite_teams, members
            RESTART IDENTITY CASCADE
        """);
    }
}
```

### 2. DB 상태 격리 — `@BeforeEach` TRUNCATE

시나리오 테스트는 `@Transactional` 롤백 불가 (각 단계가 커밋되어야 다음 단계에서 조회 가능).
`@BeforeEach`에서 명시적 TRUNCATE로 격리한다.

`game_seats`, `games`, `seats` 등 참조 데이터는 TRUNCATE 대상 제외.
대신 `@Sql(seed.sql)`에서 매 테스트 전 상태를 초기화한다.

```sql
-- src/test/resources/db/scenario/seed.sql
-- 매 테스트 메서드 전 실행, ON CONFLICT DO NOTHING으로 멱등 보장
INSERT INTO games (...) VALUES (1, ..., 'ON_SALE', ...) ON CONFLICT DO NOTHING;
INSERT INTO game_seats (...) VALUES (1, 2, 'AVAILABLE', 50000) ON CONFLICT DO NOTHING;

-- 이전 테스트의 RESERVED/SOLD 상태 복구
UPDATE game_seats SET seat_status = 'AVAILABLE' WHERE game_id = 1 AND seat_id IN (2, 3);
```

### 3. Testcontainers 재사용 — 로컬 & CI

`TestContainersConfig`에 이미 `withReuse(true)` 적용되어 있음.
로컬에서는 자동 재사용, **CI(GitHub Actions)에서는 아래 설정 추가 필요:**

```yaml
# .github/workflows/ci.yml
- name: Enable Testcontainers reuse
  run: echo "testcontainers.reuse.enable=true" >> ~/.testcontainers.properties
```

효과: 컨테이너 기동 시간 20초 → 재사용 시 2초 이하.

### 4. `@DirtiesContext` 금지

컨텍스트를 버리고 새로 올리는 `@DirtiesContext`는 사용하지 않는다.
상태 격리는 전부 `@BeforeEach` TRUNCATE로 처리한다.

---

## 외부 I/O Mock 처리 방식

| 외부 시스템 | 클래스 | Mock 처리 | 이유 |
|---|---|---|---|
| Toss Payments | `TossPaymentClient` | `@MockitoBean` | 실제 API 키 없음, 외부 네트워크 의존 |
| 이메일 SMTP | `JavaMailSender` | `@MockitoBean` | 실제 메일 서버 불필요 |
| MQTT 브로커 | `MqttNotificationSender` | `@ConditionalOnBean` 비활성 | `mqtt.enabled: false` (application-test.yml) |
| OAuth2 | — | DB 직접 insert + JWT 발급 | Provider 연동 불가 |
| Toss 결제 확정 | `PaymentService.confirmPaymentMock()` | 서비스 직접 호출 | 컨트롤러 엔드포인트 미존재 |

---

## 공통 제약

| 항목 | 내용 |
|---|---|
| `@Transactional` 금지 | 각 단계가 커밋되어야 다음 단계에서 DB 조회 가능 |
| `@TestMethodOrder` 필수 | 단계 순서 보장 |
| 병렬 실행 금지 | DB 상태 공유 → 시나리오 클래스 간 순차 실행 |
| 알림 비동기 대기 | Redis Stream Consumer 처리 대기 → `Awaitility` 최대 5초 |
| 참조 데이터 | `src/test/resources/db/scenario/seed.sql`로 테스트 DB에 직접 적재 (R__sample_data.sql 무관) |

---

## 회원 준비 전략

OAuth2는 외부 provider 연동이 필요하므로 직접 호출 불가.
`@BeforeEach`에서 DB에 직접 저장 후 `JwtProvider`로 토큰 발급한다.

```java
@BeforeEach
void setUpMember() {
    Member member = createMember(memberRepository, "test@test.com", "kakao-test-001");
    memberId = member.getId();
    accessToken = bearerToken(memberId);
}
```

`cleanUp()`이 `members`를 TRUNCATE한 뒤 `setUpMember()`가 실행되므로 매 테스트마다 깨끗한 회원 상태가 보장된다.
(JUnit 5: 부모 클래스 `@BeforeEach` → 자식 클래스 `@BeforeEach` 순서 보장)

---

## 시나리오 목록

| 순서 | 시나리오 | 파일 | 관통 도메인 | 상태 |
|---|---|---|---|---|
| 1 | 티켓 구매 전체 여정 | `TicketPurchaseScenarioTest` | 경기·예매·결제·알림 | 구현 완료 |
| 2 | 티켓팅 오픈 알림 수신 | `TicketOpenNotificationScenarioTest` | 알림·Redis Stream | 구현 완료 |
| 3 | 결제 취소 후 좌석 복구 | `PaymentCancelScenarioTest` | 결제·예매 | 구현 완료 |
| 4 | 채팅방 입장 후 메시지 전송 | `ChatMessageScenarioTest` | 채팅 | 구현 완료 |
| 5 | 경기 시작 알림 수신 | `GameStartNotificationScenarioTest` | 알림·Redis Stream | 구현 완료 |

---

## 시나리오 1 — 티켓 구매 전체 여정 ★

**파일:** `TicketPurchaseScenarioTest.java`
**관통 도메인:** 경기 → 예매 → 결제 → 알림

```
[회원 준비] @BeforeEach — DB insert + JWT 발급
     │
     ├─ 1. GET  /api/games                      경기 목록 조회 (ON_SALE 경기 존재 확인)
     ├─ 2. GET  /api/games/{gameId}/seats        좌석 조회 (AVAILABLE 좌석 존재 확인)
     ├─ 3. POST /api/seats/reservations          좌석 예약 → orderId 추출
     ├─ 4. POST /api/payments                    결제 생성 → paymentId, tossOrderId 추출
     ├─ 5. [결제 확정] paymentService.confirmPaymentMock() 직접 호출
     └─ 6. GET  /api/notifications               PAYMENT_COMPLETED 알림 수신 확인 (Awaitility 5s)
```

| 단계 | 검증 포인트 |
|---|---|
| 경기 조회 | ON_SALE 경기 1건 이상 |
| 좌석 조회 | AVAILABLE 좌석 1건 이상 |
| 좌석 예약 | orderId 반환, 좌석 RESERVED 전환 |
| 결제 생성 | paymentId, tossOrderId 반환 |
| 결제 확정 | status = COMPLETED |
| 알림 수신 | PAYMENT_COMPLETED 타입 1건 이상, 5초 이내 |

> `confirmPaymentMock()`은 TossPaymentClient를 호출하지 않고 직접 COMPLETED 처리하는 서비스 메서드.
> 컨트롤러 엔드포인트가 없으므로 서비스 직접 주입으로 호출한다.

---

## 시나리오 2 — 티켓팅 오픈 알림 수신

**파일:** `TicketOpenNotificationScenarioTest.java`
**관통 도메인:** 알림 설정 → Redis Stream → 인박스

```
[회원 준비] @BeforeEach — DB insert + JWT 발급
     │
     ├─ 1. PUT /api/notifications/settings       ticketOpenAlert: true 설정
     ├─ 2. [이벤트 발행] NotificationEventPublisher.publish(TICKET_OPEN, payload)
     └─ 3. GET /api/notifications                TICKET_OPEN 수신 확인 (Awaitility 5s)
```

| 단계 | 검증 포인트 |
|---|---|
| 알림 설정 | 200 OK |
| 이벤트 발행 | Redis Stream에 메시지 적재 |
| 알림 수신 | TICKET_OPEN 타입 1건 이상, 5초 이내 |

> `NotificationEventPublisher` 직접 주입해 발행.
> `JavaMailSender`는 `@MockitoBean` 처리 — 실제 메일 발송 없음.

---

## 시나리오 3 — 결제 취소 후 좌석 복구

**파일:** `PaymentCancelScenarioTest.java`
**관통 도메인:** 예매 → 결제 → 취소 → 좌석 상태

```
[회원 준비] @BeforeEach — DB insert + JWT 발급
     │
     ├─ 1. GET  /api/games/{gameId}/seats        AVAILABLE 좌석 확인
     ├─ 2. POST /api/seats/reservations          좌석 예약 → orderId 추출
     ├─ 3. POST /api/payments                    결제 생성 → paymentId 추출
     ├─ 4. [결제 확정] paymentService.confirmPaymentMock() 직접 호출
     ├─ 5. POST /api/payments/{paymentId}/cancel 결제 취소 (tossPaymentClient.cancel() mock)
     └─ 6. GET  /api/games/{gameId}/seats        좌석 AVAILABLE 복구 확인
```

| 단계 | 검증 포인트 |
|---|---|
| 결제 취소 | status = CANCELED |
| 좌석 복구 | 해당 seatId 상태 AVAILABLE 전환 |

> 취소 이벤트 흐름: `PaymentCancelledEvent` → `PaymentEventListener.onPaymentCancelled()` → `SeatStatus.AVAILABLE`
> `tossPaymentClient.cancel()`은 `willDoNothing()` mock — 실제 Toss API 호출 없음.

---

## 시나리오 4 — 채팅방 입장 후 메시지 전송

**파일:** `ChatMessageScenarioTest.java`
**관통 도메인:** 채팅방 → 입장 → 메시지

**전제조건:**
Game 생성 Controller/Service 미구현 상태. 운영자가 DB에 직접 INSERT하는 것으로 가정.
`@BeforeEach`에서 seed.sql의 game_id=1에 연결된 ChatRoom을 `ChatIntegrationTestFixture`로 직접 생성.

```
[사전 준비] @BeforeEach — game_id=1 기준 ChatRoom DB 직접 insert
[회원 준비] @BeforeEach — user1, user2 DB insert + JWT 발급
     │
     ├─ 1. GET  /api/chat/rooms/game/{gameId}         game 기준 채팅방 조회 → roomId 추출
     ├─ 2. POST /api/chat/rooms/{roomId}/join         user1 입장
     ├─ 3. POST /api/chat/rooms/{roomId}/join         user2 입장
     ├─ 4. [메시지 전송] MessageService.send() 직접 호출 (user1)
     └─ 5. GET  /api/chat/messages/history/{roomId}   user2 관점에서 메시지 조회
```

**실제 서비스 채팅 전체 여정 (참고용):**
```
login
  → GET  /api/chat/rooms/game/{gameId}   게임방 조회
  → POST /api/chat/rooms/{roomId}/join   입장
  → SOCKET 연결 및 SUBSCRIBE
  → SEND
```

시나리오 테스트에서 STOMP SEND는 `MessageService.send()` 직접 호출로 대체한다.
`StompAuthChannelInterceptor` + `WebSocketSessionRegistry`가 실제 TCP 연결을 요구하기 때문.

| 단계 | 검증 포인트 |
|---|---|
| 채팅방 조회 | game 기준 roomId 반환 |
| 채팅방 입장 | memberId, roomId 응답 일치 |
| 메시지 전송 | messageId 반환 (not null) |
| 메시지 조회 | user1이 보낸 메시지 1건 이상 존재 |

---

## 시나리오 5 — 경기 시작 알림 수신

**파일:** `GameStartNotificationScenarioTest.java`
**관통 도메인:** 알림 설정 → Redis Stream → 인박스

```
[회원 준비] @BeforeEach — DB insert + JWT 발급
     │
     ├─ 1. PUT /api/notifications/settings       gameStartAlert: true 설정
     ├─ 2. [이벤트 발행] NotificationEventPublisher.publish(GAME_START, payload)
     └─ 3. GET /api/notifications                GAME_START 수신 확인 (Awaitility 5s)
```

| 단계 | 검증 포인트 |
|---|---|
| 알림 설정 | 200 OK |
| 알림 수신 | GAME_START 타입 1건 이상, 5초 이내 |

---

## 파일 구조

```
src/test/
  ├── java/com/sportsify/scenario/
  │   ├── ScenarioTestSupport.java                ← 베이스 클래스 (컨텍스트 공유 핵심)
  │   ├── TicketPurchaseScenarioTest.java          ← @Order(1)
  │   ├── TicketOpenNotificationScenarioTest.java  ← @Order(2)
  │   ├── PaymentCancelScenarioTest.java           ← @Order(3)
  │   ├── ChatMessageScenarioTest.java             ← @Order(4)
  │   └── GameStartNotificationScenarioTest.java   ← @Order(5)
  └── resources/db/scenario/
      └── seed.sql                                ← 참조 데이터 (game, seat, game_seats)
```

---

## 작업 현황 및 의존성

| 시나리오 | 상태 | 비고 |
|---|---|---|
| 1. 티켓 구매 전체 여정 | ✅ 구현 완료 | `confirmPaymentMock()` 서비스 직접 호출로 처리 |
| 2. 티켓팅 오픈 알림 | ✅ 구현 완료 | — |
| 3. 결제 취소 좌석 복구 | ✅ 구현 완료 | `tossPaymentClient.cancel()` mock 처리 |
| 4. 채팅 메시지 전송 | ✅ 구현 완료 | game/ChatRoom @BeforeEach 직접 insert, STOMP → MessageService 대체 |
| 5. 경기 시작 알림 | ✅ 구현 완료 | — |

---

## Gradle 실행

```bash
# CI/로컬 기본 빌드 — 시나리오 테스트 제외
./gradlew test

# 시나리오 테스트만 수동 실행
./gradlew externalTest
```

### GitHub Actions CI 설정 추가 필요

```yaml
# .github/workflows/ci.yml 에 아래 step 추가
- name: Enable Testcontainers reuse
  run: echo "testcontainers.reuse.enable=true" >> ~/.testcontainers.properties
```

---

## 팀원별 요청 사항

| 팀원 | 요청 | 상태 |
|---|---|---|
| 유창민 | `PaymentService.confirmPaymentMock()` 서비스 메서드 유지 | ✅ 이미 구현됨, 컨트롤러 불필요 |
| 주병규 | `ChatIntegrationTestFixture.createRoom(gameId)` 오버로드 추가 | ✅ 시나리오 테스트에서 직접 추가 |
| 손하영 | seed.sql에 ON_SALE 경기 + AVAILABLE 좌석 확인 | ✅ `src/test/resources/db/scenario/seed.sql`로 해결 |
