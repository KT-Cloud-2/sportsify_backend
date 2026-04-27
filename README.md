# Sportsify

스포츠 경기 예매 + 팀 응원 채팅 통합 백엔드 시스템

---

## 프로젝트 소개

티켓 구매부터 실시간 응원까지, 스포츠 관람 경험을 하나의 서비스로 완결합니다.

- 스포츠 경기 예매 — 대기열, 좌석 선점, 결제
- 실시간 응원 채팅 — WebSocket STOMP 기반
- 알림 — Redis Streams 이벤트 버스, Email / MQTT 채널

---

## 기술 스택

| 분류 | 기술 |
|------|------|
| Language | Java 25 |
| Framework | Spring Boot 4.0.3 |
| Database | PostgreSQL 18, Redis 8 |
| Realtime | WebSocket (STOMP) |
| Event Bus | Redis Streams |
| MQTT | Mosquitto |
| Infra | Docker, GitHub Actions |

---

## 핵심 기술 챌린지

- **동시성 제어 3종 비교**: 비관적 락 vs 낙관적 락 vs Redis 분산 락
- **대기열 시스템**: Redis Sorted Set 기반 SSE 스트리밍 대기열
- **실시간 채팅**: WebSocket STOMP + Redis Pub/Sub
- **이벤트 드리븐**: Redis Streams 기반 도메인 간 느슨한 결합
- **캐싱 3단 구조**: Caffeine(L1) → Redis(L2) → DB(L3)

---

## 로컬 실행 방법

### 1. 최초 1회 셋업

```bash
bash scripts/setup.sh
```

- Git hooks 경로 설정 (`.env` 파일 커밋 방지 pre-commit hook 포함)
- `.env.example` → `.env.local` 복사

이후 `.env.local`에 실제 값을 채웁니다.

### 2. 인프라 실행

```bash
docker compose -f docker-compose.local.yml up -d
```

`-f` 옵션을 사용하는 이유: `docker-compose.local.yml` / `docker-compose.infra.yml` / `docker-compose.prod.yml` 세 파일이 존재하므로 파일명을 명시합니다.

| 서비스 | 주소 |
|--------|------|
| PostgreSQL | `localhost:5432` |
| Redis | `localhost:6379` |
| Prometheus | `http://localhost:9090` |
| Grafana | `http://localhost:3001` (admin / admin) |

### 3. 애플리케이션 실행

IDE에서 `SPRING_PROFILES_ACTIVE=local`로 실행하거나:

```bash
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
```

앱 시작 시 Flyway가 DB 스키마를 자동 생성합니다.

| 엔드포인트 | 주소 |
|-----------|------|
| API | `http://localhost:8080` |
| API Docs (Redoc) | `http://localhost:8080/docs.html` |

---

## DB 스키마 관리

Flyway 기반으로 관리합니다. 모든 DDL 변경은 migration 파일로만 합니다.

```
src/main/resources/db/migration/
└── V1__init_schema.sql   ← 초기 전체 스키마
```

- `ddl-auto: validate` — JPA는 검증만 수행, 스키마 직접 변경 금지
- 변경이 필요하면 `V2__...sql`, `V3__...sql` 순으로 추가

---

## 팀 구성

| 이름 | 도메인 |
|------|--------|
| 강정훈 | 회원, 팀, 알림, 인프라 |
| 손하영 | 경기/좌석, 예매 |
| 유창민 | 결제 |
| 주병규 | 채팅 |

---

## 문서

| 문서 | 설명 |
|------|------|
| [프로젝트 개요](docs/01-project-overview.md) | 목표, 핵심 기능, 기술 스택, 개발 일정, KPI |
| [ERD](docs/02-erd.md) | 도메인별 테이블 정의, Redis 키 구조, Redis Streams |
| [팀 규칙](docs/03-team-rules.md) | 커밋 컨벤션, 브랜치 전략, 코드 스타일, 패키지 구조 |
| [API 명세서](docs/04-api-spec.md) | 전 도메인 REST API 및 WebSocket 명세 |
