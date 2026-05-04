# 알림 도메인 설계 문서

> 상태: 검토 중 (개발 전 검토용)  
> 작성일: 2026-05-04  
> 담당: 강정훈

---

## 1. 개요

스포츠 경기 예매·결제·채팅 이벤트를 Redis Streams로 수신하여 사용자에게 EMAIL / MQTT 두 채널로 알림을 발송하고, SSE 기반 실시간 인박스를 제공하는 도메인.

### 범위 (MVP)

| 항목 | 결정 |
|------|------|
| 발송 채널 | EMAIL(실 구현) + MQTT(실 구현), SLACK은 stub |
| 이벤트 타입 | TICKET_OPEN / GAME_START / PAYMENT_COMPLETED / CHAT_MENTION (4종) |
| 인박스 | SSE 실시간 푸시 + REST API(목록·읽음 처리) |
| 재시도 | 즉시 재시도 최대 3회 → 실패 시 FAILED 기록 |
| 설정 API | 알림 ON/OFF 설정 + 채널 CRUD |

---

## 2. 아키텍처

### 전체 흐름

```
[타 도메인] ──publish──► Redis Streams
                              ↓
              notification/application/consumer/
                   NotificationStreamConsumer       ← Consumer Group ACK
                              ↓
              notification/application/service/
                   NotificationEventProcessor       ← 재시도 3회, Outbox 영속화
                              ↓
              notification/application/sender/
                   NotificationSender (interface)
                   ├── EmailNotificationSender
                   └── MqttNotificationSender
                              ↓
                   notification_history 저장

[클라이언트] ◄──SSE────── SseEmitterManager         ← 인박스 실시간 푸시
[클라이언트] ◄──REST───── NotificationController    ← 목록 조회 / 읽음 처리
                          NotificationSettingController ← 설정·채널 CRUD
```

### 패키지 구조

```
com.sportsify.notification
├── domain/
│   ├── model/
│   │   ├── NotificationEvent.java       # 이벤트 원장 (Outbox)
│   │   ├── NotificationEventType.java   # Enum: TICKET_OPEN | GAME_START | PAYMENT_COMPLETED | CHAT_MENTION
│   │   ├── NotificationEventStatus.java # Enum: PENDING | PUBLISHED | FAILED
│   │   ├── Notification.java            # 사용자 인박스 1건
│   │   ├── NotificationSetting.java     # 사용자별 ON/OFF 설정
│   │   ├── NotificationChannel.java     # 사용자별 채널 등록
│   │   ├── NotificationChannelType.java # Enum: EMAIL | MQTT | SLACK
│   │   └── NotificationHistory.java     # 채널별 발송 이력
│   └── repository/
│       ├── NotificationEventRepository.java
│       ├── NotificationRepository.java
│       ├── NotificationSettingRepository.java
│       ├── NotificationChannelRepository.java
│       └── NotificationHistoryRepository.java
├── application/
│   ├── consumer/
│   │   └── NotificationStreamConsumer.java   # Redis Streams 수신
│   ├── service/
│   │   ├── NotificationEventProcessor.java   # 핵심 처리 로직
│   │   ├── NotificationService.java          # 인박스 조회·읽음 처리
│   │   └── NotificationSettingService.java   # 설정·채널 관리
│   ├── sender/
│   │   ├── NotificationSender.java           # interface
│   │   ├── EmailNotificationSender.java
│   │   └── MqttNotificationSender.java
│   ├── sse/
│   │   └── SseEmitterManager.java            # SSE emitter 등록·관리·푸시
│   └── dto/
│       ├── NotificationResult.java
│       ├── NotificationSettingResult.java
│       └── NotificationChannelResult.java
├── infrastructure/
│   └── repository/
│       ├── NotificationEventJpaRepository.java
│       ├── NotificationEventRepositoryAdapter.java
│       ├── NotificationJpaRepository.java
│       ├── NotificationRepositoryAdapter.java
│       ├── NotificationSettingJpaRepository.java
│       ├── NotificationSettingRepositoryAdapter.java
│       ├── NotificationChannelJpaRepository.java
│       ├── NotificationChannelRepositoryAdapter.java
│       ├── NotificationHistoryJpaRepository.java
│       └── NotificationHistoryRepositoryAdapter.java
└── presentation/
    ├── api/
    │   ├── NotificationApi.java
    │   └── NotificationSettingApi.java
    ├── controller/
    │   ├── NotificationController.java
    │   └── NotificationSettingController.java
    └── dto/
        ├── NotificationResponse.java
        ├── NotificationSettingResponse.java
        ├── UpdateNotificationSettingRequest.java
        ├── NotificationChannelResponse.java
        ├── RegisterChannelRequest.java
        └── NotificationSseResponse.java
```

