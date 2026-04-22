# Sortsify

스포츠 응원 문화 + 티케팅 통합 백엔드 시스템

---

## 프로젝트 소개

스포츠 경기 예매 + 팀 응원 채팅 서비스

- 기본 단계: 회원 관리, 경기 예매, 결제, 실시간 응원 채팅, 알림
- 심화 단계: 동영상 라이브스트리밍 연동

---

## 차별화 전략

- **차별화 방향:** 스포츠 티켓 예매와 실시간 응원 채팅을 하나의 플랫폼으로 통합
- **목표:**
    - 티켓 구매부터 실시간 응원까지, 스포츠 관람 경험을 하나의 서비스로 완결
    - 동시 접속자 급증 등 고부하 상황에서도 안정적으로 동작하는 시스템 설계
    - 응원 수, 잔여 좌석, 예매 현황 등 실시간 집계 데이터 활용
  

## 기술 스택

| 분류 | 기술 |
|------|------|
| Language | Java 25 |
| Framework | Spring Boot 4.0.3 |
| Database | PostgreSQL, Redis |
| Message | Kafka |
| Realtime | WebSocket (STOMP) |
| Infra | Docker, AWS |

---

## 핵심 기술 챌린지

- **동시성 제어 3종 비교**: 비관적 락 vs 낙관적 락 vs Redis 분산 락
- **대기열 시스템**: Redis Sorted Set 기반 FIFO 대기열
- **실시간 채팅**: WebSocket + Redis Pub/Sub, 초당 1,000건 처리
- **이벤트 드리븐**: Kafka 기반 도메인 간 느슨한 결합
- **캐싱 3단 구조**: Caffeine(L1) → Redis(L2) → DB(L3)

---

## 실행 방법

```bash
# 인프라 실행
docker compose up -d

# 애플리케이션 실행
./gradlew bootRun
```

Swagger: `http://localhost:8080/swagger-ui.html`

---

## 팀 구성

| 이름 | 도메인 |
|------|--------|
| 오예진 | Member |
| 손하영 | Ticketing |
| 유창민 | Payment |
| 주병규 | Chat |
| 강정훈 | Notification |

---

## 문서

| 문서 | 설명 |
|------|------|
| [기획안](docs/01-project-overview.md) | 프로젝트 목표, 핵심 기능, 개발 일정, KPI |
| [ERD](docs/02-erd.md) | 도메인별 엔티티 및 관계, Redis 키 구조 |
| [팀 규칙](docs/03-team-rules.md) | 커밋 컨벤션, 브랜치 전략, 코드 스타일, 패키지 구조 |
| [API 명세서](docs/04-api-spec.md) | 전 도메인 REST API 및 WebSocket 명세 |
| [테스트 시나리오](docs/05-test-scenarios.md) | 단위·통합·부하 테스트 시나리오 |
| [백엔드 개발 문서](docs/06-backend-dev.md) | 환경 설정, 핵심 구현 전략, 도메인별 가이드 |


-----



testtest






