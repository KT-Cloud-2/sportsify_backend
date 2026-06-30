# Sportsify

---

> **Sportsify**는 스포츠 경기 예매부터 실시간 응원까지 끊김 없는 팬 경험을 제공하는 플랫폼입니다.  <br />
> 개발 기간: 2026.04.24 ~ 2026.06.24(2달)

## _intro._

---

프로젝트 주요 기능은 다음과 같습니다.

| 기능        | 설명                                        |
|-----------|-------------------------------------------|
| 🎫 티켓 예매  | 경기 및 좌석 선택, 다수 좌석 동시 예약 가능                |
| 💳 결제     | Toss Payments 기반 결제 시스템, 결제 무결성 확보        |
| 💬 실시간 채팅 | WebSocket 기반 실시간 메시지 송수신, 응원 활동 지표        |
| 🔔 스마트 알림 | 예매 완료, 경기 시작 등 주요 이벤트 실시간 알림 (SSE, Email) |

<p align="center">
  <img width="400" height="225" alt="시연_배속_움짤" src="https://github.com/user-attachments/assets/d11d2dd0-6b99-4428-8b7a-7a89d8950db3" />
</p>


<br />

## _Documents._

- [프로젝트 개요](docs/01-project-overview.md)
- [팀 규칙 컨벤샨](docs/03-team-rules.md)
- [API 명세서](docs/04-api-spec.md)

## _ER Diagram._

![ERD](docs/erd_v2.png)

## _Stack._

> backend

- Java25,Spring Boot 4.0.3,
- JWT,Spring Security,Oauth2
- JPA/QueryDSL,
- PostgreSQL18,Redis8
- JUnit5,Mock
- Prometheus, Grafana, JMeter

> Collaborations

- Slack
- Notion

## _SW Architecture._

![aws](docs/aws_a.png)

## _Member._