---

## 3. 도메인 모델

### NotificationEvent (이벤트 원장)

| 필드 | 타입 | 설명 |
|------|------|------|
| id | Long | PK |
| eventType | NotificationEventType | TICKET_OPEN 등 |
| payload | JSONB | 이벤트 상세 데이터 |
| status | NotificationEventStatus | PENDING → PUBLISHED \| FAILED |
| createdAt | LocalDateTime | 생성 시각 |
| publishedAt | LocalDateTime | 처리 완료 시각 |

### Notification (사용자 인박스)

| 필드 | 타입 | 설명 |
|------|------|------|
| id | Long | PK |
| memberId | Long | 수신 대상 |
| eventId | Long | FK → notification_events |
| isRead | boolean | 읽음 여부 |
| createdAt | LocalDateTime | 생성 시각 |

### NotificationSetting (알림 ON/OFF)

| 필드 | 타입 | 설명 |
|------|------|------|
| memberId | Long | PK (1:1) |
| ticketOpenAlert | boolean | 티켓 오픈 알림 |
| gameStartAlert | boolean | 경기 시작 알림 |
| paymentAlert | boolean | 결제 완료 알림 |
| updatedAt | LocalDateTime | 마지막 수정 시각 |

> `CHAT_MENTION`은 별도 설정 없이 항상 발송 (채팅방 알림 설정은 `chat_participants.notification_enabled`로 관리)

### NotificationChannel (채널 등록)

| 필드 | 타입 | 설명 |
|------|------|------|
| id | Long | PK |
| memberId | Long | FK → members |
| channelType | NotificationChannelType | EMAIL \| MQTT \| SLACK |
| channelTarget | String | 이메일 주소 / MQTT clientId 등 |
| isEnabled | boolean | 활성 여부 |

> `UNIQUE(member_id, channel_type)` — 채널 타입별 1개만 허용

---

## 4. Redis Streams 연동

### Consumer Group 설정

| 항목 | 값 |
|------|----|
| Stream Keys | `ticket.opened`, `payment.completed`, `game.starting`, `chat.mentioned` |
| Consumer Group | `notification-group` |
| Consumer Name | `notification-consumer-{instanceId}` |
| ACK 시점 | 발송 처리 완료(PUBLISHED or FAILED) 후 |

### Redis Streams → EventType 매핑

| Stream Key | EventType |
|------------|-----------|
| `ticket.opened` | TICKET_OPEN |
| `payment.completed` | PAYMENT_COMPLETED |
| `game.starting` | GAME_START |
| `chat.mentioned` | CHAT_MENTION |

### Redis 캐시 키

| Key | Type | TTL | 용도 |
|-----|------|-----|------|
| `notification:settings:{memberId}` | Hash | 1시간 | 알림 설정 캐시 |
| `notification:sent:{eventId}:{memberId}` | String | 24시간 | 중복 발송 방지 |
| `notification:connected:{memberId}` | String | 없음 | MQTT 연결 상태 |

---

## 5. 핵심 처리 플로우

### 이벤트 수신 → 발송

```
1. NotificationStreamConsumer가 Redis Streams에서 이벤트 수신
2. NotificationEventProcessor 호출:
   a. notification_events에 PENDING 상태로 저장 (Outbox)
   b. Redis 중복 발송 키 확인 → 이미 존재하면 ACK 후 종료
   c. 이벤트 타입에 맞는 알림 설정(notification_settings) 확인
      → 해당 알림 OFF인 대상 사용자 제외
   d. 대상 사용자 목록 조회
   e. notifications 테이블에 인박스 레코드 생성
   f. SseEmitterManager로 실시간 인박스 푸시 (연결된 경우)
   g. notification_channels 조회 → isEnabled=true인 채널만 선택
   h. NotificationSender(EMAIL/MQTT) 호출 → 최대 3회 즉시 재시도
   i. notification_history 저장 (SENT or FAILED)
   j. notification_events.status → PUBLISHED or FAILED 갱신
   k. Redis Streams ACK
```

