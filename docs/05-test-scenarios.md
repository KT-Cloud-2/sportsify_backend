# 테스트 시나리오

> 단위·통합·부하 테스트 시나리오 및 기준을 정의한다.

---

## 1. 단위 테스트

### Member 도메인

| ID | 시나리오 | 검증 항목 |
|----|---------|---------|
| M-01 | 최초 소셜 로그인 시 회원 자동 생성 | members 테이블 email 저장, notification_settings 자동 생성 |
| M-02 | 선호 팀 추가 (priority=1) | member_favorite_teams 1개 생성 |
| M-03 | 동일 팀 중복 등록 시도 | DUPLICATE_FAVORITE_TEAM 409 에러 |
| M-04 | 선호 팀 우선순위 변경 | priority 값 정상 업데이트 |
| M-05 | 선호 팀 삭제 | member_favorite_teams 행 삭제 |

### Ticketing 도메인

| ID | 시나리오 | 검증 항목 |
|----|---------|---------|
| T-01 | 경기 목록 조회 — sportType 필터 | 해당 종목 경기만 반환 |
| T-02 | 좌석 목록 — teamSide=TEAM1 필터 | TEAM1 좌석만 반환 |
| T-03 | AVAILABLE 좌석 예매 성공 | ticket PENDING 생성, seat RESERVED 변경 |
| T-04 | RESERVED 좌석 예매 시도 | SEAT_ALREADY_RESERVED 409 에러 |
| T-05 | 10분 TTL 만료 후 좌석 자동 해제 | seat AVAILABLE 복구, ticket CANCELLED |
| T-06 | 티켓 취소 | seat AVAILABLE 복구, ticket CANCELLED |

### Payment 도메인

| ID | 시나리오 | 검증 항목 |
|----|---------|---------|
| P-01 | 정상 결제 요청·검증 | payment COMPLETED, ticket CONFIRMED |
| P-02 | 금액 위변조 (amount 불일치) | PAYMENT_AMOUNT_MISMATCH 422 에러 |
| P-03 | 동일 idempotency_key 중복 요청 | DUPLICATE_PAYMENT 409 에러 |
| P-04 | 10분 초과 후 결제 — 타임아웃 처리 | payment FAILED, ticket CANCELLED, seat AVAILABLE |
| P-05 | 환불 요청 | payment REFUNDED |

### Chat 도메인

| ID | 시나리오 | 검증 항목 |
|----|---------|---------|
| C-01 | 메시지 80자 초과 전송 | INVALID_REQUEST 400 에러 |
| C-02 | 욕설 포함 메시지 | is_filtered=true, chat_warnings 생성 |
| C-03 | 경고 3회 누적 | 24시간 채팅 금지 적용 |
| C-04 | 응원 액션 (FIRE, CLAP, HEART, FLAG) | type=CHEER 메시지 저장 |

### Notification 도메인

| ID | 시나리오 | 검증 항목 |
|----|---------|---------|
| N-01 | ticket.opened 이벤트 수신 | ticket_open_alert=true 회원에게 발송 |
| N-02 | payment.completed 이벤트 수신 | payment_alert=true 회원에게 발송 |
| N-03 | game.starting 이벤트 수신 | game_start_alert=true 회원에게 발송 |
| N-04 | 채널 비활성화 (is_enabled=false) | 해당 채널 미발송, 나머지 채널 정상 발송 |
| N-05 | 알림 발송 성공 | notification_histories status=SENT |
| N-06 | 알림 발송 실패 | notification_histories status=FAILED |

---

## 2. 통합 테스트

### 예매 → 결제 플로우

```
1. POST /api/tickets/reserve       → ticket PENDING, seat RESERVED
2. POST /api/payments/request      → payment PENDING
3. POST /api/payments/verify       → payment COMPLETED, ticket CONFIRMED
4. GET  /api/tickets/{ticketId}    → status=CONFIRMED 확인
```

### 대기열 → 예매 플로우

```
1. POST /api/tickets/queue/enter   → 대기열 입장
2. GET  /api/tickets/queue/position → 순번 확인
3. WebSocket /topic/queue/{gameId} → 순번 도래 알림 수신
4. POST /api/tickets/reserve       → 예매 진행
```

### 예매 취소 → 좌석 복구 플로우

```
1. POST /api/tickets/{ticketId}/cancel → ticket CANCELLED
2. GET  /api/games/{gameId}/seats      → seat status=AVAILABLE 확인
3. GET  /api/games/{gameId}            → available_seats 1 증가 확인
```

---

## 3. 동시성 테스트

### 동일 좌석 동시 예매

| 시나리오 | 실행 방법 | 합격 기준 |
|---------|---------|---------|
| 100명이 같은 seat_id를 동시에 예매 | JMeter Thread Group 100 | 성공 1건, 나머지 409 |
| Redis 분산 락 검증 | 락 해제 전 재진입 시도 | 락 보유 스레드만 성공 |
| 낙관적 락 (@Version) 검증 | 동시 update 시도 | 충돌 시 재시도 정상 동작 |

---

## 4. 부하 테스트 (JMeter)

### 환경별 목표치

| 시나리오 | 로컬 (M2 Air) | AWS |
|---------|------------|-----|
| 동시 접속 유지 | 300명 / 3분 | 2,000명 / 5분 |
| 티켓 구매 연속 | 100 req/s / 3분 | 500 req/s / 5분 |
| 채팅 메시지 폭주 | 200 msg/s / 2분 | 1,000 msg/s / 3분 |
| 대기열 입장 | 200 req/s 순간 | 1,000 req/s 순간 |

### 합격 기준

| 지표 | 기준 |
|------|------|
| P95 응답 시간 | < 200ms |
| P99 응답 시간 | < 500ms |
| 에러율 | < 1% |
| 좌석 중복 예매 | 0건 |

### JMeter 시나리오 파일

| 파일 | 설명 |
|------|------|
| `tools/jmeter/ticket-purchase.jmx` | 티켓 구매 부하 테스트 |
| `tools/jmeter/chat-load.jmx` | 채팅 동시 접속 테스트 |
| `tools/jmeter/queue-enter.jmx` | 대기열 동시 입장 테스트 |

---

## 5. 테스트 도구

| 도구 | 용도 |
|------|------|
| JUnit 5 | 단위·통합 테스트 |
| Mockito | Mock 객체 |
| TestContainers | PostgreSQL, Redis, Kafka 실제 컨테이너 |
| JMeter | 부하 테스트 |
| Swagger UI | API 수동 테스트 (`http://localhost:8080/swagger-ui.html`) |
| Postman | API 컬렉션 테스트 (`tools/sortsify.postman_collection.json`) |
| WebSocket HTML | WebSocket 수동 테스트 (`tools/websocket-test.html`) |