|                                                               **강정훈**                                                               |                                                             **주병규**                                                              |                                                              **손하영**                                                               |                                                                     **유창민**                                                                     | 
|:-----------------------------------------------------------------------------------------------------------------------------------:|:--------------------------------------------------------------------------------------------------------------------------------:|:----------------------------------------------------------------------------------------------------------------------------------:|:-----------------------------------------------------------------------------------------------------------------------------------------------:| 
| [<img src="https://avatars.githubusercontent.com/u/105915960?v=4" height=130 width=130><br/>  @JHkoder](https://github.com/JHkoder) | [<img src="https://avatars.githubusercontent.com/u/70316489?v=4" height=130 width=130> <br/> @jnj3j3](https://github.com/jnj3j3) | [<img src="https://avatars.githubusercontent.com/u/80742177?v=4" height=130 width=130> <br/> @glosona](https://github.com/glosona) | [<img src="https://avatars.githubusercontent.com/u/268832835?v=4" height=120 width=130> <br/> @dnwn3295-lgtm](https://github.com/dnwn3295-lgtm) | 
|                                                                 알림                                                                  |                                                                채팅                                                                |                                                                티켓팅                                                                 |                                                                       결제                                                                        |

<br />

## _Sequence Diagram._

### 1. 좌석 선점

```
Client        API Server       DB (game_seats / orders)
  │                │                    │
  │──POST /api/seats/reservations──▶│   │
  │                │                    │
  │                │──SELECT game_seats WHERE id IN (...)
  │                │   FOR UPDATE (PESSIMISTIC_WRITE)────▶│
  │                │◀─ [seat1, seat2] ───────────────────│
  │                │  (AVAILABLE 아니면 예외)              │
  │                │                    │
  │                │──INSERT orders (PENDING, expires_at=+15m)────▶│
  │                │──INSERT order_seats (HOLDING)───────────────▶│
  │                │──UPDATE game_seats SET status=RESERVED───────▶│
  │                │──COMMIT (락 해제)───────────────────▶│
  │◀─ 200 orderId ─│                    │
  │                │                    │
  │  (만료 스케줄러) │                    │
  │                │──Scheduler: expires_at < now AND status=PENDING──▶│
  │                │──UPDATE orders SET status=EXPIRED───────────▶│
  │                │──UPDATE game_seats SET status=AVAILABLE──────▶│
```

### 2. 결제

```
Client        API Server (PaymentService)   Toss Payments   DB          EventListener
  │                │                             │           │               │
  │─POST /api/payments─▶│                          │           │               │
  │  {orderId, amount}│                            │           │               │
  │                │──findByIdWithLock(orderId) FOR UPDATE──▶│               │
  │                │  (금액·상태·게임 유효성 검증)  │           │               │
  │                │──INSERT payments (PENDING)──────────────▶│               │
  │◀─ 200 tossOrderId│                            │           │               │
  │                │                             │           │               │
  │─POST /api/payments/confirm──▶│               │           │               │
  │  {tossOrderId, paymentKey, amount}            │           │               │
  │                │──POST /confirm──────────────▶│           │               │
  │                │◀─ 200 DONE ─────────────────│           │               │
  │                │──UPDATE payments SET status=COMPLETED───▶│               │
  │                │──publishEvent(PaymentCompletedEvent)     │               │
  │◀─ 200 ──────────│                             │           │               │
  │                │                             │           │    @TransactionalEventListener(AFTER_COMMIT)
  │                │                             │           │◀──completePayment()────────────│
  │                │                             │           │   ORDER: PENDING→CONFIRMED      │
  │                │                             │           │   ORDER_SEATS: HOLDING→CONFIRMED│
  │                │                             │           │   GAME_SEATS: RESERVED→SOLD     │
  │                │                             │           │◀──createTickets()──────────────│
  │                │                             │           │   INSERT tickets (CONFIRMED)    │
  │                │                             │           │               │
  │  (결제 취소 시)  │                             │           │               │
  │─POST /api/payments/{id}/cancel──▶│           │           │               │
  │                │──Toss cancel API────────────▶│           │               │
  │                │──UPDATE payments SET status=CANCELED────▶│               │
  │                │──publishEvent(PaymentCancelledEvent)     │               │
  │◀─ 200 ──────────│                             │           │               │
  │                │                             │           │◀──cancelPayment()──────────────│
  │                │                             │           │   ORDER: PENDING→CANCELLED      │
  │                │                             │           │   GAME_SEATS: RESERVED→AVAILABLE│
```

### 3. 채팅

```
Client A      WebSocket Server (STOMP)   SessionRegistry   DB (chat_messages)   Client B
  │                │                          │                  │                  │
  │──WS Connect────▶│                          │                  │                  │
  │──SUBSCRIBE /topic/rooms/{id}──▶│           │                  │                  │
  │                │──register(sessionId, subscriptionId)──▶│    │                  │
  │                │                          │                  │                  │
  │──SEND /app/chat.send──▶│                   │                  │                  │
  │  {content, type}│                          │                  │                  │
  │                │──INSERT chat_messages (MessageService)──────▶│                  │
  │                │◀─ messageId ────────────────────────────────│                  │
  │                │──getTargets(roomId)───────▶│                │                  │
  │                │◀─ [sessionId, subscriptionId, ...]──────────│                  │
  │                │──clientOutboundChannel.send() per session───────────────────▶│
  │◀─ ack ──────────│                          │                  │                  │
  │                │                          │                  │                  │
  │  (과거 메시지 조회)│                         │                  │                  │
  │──GET /api/chat/messages/getMessages/{id}──▶│  │                  │                  │
  │                │──SELECT chat_messages WHERE room_id=? ORDER BY id DESC──────▶│
  │◀─ 200 messages ─│                          │                  │                  │
```

### 4. 알림


```
결제도메인   Redis Stream   StreamConsumer   FanoutService   Dispatcher   DB          사용자
   │              │               │                │               │        │            │
   │──XADD───────▶│               │                │               │        │            │
   │  (stream key)│               │                │               │        │            │
   │              │──메시지도착───▶│                │               │        │            │
   │              │               │──saveEvent()───────────────────────────▶│            │
   │              │               │                │               │  INSERT notification_events (PENDING)
   │              │               │                │               │        │            │
   │              │  isScheduled?─│                │               │        │            │
   │              │◀─ACK(예약알림)─│                │               │        │            │
   │              │               │                │               │        │            │
   │              │               │──fanoutBuffered()─▶│           │        │            │
   │              │               │                │ (단건) extractMemberId  │            │
   │              │               │                │ (브로드캐스트) findMemberIds(설정 기반 페이징)
   │              │               │                │──enqueueChunk()──────▶│            │
   │              │               │                │               │        │            │
   │              │               │  (buffer flush)│               │        │            │
   │              │               │                │──toMember()───▶│       │            │
   │              │               │                │               │──INSERT notifications──▶│
   │              │               │                │               │──INSERT notification_histories
   │              │               │                │               │        │            │
   │              │               │                │               │  (afterCommit / virtual thread)
   │              │               │                │               │────────────────────▶│ SSE 실시간 알림
   │              │               │                │               │────────────────────▶│ 이메일
   │              │               │                │               │        │            │
   │              │               │──markEventStatus() → PUBLISHED / FAILED(재시도)      │
   │              │◀──ackAll()────│                │               │        │            │
```

### MVP 핵심 기능

## _Trouble Shooting._

## _Test._

### 1) 트래픽 산정

| **구분**      | **평시**       | **피크시**           |
|-------------|--------------|-------------------|
| DAU         | **3,000명**   | **3,000명**        |
| CCU         | **300명**     | **500명 ~ 1,000명** |
| WebSocket   | 300          | 500 ~ 1000        |
| 예상 Peak TPS | **약 30 TPS** | **167 ~ 333 TPS** |

#### 산정 근거

| **서비스**        | **DAU 추정**   | **근거**     |
|----------------|--------------|------------|
| 인터파크 티켓        | 50~100만 (피크) | 인기 공연 오픈 시 |
| 멜론 티켓          | 10~30만       | 평시 기준      |
| 소규모 스포츠 티켓 플랫폼 | 1~5만         | 지역 리그 수준   |

→ 소규모 기준 3~5만 DAU의 1/20 적용 = **DAU 3,000명** <br />
→ CCU: DAU의 10% = **300명** (채팅 체류시간 고려, 일반 서비스 5%보다 높게 산정)

### 2) 목표 성능 지표

| **시나리오**         | **목표 RPS** | **현실적 TPS** | **성공률** |
|------------------|------------|-------------|---------|
| 일반 부하 (CCU 200)  | 50         | 40~50       | ≥ 99.9% |
| 피크 부하 (CCU 500)  | 200        | 150~200     | ≥ 99.0% |
| 스트레스 (CCU 1,000) | 400        | 200~300     | ≥ 95.0% |

---

## License

This project is for educational purposes.  
© 2025 Sportsify Team (강정훈, 손하영, 유창민, 주병규). All rights reserved.
