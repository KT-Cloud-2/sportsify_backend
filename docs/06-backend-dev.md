# 백엔드 개발 문서

> 개발 환경 설정, 핵심 구현 전략, 도메인별 구현 가이드를 기술한다.

---

## 1. 개발 환경 설정

### 필수 도구( 절때 다운그레이드 금지 )

| 도구 | 버전    |
|------|-------|
| Java | 25    |
| Spring Boot | 4.0.3 |
| Gradle | 9.4.1 |
| Docker | 최신    |
| Docker Compose | 최신    |

### 로컬 인프라 실행 (Docker Compose)

```bash
docker compose up -d
```

제공 서비스: PostgreSQL, Redis, Kafka, Zookeeper

### 애플리케이션 실행

```bash
./gradlew bootRun
```

Swagger: `http://localhost:8080/swagger-ui.html`

---

## 2. 인증 (JWT)

- `accessToken`: 만료 30분
- `refreshToken`: 만료 30일
- 소셜 로그인: Spring Security OAuth2 Client (Google, Kakao)
- 직접 회원가입/로그인 없음 — OAuth2 전용
- 최초 로그인 시 `notification_settings` 기본값으로 자동 생성

---

## 3. 핵심 구현 전략

### 3-1. 동시성 제어 (Ticketing)

동일 `seat_id` 에 대한 동시 예매를 방지한다.

| 방식 | 적용 위치 | 설명 |
|------|---------|------|
| Redis 분산 락 | 좌석 선점 | `seat:lock:{seatId}` 키, 10분 TTL |
| 낙관적 락 (`@Version`) | seats 테이블 | 충돌 시 재시도 |
| 비관적 락 | 잔여 좌석 감소 | `available_seats` 동시 업데이트 보호 |

---

### 3-2. 대기열 시스템 (Ticketing)

**대기열 흐름**
1. 티켓 오픈 30분 전 대기열 입장 시작
2. 순번 도래 시 WebSocket 알림
3. 예매 완료 또는 이탈 시 대기열에서 제거

---

### 3-3. 좌석 선점 타임아웃

결제 대기 시 10분 TTL 적용. 만료 시 자동 해제.

---

### 3-4. 이벤트 기반 아키텍처 (Kafka)

**주요 Kafka 토픽**

| 토픽 | 발행 도메인 | 소비 도메인 | 설명 |
|------|-----------|-----------|------|
| `ticket.opened` | Ticketing | Notification | 티켓 오픈 — ticket_open_alert 회원에게 알림 |
| `payment.completed` | Payment | Notification | 결제 완료 — payment_alert 회원에게 알림 |
| `game.starting` | Ticketing | Notification | 경기 시작 1시간 전 — game_start_alert 회원에게 알림 |

**이벤트 유실 방지**
- at-least-once 보장
- Consumer `enable.auto.commit=false`, 수동 커밋

---

### 3-5. 캐싱 전략 (3단 구조)

```
1차: Caffeine (Local Cache)  — 앱 메모리, 가장 빠름
2차: Redis (Shared Cache)    — 멀티 서버 공유, 일관성 보장
3차: DB                      — 캐시 미스 시에만 접근
```

**캐시 적용 대상**

| 대상 | TTL | 비고 |
|------|-----|------|
| 경기 정보 (`cache:game:{gameId}`) | 5분 | team1/team2 포함 전체 응답 캐싱 |
| 좌석 현황 | 30초 | 실시간성 필요, Redis로 충분 |
| 팀 목록 | 10분 | sport_type별 팀 목록 캐싱 |
| 유저 정보 | 로그인 유지 시간 | 로그인 시 캐싱, 선호팀 목록 포함 |

---

### 3-6. 실시간 채팅 (WebSocket + STOMP)

- Spring WebSocket + STOMP
- Redis Pub/Sub으로 멀티 서버 간 메시지 브로드캐스트

**WebSocket 최적화**
1. 연결 풀 관리: 최대 동시 연결 수 제한, 유휴 연결 자동 종료
2. 메시지 배치: 100ms마다 모아서 전송 (네트워크 오버헤드 감소)
3. Redis Pub/Sub: 수평 확장 지원

---

### 3-8. 이벤트 소싱 패턴 (티켓 예매)

티켓 예매 과정의 모든 상태 변화를 이벤트 로그로 기록한다.

```
TicketReserved      → 좌석 선점
PaymentRequested    → 결제 요청
PaymentCompleted    → 결제 완료
TicketIssued        → 티켓 발급
```

중간 실패 시 이벤트 로그로 추적 → 재시도 또는 롤백

---

---

## 6. 모니터링

| 도구 | 용도 |
|------|------|
| Prometheus | 메트릭 수집 |
| Grafana | 대시보드 시각화 |
| ELK Stack | 로그 수집·검색 |
| Actuator | 헬스체크, 메트릭 엔드포인트 |

**주요 모니터링 항목**
- API 응답 시간 (P50, P95, P99)
- 에러율
- Redis 메모리 사용량
- Kafka Consumer Lag
- 동시 WebSocket 연결 수

---

## 7. 로깅 전략

- 라이브러리: SLF4J + Logback
- 요청/응답 로깅: MDC에 `traceId`, `memberId` 포함
- 민감 정보(비밀번호, 카드 번호) 로그 마스킹 필수

---

## 8. 프론트 없이 테스트하는 방법

1. **Swagger UI** — `http://localhost:8080/swagger-ui.html`
2. **Postman Collection** — `tools/sortsify.postman_collection.json`
3. **WebSocket HTML 테스트** — `tools/websocket-test.html`
4. **JMeter 부하 테스트** — `tools/jmeter/` 시나리오 파일

---

## 9. CI/CD

```yaml
# .github/workflows/ci.yml 구성 예시
on: [push, pull_request]
jobs:
  build:
    - ./gradlew test
    - ./gradlew build
  deploy:
    - Docker 이미지 빌드 → EC2 배포 (develop → main merge 시)
```