### 재시도 전략

```
최대 3회 즉시 재시도:
  1회 시도 → 실패
  2회 시도 → 실패
  3회 시도 → 실패 → notification_history(FAILED) 저장 후 종료

성공 시 즉시 루프 종료 → notification_history(SENT) 저장
```

> 재시도 간격 없음 (즉시 재시도). 외부 서비스(SMTP/MQTT) 장애 시 3회 모두 실패 가능 — 이 경우 FAILED 기록으로 추후 운영자 확인.

### SSE 인박스 푸시

```
연결:    GET /api/v1/notifications/stream
         → SseEmitterManager에 emitter 등록 (key: memberId)
         → Redis: notification:connected:{memberId} 갱신

수신 시: 이벤트 발생 → SseEmitterManager.send(memberId, payload)
         → 미연결 사용자: skip (인박스 DB 레코드는 생성됨)

종료:    연결 끊김 → emitter 제거 → Redis 키 삭제
```

---

## 6. API 명세

### 인박스 API

| Method | Path | 인증 | 설명 |
|--------|------|------|------|
| `GET` | `/api/v1/notifications` | 필요 | 인박스 목록 (페이징, 최신순) |
| `PATCH` | `/api/v1/notifications/{id}/read` | 필요 | 단건 읽음 처리 |
| `PATCH` | `/api/v1/notifications/read-all` | 필요 | 전체 읽음 처리 |
| `GET` | `/api/v1/notifications/stream` | 필요 | SSE 연결 (text/event-stream) |

**GET /api/v1/notifications 응답 예시:**
```json
{
  "content": [
    {
      "id": 1,
      "eventType": "PAYMENT_COMPLETED",
      "message": "결제가 완료되었습니다.",
      "isRead": false,
      "createdAt": "2026-05-04T10:00:00"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 5
}
```

### 알림 설정 API

| Method | Path | 인증 | 설명 |
|--------|------|------|------|
| `GET` | `/api/v1/notifications/settings` | 필요 | 알림 설정 조회 |
| `PUT` | `/api/v1/notifications/settings` | 필요 | 알림 ON/OFF 수정 |
| `GET` | `/api/v1/notifications/channels` | 필요 | 채널 목록 조회 |
| `POST` | `/api/v1/notifications/channels` | 필요 | 채널 등록 |
| `DELETE` | `/api/v1/notifications/channels/{id}` | 필요 | 채널 삭제 |
| `PATCH` | `/api/v1/notifications/channels/{id}/toggle` | 필요 | 채널 활성화/비활성화 |

**PUT /api/v1/notifications/settings 요청 예시:**
```json
{
  "ticketOpenAlert": true,
  "gameStartAlert": false,
  "paymentAlert": true
}
```

**POST /api/v1/notifications/channels 요청 예시:**
```json
{
  "channelType": "EMAIL",
  "channelTarget": "user@example.com"
}
```

---

## 7. 오류 코드

| 코드 | HTTP | 설명 |
|------|------|------|
| `NOTIFICATION_NOT_FOUND` | 404 | 알림 없음 |
| `NOTIFICATION_ALREADY_READ` | 400 | 이미 읽은 알림 |
| `NOTIFICATION_SETTING_NOT_FOUND` | 404 | 알림 설정 없음 |
| `NOTIFICATION_CHANNEL_NOT_FOUND` | 404 | 채널 없음 |
| `NOTIFICATION_CHANNEL_ALREADY_EXISTS` | 409 | 채널 타입 중복 등록 |
| `NOTIFICATION_CHANNEL_TYPE_UNSUPPORTED` | 400 | 지원하지 않는 채널 타입 |
| `NOTIFICATION_SEND_FAILED` | 500 | 발송 실패 (3회 재시도 후) |

---

## 8. 테스트 전략

### 단위 테스트

