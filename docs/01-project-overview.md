# Sortsify — 프로젝트 개요

> 스포츠 경기 예매 + 팀 응원 채팅 서비스

---

## ① 문제 정의 (Problem Statement)

- 스포츠 티켓 예매 시스템과 실시간 응원/채팅 기능은 일반적으로 **서로 다른 시스템으로 분리되어 운영**되어 사용자 경험이 단절됨
- 티켓 오픈 시점에는 특정 경기나 공연에 대해 **짧은 시간 내 대규모 트래픽이 집중되는 구조적 문제(Flash Crowd Problem)**가 발생함
- 기존 예매 시스템은 거래 중심 구조로 설계되어 있어, 실시간 커뮤니티/응원 경험과 자연스럽게 연결되지 않음
- 일부 플랫폼은 스트리밍 기반 채팅 기능을 제공하지만, 이는 예매 시스템과 독립적으로 운영되어 **예매 → 관람 → 응원 흐름의 통합 경험이 부재함**

---

## ② 목표 (Goal / Objective)

- 동시 접속자 **MVP 기준 1,000명 이상 안정 처리**, 목표 확장 5,000명 이상 대응 가능한 구조 설계
- 평균 API 응답 시간 **200ms 이하 유지**
- 실시간 채팅 메시지 전달 지연 **100ms 이하 유지 (WebSocket + Pub/Sub 기준)**
- 티켓 예매 시 좌석 중복 선택을 방지하는 **강한 데이터 일관성 보장 (DB 트랜잭션 + 분산락 활용)**
- 트래픽 증가 시에도 서비스 장애 없이 동작 가능한 **수평 확장 가능한 구조 설계 (Stateless API + Redis 기반 세션/캐시)**

---

## ③ 핵심 기능 (Core Features)

| 도메인 | 기능 | 담당 |
|--------|------|------|
| 회원 | 소셜 로그인(Google/Kakao), JWT 인증, 회원 정보 관리, 선호 팀 관리 | 강정훈 |
| 팀 | 팀 목록/상세 조회, 관리자 팀 등록/수정/비활성화 | 강정훈 |
| 경기/좌석 | 경기 목록/상세/좌석 조회, 관리자 경기 등록·상태 관리 | 손하영 |
| 예매/대기열 | Redis 기반 대기열 SSE 스트리밍, 좌석 선점, 티켓 발급·취소 | 손하영 |
| 결제 | 결제 요청·검증·Webhook 수신, 환불 처리 | 유창민 |
| 채팅 | WebSocket(STOMP) 실시간 채팅, 채팅방 관리, 1:1·그룹 채팅 | 주병규 |
| 알림 | Redis Streams 이벤트 기반 알림, Email/MQTT 채널, 알림 설정 | 강정훈 |
| 인프라 | AWS 인프라 구성, CI/CD, Slack 봇, 모니터링 | 강정훈 |

---

## ④ 기술 스택

> **버전 다운그레이드 절대 금지**

### Backend

| 항목 | 기술 | 버전 |
|------|------|------|
| Language | Java | 25 |
| Framework | Spring Boot | 4.0.3 |
| Build Tool | Gradle | 9.x |
| ORM | Spring Data JPA + QueryDSL | Boot 기본 |
| Database | PostgreSQL | 18.x |
| Cache / 분산락 | Redis | 8.x |
| Message Broker | Redis Streams (알림), WebSocket STOMP (채팅) | - |
| MQTT Broker | Mosquitto | 2.x (EC2-2 운영) |
| Security | Spring Security + JWT (JJWT) | - |
| OAuth2 | Spring Security OAuth2 Client (Google, Kakao) | - |
| 문서화 | SpringDoc OpenAPI (Swagger UI) | 3.x |
| 테스트 | JUnit 5, Mockito, AssertJ | Boot 기본 |

### Frontend

| 항목 | 기술 |
|------|------|
| Framework | React (AI 담당) |
| 실시간 | STOMP over WebSocket, SSE |

### Infra / DevOps

