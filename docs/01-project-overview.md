# 프로젝트 기획안 — Sortsify

> 스포츠 응원 문화 + 티케팅 통합 시스템

---

## 프로젝트 개요

| 항목       | 내용                     |
|----------|------------------------|
| 개발 기간    | 2개월                    |
| 팀 구성     | 5명 (각자 1개 도메인 담당)      |
| 예산       | AWS 월 5만원 이내           |
| 목표 동시 접속 | 1,000명 (목표 KPI 5,000명) |

### 팀 도메인 담당

| 이름  | 도메인          | 개발 방식                         |
|-----|--------------|-------------------------------|
| 오예진 | Member       | 직접 구현                         |
| 손하영 | Ticketing    | 직접 구현                         |
| 유창민 | Payment      | 직접 구현                         |
| 주병규 | Chat         | 직접 구현                         |
| 강정훈 | Notification | Claude 활용 후 최적화·모니터링·부하테스트 집중 |


---

## 기술 스택

```yaml
Language: Java 25
Framework: Spring Boot 4.0.3
Security: Spring Security + JWT
Database:
  - PostgreSQL  # 주 데이터
  - Redis       # 캐시, 대기열, Pub/Sub, 세션
Message: Kafka (또는 Redis Streams — 비용 절감 고려)
Realtime: WebSocket (STOMP)
APIDocs: Swagger / OpenAPI 3.0
Testing: JUnit 5 + Mockito + TestContainers
Monitoring: Prometheus + Grafana
Logging: ELK Stack
```

---

## 도메인 구조 (DDD 기반 패키징)

```
sortsify/
├── team/         # 공통 마스터 (teams, sport_type)
├── member/       # 회원, member_favorite_teams
├── ticketing/    # games, seats, tickets
├── payment/      # payments
├── chat/         # chat_rooms, chat_messages, chat_warnings
└── notification/ # notification_settings, notification_channels, notification_histories
```

각 도메인은 독립적으로 패키징하며, 도메인 간 의존은 Kafka 이벤트와 자체 DTO를 통해서만 한다.

---

## 개발 일정

| 주차       | 목표                                            | 산출물                    |
|----------|-----------------------------------------------|------------------------|
| Week 1–2 | 기반 구축 (DDD 모델링, DB 스키마, 인프라 설정, JWT, Swagger) | ERD, API 명세서 초안, 개발 환경 |
| Week 3–4 | 도메인별 MVP — 기본 CRUD API                        | 각 도메인 기본 API           |
| Week 5   | 동시성 제어 (분산 락, 대기열, 선점 타임아웃, @Version)         | 티케팅 핵심 로직              |
| Week 6   | 이벤트 통합 (Kafka Producer/Consumer, 응원 배틀)       | 도메인 간 통합               |
| Week 7   | 성능 최적화 (Redis 캐싱, DB 인덱스, N+1 해결)             | 성능 보고서                 |
| Week 8   | 부하 테스트 (JMeter 시나리오, Grafana 대시보드)            | 부하 테스트 리포트             |
| Week 9   | 버그 픽스 / 문서화 / 발표 준비                           | 최종 프로젝트 문서             |

---

## KPI (성공 지표)

| 지표       | 목표          |
|----------|-------------|
| 동시 접속    | 5,000명 이상   |
| 좌석 판매 속도 | 1,000석/분    |
| 채팅 처리량   | 1,000건/초    |
| 평균 응답 시간 | P95 < 200ms |
| 에러율      | < 1%        |
| 테스트 커버리지 | 80% 이상      |

---

## 인프라 구성 (AWS 월 5만원)

| 서비스                        | 사양     | 예상 비용        |
|----------------------------|--------|--------------|
| EC2 t3.small × 2           | 앱 서버   | ~25,000원     |
| db                         | DB 서버  | ?원           |
| ElastiCache cache.t3.micro | Redis  | ~10,000원     |
| S3                         | 이미지 저장 | ~1,000원      |
| Route 53                   | 도메인    | ~1,000원      |
| **합계**                     |        | **~37,000원** |

---

## 미결 결정 사항 (팀 회의 논의)

### 비즈니스

1. Kafka vs Redis Streams — 어느 쪽으로?
2. MongoDB 필요 여부 (채팅 로그 Postgres로 통합 가능)
3. WebSocket 서버 분리 여부

### 우선순위

1. 부하 테스트 목표 동시 접속 수 (500 → 1,000 점진적 증가)
2. 모니터링 범위
