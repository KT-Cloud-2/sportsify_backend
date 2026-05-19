# 통합 테스트 계획서

## 개요

현재 프로젝트의 테스트는 단위 테스트(`@WebMvcTest`, Mockito)와 일부 통합 테스트(`RepositoryTestSupport`, `ApiTestSupport`)가 혼재한다.
이 계획서는 **도메인 간 흐름을 검증하는 통합 테스트**를 기준으로 누락된 항목을 파악하고 작성 순서를 정한다.

## 테스트 인프라

| 클래스 | 용도 |
|---|---|
| `RepositoryTestSupport` | `@SpringBootTest` + `@Transactional` — DB 연동 서비스/레포지토리 테스트 |
| `ApiTestSupport` | `@SpringBootTest` + `MockMvc` + `springSecurity()` — 실제 필터 체인 통과 E2E |
| `TestContainersConfig` | PostgreSQL 18 + Redis 8 Testcontainer (`withReuse(true)`) |

---

## 도메인별 현황 및 계획

### 1. Member

| 테스트 | 파일 | 현황 |
|---|---|---|
| 레포지토리 | `MemberRepositoryTest` | 완료 |
| 레포지토리 | `MemberFavoriteTeamRepositoryTest` | 완료 |
| 서비스 단위 | `MemberServiceTest` | 완료 |
| **API 통합** | `MemberControllerIntegrationTest` | **미작성** |

**작성 항목 (`ApiTestSupport` 상속)**
- `GET /api/members` — 실제 JWT 필터 통과 후 회원 정보 반환
- `PATCH /api/members/nickname` — 닉네임 중복 시 409, 정상 수정 시 200
- `DELETE /api/members` — 탈퇴 후 상태 WITHDRAWN 확인
- `POST /api/members/favorite-teams` — 팀 추가 후 목록 조회 일치 확인

---

### 2. Ticketing (예매)

| 테스트 | 파일 | 현황 |
|---|---|---|
| 서비스 통합 | `ReservationServiceIntegrationTest` | 완료 (동시성 포함) |
| API 단위 | `ReservationControllerApiTest` | 완료 |
| **API 통합** | `ReservationControllerIntegrationTest` | **미작성** |

**작성 항목 (`ApiTestSupport` 상속)**
- `POST /api/seats/reservations` — JWT 인증 → 예매 → 주문 생성 전체 흐름
- `POST /api/seats/reservations` — 이미 예매된 좌석 409 확인
- 좌석 만료 스케줄러 실행 후 AVAILABLE 복구 확인

---

### 3. Payment (결제)

| 테스트 | 파일 | 현황 |
|---|---|---|
| 서비스 단위 | `PaymentServiceTest` | 완료 |
| **서비스 통합** | `PaymentServiceIntegrationTest` | **미작성** |
| **API 통합** | `PaymentControllerIntegrationTest` | **미작성** |

**작성 항목 (`RepositoryTestSupport` 상속)**
- `createPayment` — 주문 존재 확인 후 Payment 생성, 멱등성 키 중복 시 예외
- `confirmPaymentMock` — Mock 결제 확인 후 상태 COMPLETED, 주문 PAID 전환
- `cancelPayment` — COMPLETED 상태에서 취소 후 상태 CANCELED 확인

**작성 항목 (`ApiTestSupport` 상속)**
- `POST /payments` — 인증 → 결제 생성
- `POST /payments/mock-confirm` — Mock 결제 승인 전체 흐름

---

### 4. Notification (알림)

| 테스트 | 파일 | 현황 |
|---|---|---|
| 청크 발송 통합 | `NotificationChunkServiceIntegrationTest` | 완료 |
| 예약 발송 클레임 통합 | `ScheduledEventClaimServiceIntegrationTest` | 완료 |
| **SSE 구독 통합** | `NotificationSseIntegrationTest` | **미작성** |
| **알림 설정 API 통합** | `NotificationSettingControllerIntegrationTest` | **미작성** |

**작성 항목**
- SSE 연결 (`GET /api/notifications/stream?token=...`) — 200 + `text/event-stream` Content-Type 확인
- 알림 설정 조회/수정 — DB 반영 확인
- 채널 등록 → 삭제 → 토글 흐름

---

### 5. Chat (채팅)

| 테스트 | 파일 | 현황 |
|---|---|---|
| 레포지토리 | `ChatRoomJpaRepositoryTest` 등 | 완료 |
| API 단위 | `ChatRoomControllerApiTest` 등 | 완료 |
| **API 통합** | `ChatRoomControllerIntegrationTest` | **미작성** |

**작성 항목 (`ApiTestSupport` 상속)**
- 채팅방 생성 → 참여 → 메시지 조회 흐름
- 권한 없는 사용자 채팅방 접근 403 확인

---

## 작성 우선순위

| 순위 | 대상 | 이유 |
|---|---|---|
| 1 | `PaymentControllerIntegrationTest` | 결제 플로우 전체 검증 미비 |
| 2 | `ReservationControllerIntegrationTest` | 예매 → 결제 연결 흐름 |
| 3 | `MemberControllerIntegrationTest` | JWT 필터 실제 통과 검증 |
| 4 | `NotificationSseIntegrationTest` | SSE 쿼리 파라미터 인증 검증 |
| 5 | `ChatRoomControllerIntegrationTest` | 채팅 흐름 E2E |

---

## 작성 규칙

- `ApiTestSupport` 상속 — 실제 필터 체인(JWT 인증 포함) 통과
- `RepositoryTestSupport` 상속 — 서비스 레이어 + DB 연동, 필터 불필요 시
- `@AfterEach` + fixture `deleteAll()` — 테스트 격리
- GIVEN-WHEN-THEN 패턴 준수
- 테스트 메서드명은 한글로 작성