| 항목 | 기술 |
|------|------|
| Cloud | KT Cloud (AWS 호환) |
| Container | Docker, Docker Compose |
| CI/CD | GitHub Actions |
| Load Balancer | ALB |
| Compute | EC2 t4g.medium × 2 |
| Storage | EBS |
| 알림 | Slack Bot + Lambda |
| 모니터링 | Prometheus + Grafana |
| 로그 | Logback + (ELK 검토) |

---

## ⑤ 시스템 아키텍처

```
[Client (React)]
      │
      ▼
[ALB] ──── HTTPS ────►  [EC2-1: Spring Boot App]
                              │   ├─ :8081 (Blue)
                              │   └─ :8082 (Green)  ← 롤링 배포
                              │
                              ├──► [EC2-2: Internal Services]
                              │         ├─ PostgreSQL :5432
                              │         ├─ Redis :6379
                              │         └─ Mosquitto MQTT :8883 (TLS)
                              │
                              └──► [GitHub Actions] ── CI/CD
```

### 인프라 구성 원칙

- EC2-1: 앱 서버 전용, Blue/Green 포트 기반 롤링 배포
- EC2-2: DB/캐시/모니터링 내부 통합 — private subnet, 외부 직접 노출 금지
- 모든 EC2 간 통신은 internal IP 사용
- NAT Gateway: EC2에서 외부 패키지 설치 등 outbound 전용

---

## ⑥ R&R (역할 분담)

| 이름 | 역할 | 도메인 |
|------|------|--------|
| 강정훈 | 백엔드, 인프라, 팀장 | 알림, 인프라, CI/CD, 회원, 팀 |
| 손하영 | 백엔드 | 예매, 경기/좌석 |
| 유창민 | 백엔드 | 결제 |
| 주병규 | 백엔드 | 채팅 |

> 프론트엔드 및 앱 UI는 AI가 담당. **웹 전용 개발** (앱 개발 없음).

---

## ⑦ 트래픽 & 부하 가정

| 항목 | MVP 목표 | 최대 확장 목표 |
|------|----------|---------------|
| 동시 접속자 | 1,000명 | 5,000명 |
| API RPS | 300 ~ 1,000 | 5,000+ |
| 채팅 메시지/초 | 300 ~ 800 | 2,000+ |
| 티켓 오픈 시 대기열 | 1,000명 동시 진입 | - |

- 티켓 오픈 시점에 집중되는 **Burst Traffic 구조** — 대기열로 유량 제어

---

## ⑧ 비용 설계

### 예산 구성

| 항목 | 원화 | 달러(약) |
|------|------|---------|
| KT Cloud 지원금 | 100,000원 | $75 |
| 프리티어 활용 | 50,000원 | $38 |
| **총 예산** | **150,000원** | **$113** |

### 예산 배분 (풀가동 기준)

| 항목 | 예산 |
|------|------|
| EC2-1 (t4g.medium) | $28 |
| EC2-2 (t4g.medium) | $28 |
| ALB | $16 |
| NAT Gateway | $33 |
| EBS | $8 |
| 트래픽 + 기타 | $4 |
| 버퍼 | $7 |
| **합계** | **$124** |

### 비용 절감 전략

- **야간 스케줄링**: 평일 14시간 × 22일 = 308시간 → 41% 가동 → 월 ~$51
- **Slack Bot 비용 제어** (Lambda + Incoming Webhook, 무료)
  - `/server start` → EC2 전체 켜기
  - `/server stop` → EC2 전체 끄기
  - `/server status` → 현재 상태 + 오늘 비용 확인

---

## ⑨ 환경 분리 전략

> `.env` 파일은 **절대 커밋 금지** (`.gitignore` + pre-commit hook으로 이중 차단)

### 환경 파일 구조

```
프로젝트 루트/
├── .env.local    # 로컬 개발용 (개인 PC)
├── .env.dev      # 개발 서버용 (EC2 dev, 절대 공유 금지)
└── .env.prod     # 운영 서버용 (EC2 prod, 절대 공유 금지)
```

### 환경별 보안 설정