| 대상 | 검증 시나리오 |
|------|-------------|
| `NotificationEventProcessor` | 알림 설정 OFF 시 skip 처리 |
| `NotificationEventProcessor` | 재시도 3회 후 FAILED 저장, ACK 정상 |
| `NotificationEventProcessor` | 중복 발송 방지 (Redis 키 존재 시 skip) |
| `EmailNotificationSender` | SMTP 호출 성공 시 SENT 반환 |
| `EmailNotificationSender` | SMTP 예외 시 실패 반환 |
| `MqttNotificationSender` | MQTT publish 성공/실패 시나리오 |
| `SseEmitterManager` | emitter 등록·제거·푸시·미연결 시 skip |
| `NotificationSetting` | ticketOpenAlert ON/OFF 도메인 규칙 |

### 통합 테스트 (TestContainers)

| 대상 | 검증 시나리오 |
|------|-------------|
| `NotificationStreamConsumer` | Redis Streams 실 수신 → DB 저장 → ACK 확인 |
| `NotificationRepository` | 인박스 페이징, isRead 필터, 중복 방지 uq_noti |
| `NotificationChannelRepository` | CRUD, uq_nc 제약 조건 위반 시 예외 |
| `NotificationSettingRepository` | 조회·수정, uq_ns_member 제약 확인 |

### API 테스트 (MockMvc)

| 대상 | 검증 시나리오 |
|------|-------------|
| `NotificationController` | 목록 조회 응답 구조, 페이징 파라미터 |
| `NotificationController` | 읽음 처리 상태 변경, 없는 알림 404 |
| `NotificationSettingController` | 설정 수정 @Valid 검증 |
| `NotificationSettingController` | 채널 중복 등록 409 응답 |
| SSE 엔드포인트 | Content-Type: text/event-stream 확인 |

### E2E 시나리오

```
1. 결제 완료 이벤트 Redis Streams publish
2. Consumer 수신 확인
3. notification_events PENDING → PUBLISHED 상태 전환 확인
4. notifications 인박스 레코드 생성 확인
5. EMAIL 발송 확인 (GreenMail TestContainer)
6. MQTT publish 확인 (Mosquitto TestContainer)
7. SSE 연결 클라이언트에 푸시 수신 확인
```

---

## 9. 설계 결정 사항

### D-1. Outbox 패턴 (notification_events 선 저장)
Redis Streams 발행 전 DB에 먼저 저장. Redis 장애 시에도 이벤트 유실 없이 DB 기반 재처리 가능.

### D-2. Strategy 패턴 (NotificationSender interface)
채널별 구현체를 인터페이스로 분리. SLACK 등 채널 추가 시 `NotificationSender` 구현체만 추가하면 `NotificationEventProcessor` 변경 불필요.

### D-3. CHAT_MENTION 설정 예외
`chat_participants.notification_enabled`가 채팅방 단위 알림 제어를 담당하므로, `notification_settings`에 `chatMentionAlert` 필드를 별도로 두지 않음. 멘션 알림은 채팅 도메인에서 이미 필터링된 대상만 Streams에 publish.

### D-4. SSE vs WebSocket
인박스 알림은 단방향(서버→클라이언트)이므로 SSE 채택. WebSocket은 채팅 도메인에서 이미 사용 중 — 혼용 시 연결 관리 복잡도 증가.

### D-5. 즉시 재시도 3회
지수 백오프 없이 즉시 재시도. SMTP/MQTT 일시적 오류(타임아웃 등)에는 효과가 제한적이나, 구현 복잡도를 낮추는 MVP 트레이드오프. 운영 중 빈번한 FAILED 발생 시 지수 백오프로 전환 검토.

---

## 10. 개발 전 체크리스트

- [ ] ERD 확정 (notification 5개 테이블) — `docs/02-erd.md` 기반
- [ ] Flyway 마이그레이션 파일 작성 (`V{N}__add_notification_tables.sql`)
- [ ] Redis Streams Consumer Group 초기화 설정 확인
- [ ] SMTP 설정 (`application-local.yml` GreenMail / `application-prod.yml` 실 SMTP)
- [ ] MQTT Mosquitto 연결 설정 확인 (EC2-2 내부망 `:8883`)
- [ ] `notification_settings` 회원가입 완료 시 모든 알림 ON 기본값으로 자동 생성 (MemberService 내 처리)
- [ ] ErrorCode Enum에 알림 도메인 오류 코드 추가
