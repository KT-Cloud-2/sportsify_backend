# 팀 규칙

---

## 1. 커밋 메시지 컨벤션

형식: `[커밋 유형]: [커밋 제목]`

### 커밋 유형

| 유형 | 의미 |
|------|------|
| `feat` | 새로운 기능 추가 |
| `fix` | 버그 수정 |
| `refactor` | 코드 리팩토링 (동작 변화 없음) |
| `test` | 테스트 코드 추가 및 리팩토링 |
| `perf` | 성능 개선 |
| `docs` | 문서 수정 |
| `chore` | 패키지 매니저 수정, 기타 (.gitignore 등) |
| `style` | 코드 포매팅, 세미콜론 누락 등 코드 변경 없는 경우 |
| `design` | CSS 등 UI 디자인 변경 |
| `comment` | 필요한 주석 추가 및 변경 |
| `rename` | 파일·폴더 이름 변경 또는 이동 |
| `remove` | 파일 삭제 |
| `!BREAKING CHANGE` | 커다란 API 변경 |
| `!HOTFIX` | 치명적인 버그 긴급 수정 |

### 작성 규칙
- 제목은 50자 이내, 직관적인 내용으로 작성
- 세부 항목은 `-`로 추가 작성

```
feat: 티켓 선점(HOLD) 로직 추가

- Redis 기반 좌석 선점 처리 구현
- TTL 5분 적용하여 미결제 자동 해제
```

---

## 2. Issue

- 이슈 먼저 생성 후 작업 시작 (태스크 관리 목적)

### Issue 템플릿

```
## Feature/Bug

> 추가하려는 기능에 대해 간결하게 설명해주세요

* 기능 설명
* 혹은 버그 설명

## 작업 상세 내용

- [ ] TODO
- [ ] TODO
- [ ] TODO

## 참고할만한 자료(선택)
```

---

## 3. Pull Request

- **코드 리뷰**: 코드래빗 + 팀원 1명
- **merge 담당**: 팀장 강정훈

### PR 템플릿

```
# Related Issues

- #[이슈]

## 작업 리스트

## 작업 내용

## 참고사항
```

---

## 4. Branch 전략 — Git Flow

```
main
 └─ hotfix/*       main에서 발생한 버그 수정 → main
 └─ develop
      └─ feature/* 기능 개발 → develop
```

| 브랜치 | 용도 |
|--------|------|
| `main` | 라이브 서버 출시 브랜치 |
| `hotfix/*` | main 브랜치 긴급 버그 수정 |
| `develop` | 다음 출시 버전 통합 브랜치 (develop → main) |
| `feature/*` | 기능 개발 브랜치 (feature → develop) |

> `release` 브랜치는 사용하지 않는다.

### 브랜치 명명 규칙

`유형/설명` 형식 (kebab-case)

```
feature/ticket-reservation
feature/chat-realtime
hotfix/seat-double-booking
```

---

## 5. 코드 스타일

### DTO / Record / Enum 사용 기준

| 타입 | 사용 기준 |
|------|----------|
| `Enum` | 스포츠 종류, 구단명 등 고정 값 |
| `record` | 내부 구현이 없는 불변 DTO (우선 사용) |
| `class` | 내부 구현이 필요한 경우 (JPA 엔티티 등) |

> 코드 리뷰 과정에서 필요 시 변경한다.

### 포매터

Google Java Format 사용 (`google-java-format`)

---

## 6. 패키지 구조 — 도메인 패키징 + DDD

```
com.sportsify
├── SortsifyApplication.java
│
├── common/
│   ├── domain/         # 공통 도메인 개념
│   ├── event/          # Domain Event
│   ├── exception/      # 예외 통합 관리
│   └── util/           # 공통 유틸
│
├── member/
│   ├── domain/
│   │   ├── model/      # Entity, Value Object, Aggregate
│   │   ├── service/    # Domain Service
│   │   └── repository/ # Repository 인터페이스
│   ├── application/
│   │   ├── service/    # Application Service (유스케이스)
│   │   └── dto/        # 내부 DTO 
│   ├── infrastructure/
│   │   ├── repository/ # JPA 구현체
│   │   └── external/   # 외부 시스템 연동
│   └── presentation/
│       ├── controller/
│       └── dto/        # Request / Response DTO
│
├── ticketing/          # 동일 구조
├── payment/            # 동일 구조
├── chat/
│   ├── application/
│   │   └── consumer/   # Redis Streams Consumer
│   └── ...             # 동일 구조
├── notification/
│   ├── application/
│   │   └── consumer/   # Redis Streams Consumer
│   └── ...             # 동일 구조
│
└── infrastructure/     # 공통 인프라
    ├── config/         # Redis, JPA Config
    ├── redis/
    └── jpa/
```

---

## 7. 도메인 간 결합 규칙

- 다른 도메인 엔티티를 직접 참조하지 않는다.
- 도메인 간 통신은 **Redis Streams 이벤트** 또는 **자체 DTO** 를 통해서만 한다.
- Claude를 사용하는 경우에도 다른 도메인 코드를 직접 구현하지 않는다.

---

## 8. 환경변수 / 보안 규칙

### 클론 후 최초 1회 셋업 필수

```bash
bash scripts/setup.sh
```

이 스크립트가 하는 일:
1. `git config core.hooksPath .githooks` — 팀 공유 Git hook 경로 설정
2. `.env.example` → `.env.local` 복사 (없을 경우에만)

> **셋업을 건너뛰면 pre-commit hook이 동작하지 않는다.**

### .env 파일 규칙

| 파일 | 용도 | 커밋 |
|------|------|------|
| `.env.example` | 환경변수 키 템플릿 (값 없음) | **커밋 O** |
| `.env.local` | 로컬 개발용 실제 값 | **커밋 금지** |
| `.env.prod` | 운영 서버용 실제 값 | **커밋 금지** |

- `.env.local`, `.env.prod`는 `.gitignore`에 등록되어 있음
- pre-commit hook(`.githooks/pre-commit`)이 `.env` 파일 스테이징을 차단함
- `git add -f`로 강제 추가 시도해도 커밋 단계에서 차단됨

### 새 환경변수 추가 시

1. `.env.example`에 키와 설명 주석 추가 (값은 비워둠) — **커밋**
2. `application-local.properties` / `application-prod.properties`에 `${변수명}` 참조 추가 — **커밋**
3. 각자 `.env.local` / EC2 환경변수에 실제 값 설정 — **커밋 금지**

---

## 9. 협업 문화

- Git Flow 브랜치 전략 준수
- PR 리뷰 후 merge (팀장 최종 승인)
- 주 2회 회고
- Jira / Notion으로 이슈 관리
- **클론 직후 `bash scripts/setup.sh` 필수 실행**