| 항목 | local | dev | prod |
|------|-------|-----|------|
| DB 인증 | 단순 PW 가능 | 강한 PW 권장 | 강한 PW 필수 |
| Redis 인증 | AUTH 없이 가능 | requirepass 권장 | requirepass 필수 |
| DB 네트워크 | localhost 허용 | private subnet | private subnet만 접근 |
| Redis 네트워크 | localhost 허용 | private subnet | private subnet만 접근 |
| SSH | 자유 | EC2 키페어 | EC2 키페어 + 보안그룹 제한 |
| HTTPS | HTTP 가능 | HTTPS 권장 | HTTPS 강제 (ALB SSL) |
| show-sql | true | true | false |
| log level | DEBUG | DEBUG | INFO |

### Spring Profile 연동

```
application.yml        → 공통 설정 (Jackson UTC 등)
application-local.yml  → 로컬 전용 (localhost DB/Redis, mock mail)
application-dev.yml    → 개발 서버 전용 (환경변수 주입, DEBUG 로그)
application-prod.yml   → 운영 전용 (환경변수 주입, INFO 로그, management port 분리)
```

### DB 스키마 관리 — Flyway

- `src/main/resources/db/migration/V{버전}__{설명}.sql` 형식으로 관리
- 모든 환경(local/dev/prod/test)에서 Flyway가 스키마를 책임짐
- `ddl-auto: validate` — JPA는 검증만, 스키마 변경은 반드시 마이그레이션 파일로

---

## ⑩ 성공 지표 (KPI / Metrics)

| 지표 | 목표값 |
|------|--------|
| API 평균 응답 시간 | < 200ms |
| P95 latency | < 300ms |
| 채팅 메시지 전달 지연 | < 200ms |
| 시스템 가용성 | 99.5% 이상 (MVP 기준) |
| 티켓 예매 성공률 | 99% 이상 (중복 실패 제외) |
| 에러율 | < 1% |

---

## ⑪ 개발 일정

**개발 기간:** `2026-04-20` ~ `2026-05-20` (총 4주)

| 목표 | 일정 | 세부사항 |
|------|------|---------|
| 도메인 기획, 요구사항 설계 | 04/20(월) ~ 04/22(수) | 도메인 정의, 요구사항 정의서 |
| ERD, API 설계 | 04/22(수) ~ 04/23(목) | 1차 설계 (개발 중 수정 허용) |
| **Sprint 1** — P1 기능 구현 | 04/23(목) ~ 04/29(수) | 공통 인프라 + 도메인별 핵심 기능 |
| **Sprint 2** — P2 기능 + 성능 | 04/29(목) ~ 05/13(수) | 부하 테스트, 비기능 요구사항 |
| 중간 발표 준비 | 04/30 ~ 05/06 | 05/06(수) 18:00까지 제출 |
| **중간 발표** | **05/07(목)** | 팀당 20분 이내 |
| 부하 테스트 + 개선 | 04/29 ~ 05/17 | JMeter 시나리오, 튜닝 |
| 최종 발표 준비 | 05/14 ~ 05/19 | 05/20(수) 17:00까지 제출 |
| **최종 발표** | **05/21(목)** | 발표 20분 + 질의응답 10분 |

---

## ⑫ 기술 챌린지 목록

> 성능 비교·검증이 목적. 단순 구현이 아니라 **측정 결과를 문서화**해야 함.

| 챌린지 | 내용 | 담당 |
|--------|------|------|
| 동시성 제어 비교 | 비관적 락 vs 낙관적 락 vs Redis 분산 락 (TPS, 에러율 비교) | 손하영, 유창민 |
| 캐싱 계층 구조 | Caffeine(L1) → Redis(L2) → DB(L3) hit rate 측정 | 전체 |
| 대기열 성능 | Redis Sorted Set 기반 대기열 SSE 1,000 동시 연결 테스트 | 손하영 |
| 부하 테스트 | JMeter 시나리오 (티켓 오픈 시뮬레이션) | 전체 |
| 모니터링 | Prometheus + Grafana 대시보드 구성 | 강정훈 |
