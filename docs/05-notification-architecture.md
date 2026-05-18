# 알림 아키텍처 가이드

> Redis를 처음 접하는 분도 읽을 수 있도록 작성했습니다.

---

## 목차

1. [Redis가 뭔가요?](#1-redis가-뭔가요)
2. [Redis Streams가 뭔가요?](#2-redis-streams가-뭔가요)
3. [전체 흐름 한눈에 보기](#3-전체-흐름-한눈에-보기)
4. [단계별 상세 설명](#4-단계별-상세-설명)
5. [알림이 오는 전체 시퀀스](#5-알림이-오는-전체-시퀀스)
6. [NotificationEvent 상태 변화](#6-notificationevent-상태-변화)
7. [발송 재시도 로직](#7-발송-재시도-로직)
8. [코드 구조 한눈에 보기](#8-코드-구조-한눈에-보기)
9. [왜 Kafka 대신 Redis Streams?](#9-왜-kafka-대신-redis-streams)

---

## 1. Redis가 뭔가요?

**Redis = 메모리에 데이터를 저장하는 초고속 저장소**

일반 DB(MySQL 등)는 데이터를 디스크에 저장합니다.  
Redis는 RAM에 저장하기 때문에 **수십~수백 배 빠릅니다.**

```
일반 DB 조회:  디스크 → 메모리 → 응답   (수 ms ~ 수십 ms)
Redis 조회:    메모리 → 응답            (0.1 ms 이하)
```

주로 이런 곳에 씁니다:
- 캐시 (자주 조회되는 데이터 임시 저장)
- 세션 저장
- 메시지 큐 ← **이 프로젝트에서 사용하는 방식**

---

## 2. Redis Streams가 뭔가요?

**Redis Streams = Redis 안에 내장된 메시지 큐**

메시지 큐가 뭔지 먼저 이해해야 합니다.

### 메시지 큐가 없다면?

```
결제 완료
   │
   └──▶ 알림 서버에 직접 API 호출 ──▶ 알림 발송

문제: 알림 서버가 죽어있으면? → 알림 영원히 못 받음
문제: 결제 처리 속도가 알림 발송 속도에 묶임
```

### 메시지 큐가 있다면?

```
결제 완료
   │
   └──▶ 큐에 "결제완료" 메시지 넣기  ← 빠름, 실패 없음
   
               ↕  (나중에 꺼내서 처리)
           
        알림 서버 ──▶ 큐에서 메시지 꺼내기 ──▶ 알림 발송

장점: 결제와 알림이 분리됨 (서로 영향 없음)
장점: 알림 서버가 잠깐 죽어도 메시지는 큐에 보관됨
```

### Redis Streams 구조

Redis Streams는 **로그 파일처럼 메시지가 순서대로 쌓이는 큐**입니다.

```
ticket.opened 스트림 (우편함 이름)
┌──────────────────────────────────────────────────┐
│  1-0  {"gameId": 101, "saleStartAt": "2025-05-01"} │  ← 오래된 메시지
│  2-0  {"gameId": 102, "saleStartAt": "2025-05-02"} │
│  3-0  {"gameId": 103, "saleStartAt": "2025-05-03"} │  ← 최신 메시지
└──────────────────────────────────────────────────┘
         ↑
     메시지 ID (시간-순번 형식)
```

**Consumer Group (소비자 그룹)** 이란?

```
여러 서버(인스턴스)가 같은 큐를 처리할 때 중복 처리를 막는 그룹

스트림: ticket.opened
   │
   └── Consumer Group: notification-group
           ├── Consumer: sportsify-consumer-1  ← 서버 1
           └── Consumer: sportsify-consumer-2  ← 서버 2

규칙: 하나의 메시지는 그룹 안에서 딱 한 서버만 처리
```

**ACK (Acknowledge)** 란?

```
"나 이 메시지 처리 완료했어요" 라고 Redis에 보내는 확인 신호

ACK를 보내지 않으면?
  → Redis가 "아직 처리 중인가봐" 하고 계속 들고 있음
  → 서버가 죽었다 살아나면 다시 처리할 수 있음 (안전망)
```

---

## 3. 전체 흐름 한눈에 보기

```
┌─────────────────────────────────────────────────────────────────────┐
│  이벤트 발생                                                          │
│                                                                     │
│  티켓 오픈 ──────────────────▶ ticket.opened 스트림                 │
│  결제 완료 ──────────────────▶ payment.completed 스트림   (Redis)   │
│  경기 1시간 전 ───────────────▶ game.starting 스트림                │
│  채팅 @멘션 ─────────────────▶ chat.mentioned 스트림               │
└─────────────────────────────────────────┬───────────────────────────┘
                                          │ ① 메시지 쌓임
                                          ▼
┌─────────────────────────────────────────────────────────────────────┐
│  NotificationStreamConsumer (앱 기동 시 4개 스트림 전부 구독)         │
│                                                                     │
│  ② 메시지 꺼내기 → ③ 처리 위임 → ④ ACK 전송                        │
└─────────────────────────────────────────┬───────────────────────────┘
                                          │ ③ process() 호출
                                          ▼
┌─────────────────────────────────────────────────────────────────────┐
│  NotificationEventProcessor (핵심 처리)                              │
│                                                                     │
│  ⑤ DB에 이벤트 기록                                                  │
│  ⑥ "이 알림 켜둔 회원" 목록 조회                                     │
│  ⑦ 회원별 처리                                                       │
│     ├── 알림 인박스에 저장 (나중에 /api/notifications로 조회 가능)    │
│     ├── SSE로 브라우저에 실시간 push                                  │
│     └── 등록된 채널로 발송 (이메일 / MQTT) — 실패 시 최대 3회 재시도  │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 4. 단계별 상세 설명

### ① 다른 도메인이 Redis Streams에 메시지를 넣는다

예시: 결제 도메인에서 결제가 완료되면

```
Redis XADD payment.completed * value '{"paymentId":7001,"memberId":1}'
```

`payment.completed`라는 이름의 스트림에 메시지를 추가합니다.  
알림 도메인은 이 시점에 아무것도 모릅니다. 그냥 큐에 쌓입니다.

---

### ② Consumer Group 초기화 (앱 기동 시 1회)

`RedisStreamsConfig.java`가 앱 시작 시 아래를 실행합니다:

```
4개 스트림 각각에 "notification-group" 이라는 소비자 그룹 생성

ticket.opened     → notification-group 등록
payment.completed → notification-group 등록
game.starting     → notification-group 등록
chat.mentioned    → notification-group 등록
```

이미 그룹이 있으면 조용히 넘어갑니다(에러 아님).

---

### ③ 스트림 구독 등록 (앱 기동 시 1회)

`NotificationStreamConsumer.registerListeners()`가 실행됩니다:

```java
// 4개 스트림에 각각 리스너 등록
container.receive(
    Consumer.from("notification-group", "sportsify-consumer"),
    StreamOffset.create("payment.completed", ReadOffset.lastConsumed()),
    message -> handleMessage(...)  // 메시지 오면 이 메서드 호출
);
```

`ReadOffset.lastConsumed()` = "내가 마지막으로 읽은 것 다음부터 읽겠다"  
→ 서버를 재시작해도 처리했던 메시지를 다시 처리하지 않습니다.

---

### ④ 메시지 처리 & ACK

메시지가 도착하면 `handleMessage()`가 호출됩니다:

```java
private void handleMessage(...) {
    try {
        processor.process(eventType, message.getValue()); // ← 처리
        redisTemplate...acknowledge(...);                  // ← ACK (성공 시에만)
    } catch (Exception e) {
        log.error(...); // ACK 안 보냄 → Redis가 미처리로 인식
    }
}
```

**처리 실패 시 ACK를 보내지 않습니다.**  
→ 해당 메시지는 Redis PEL(미처리 목록)에 계속 남습니다.  
→ 현재는 수동 재처리가 필요한 상태입니다.

---

### ⑤~⑦ NotificationEventProcessor 처리 순서

```
process(PAYMENT_COMPLETED, {"paymentId":7001, "memberId":1})

  1. notification_events 테이블에 저장 (status: PENDING)
  
  2. notification_settings에서
     paymentAlert = true 인 회원 ID 목록 조회
     예) [1, 3, 7, 42]
  
  3. 회원 1번 처리:
     ├── notifications 테이블에 인박스 레코드 저장
     ├── SSE 연결 있으면 → 브라우저에 실시간 push
     └── notification_channels 조회 (이 회원이 등록한 채널들)
         ├── EMAIL: user@example.com → 이메일 발송
         └── MQTT:  device/topic/1  → MQTT 발송
  
  4. 회원 3번, 7번, 42번도 동일하게 처리
  
  5. 전부 성공 → status: PUBLISHED
     하나라도 실패 → status: FAILED
```

---

## 5. 알림이 오는 전체 시퀀스

아래는 "결제 완료 → 이메일 알림 수신" 전체 흐름입니다.

```
결제도메인   Redis Streams   StreamConsumer   EventProcessor   DB      사용자
   │              │               │                │            │        │
   │──XADD───────▶│               │                │            │        │
   │  메시지넣기   │               │                │            │        │
   │              │               │                │            │        │
   │              │──메시지도착───▶│                │            │        │
   │              │               │──process()────▶│            │        │
   │              │               │                │──이벤트저장▶│        │
   │              │               │                │  PENDING   │        │
   │              │               │                │            │        │
   │              │               │                │──설정조회──▶│        │
   │              │               │                │◀─[1,3,7]───│        │
   │              │               │                │            │        │
   │              │               │                │──인박스저장▶│        │
   │              │               │                │            │        SSE
   │              │               │                │────────────────────▶│ 실시간 알림
   │              │               │                │            │        │
   │              │               │                │──이메일발송─────────▶│ 이메일
   │              │               │                │            │        │
   │              │               │                │──히스토리──▶│        │
   │              │               │                │  SENT      │        │
   │              │               │                │            │        │
   │              │               │                │──PUBLISHED▶│        │
   │              │               │◀───────────────│            │        │
   │              │◀──ACK─────────│                │            │        │
```

---

## 6. NotificationEvent 상태 변화

`notification_events` 테이블의 `status` 컬럼이 아래처럼 바뀝니다.

```
            Redis Streams에서 메시지 수신
                        │
                        ▼
                    PENDING ← DB 저장 직후 초기 상태
                   /        \
     모든 회원 발송 성공     한 명이라도 채널 발송 실패
              /                    \
         PUBLISHED               FAILED
      (publishedAt 기록됨)    (재처리 필요)
```

> **참고:** 대상 회원이 아무도 없는 경우(알림 OFF)도 PUBLISHED로 처리됩니다.

---

## 7. 발송 재시도 로직

이메일/MQTT 발송 실패 시 최대 3회 재시도합니다.

```
채널 발송 시도

attempt 1 ──▶ 성공? ──▶ YES → notification_history SENT 저장, 종료
              │
              NO (예외 발생)
              │ warn 로그
              ▼
attempt 2 ──▶ 성공? ──▶ YES → notification_history SENT 저장, 종료
              │
              NO
              │ warn 로그
              ▼
attempt 3 ──▶ 성공? ──▶ YES → notification_history SENT 저장, 종료
              │
              NO
              │ error 로그
              ▼
        notification_history FAILED 저장
        이벤트 status → FAILED
```

---

## 8. 코드 구조 한눈에 보기

```
notification/
├── domain/                         ← 비즈니스 규칙 (순수 Java)
│   ├── model/
│   │   ├── NotificationEvent       → Redis 메시지 1건을 DB에 기록
│   │   ├── NotificationEventType   → 이벤트 종류 + 스트림 키 이름
│   │   ├── Notification            → 회원별 알림 인박스 1건
│   │   ├── NotificationSetting     → 회원별 알림 ON/OFF 설정
│   │   ├── NotificationChannel     → 회원이 등록한 발송 채널
│   │   └── NotificationHistory     → 채널 발송 결과 이력
│   └── repository/                 → DB 접근 인터페이스 (구현은 infra)
│
├── application/                    ← 유스케이스 조합
│   ├── service/
│   │   ├── NotificationEventProcessor   → Redis 메시지 처리 핵심
│   │   ├── NotificationService          → 인박스 조회/읽음 처리
│   │   └── NotificationSettingService   → 설정/채널 관리
│   ├── sender/
│   │   └── NotificationSender      → 발송 인터페이스 (이메일/MQTT 추상화)
│   └── port/
│       └── SseNotificationPort     → SSE 추상화 (infra 직접 의존 방지)
│
├── infrastructure/                 ← 외부 기술 연동
│   ├── config/
│   │   ├── RedisStreamsConfig       → Consumer Group 생성 + Container Bean
│   │   └── MqttConfig              → MQTT 클라이언트 설정
│   ├── consumer/
│   │   └── NotificationStreamConsumer  → Redis 스트림 구독 + ACK
│   ├── sender/
│   │   ├── EmailNotificationSender → JavaMailSender 구현
│   │   └── MqttNotificationSender  → MqttClient 구현
│   ├── sse/
│   │   └── SseEmitterManager       → SseNotificationPort 구현
│   └── repository/                 → JPA 어댑터
│
└── presentation/                   ← HTTP API
    ├── controller/
    │   └── NotificationController  → REST 엔드포인트
    └── dto/                        → Request/Response 객체
```

---

## 9. 왜 Kafka 대신 Redis Streams?

두 기술 모두 메시지 큐입니다. 차이는 아래와 같습니다.

| 항목 | Redis Streams | Kafka |
|------|--------------|-------|
| 운영 복잡도 | 낮음 — Redis 하나로 해결 | 높음 — Kafka + ZooKeeper 별도 운영 |
| 처리량 | 초당 수만 건 (중간 규모) | 초당 수백만 건 (대용량) |
| 메시지 보존 | 메모리 기반 (장기 보존 부적합) | 디스크 기반 (수주~수개월 보존 가능) |
| 학습 난이도 | 낮음 | 높음 |
| 현재 프로젝트 적합성 | **적합** | 과함 |

이 프로젝트 규모에서 Kafka는 오버엔지니어링입니다.  
Redis는 이미 캐시/세션으로 쓰고 있으므로 인프라 추가 없이 Streams 기능만 활성화하면 됩니다.
