# API 명세서 — Sportsify

> **Base URL** : `https://api.sportsify.com` (로컬: `http://localhost:8080`)  
> **인증** : `Authorization: Bearer {accessToken}` 헤더 필요 (Auth Required = O인 경우)  
> **API Docs** : 앱 실행 후 `http://localhost:8080/docs.html` (Swagger UI + Redoc)

---

## 공통 규격

### 성공 응답

도메인 DTO를 HTTP 상태 코드와 함께 직접 반환합니다. (래핑 없음)

```json
// 200 OK — 단일 객체
{ "memberId": 1, "nickname": "응원왕", "email": "user@example.com" }

```

### 에러 응답

```json
{
  "code": "SEAT_ALREADY_RESERVED",
  "message": "이미 선점된 좌석입니다.",
  "detail": null
}
```

### HTTP 상태 코드

| 코드 | 설명 |
|------|------|
| 200 | 성공 |
| 201 | 리소스 생성 성공 |
| 204 | 성공 (응답 본문 없음) |
| 400 | 요청 파라미터/바디 오류 |
| 401 | 인증 실패 (토큰 없음 또는 만료) |
| 403 | 권한 없음 |
| 404 | 리소스 없음 |
| 409 | 충돌 (중복, 이미 존재) |
| 410 | 리소스 만료 (예: 주문 만료) |
| 422 | 비즈니스 규칙 위반 |
| 429 | 요청 한도 초과 |
| 500 | 서버 내부 오류 |

### 공통 에러 코드

| 에러 코드 | HTTP | 설명 |
|---|---|---|
| `INVALID_INPUT` | 400 | 입력값 유효성 오류 |
| `REQUEST_INVALID` | 400 | Request Body 누락 |
| `REQUEST_BODY_MALFORMED` | 400 | JSON 형식 오류 |
| `UNAUTHORIZED` | 401 | 인증 필요 |
| `FORBIDDEN` | 403 | 접근 권한 없음 |
| `NOT_FOUND` | 404 | 리소스 없음 |
| `CONFLICT` | 409 | 리소스 충돌 (중복) |
| `BUSINESS_RULE_VIOLATION` | 422 | 비즈니스 규칙 위반 |
| `TOO_MANY_REQUESTS` | 429 | 요청 한도 초과 |
| `INTERNAL_ERROR` | 500 | 서버 내부 오류 |

### 페이지네이션

**Offset 기반** (티켓 목록): `page`, `size` 쿼리 파라미터

**커서 기반** (채팅 메시지 등): `cursor`, `limit` 쿼리 파라미터

---

## 도메인별 엔드포인트 목록

| 도메인 | 엔드포인트 수 |
|---|---|
| [인증/회원](#1-인증--회원) | 11 |
| [팀](#2-팀) | 2 |
| [경기/좌석](#3-경기--좌석) | 4 |
| [예매](#4-예매) | 2 |
| [결제](#5-결제) | 3 |
| [채팅 REST](#6-채팅-rest) | 15 |
| [채팅 WebSocket](#7-채팅-websocket-stomp) | 3 (STOMP) |
| [알림](#8-알림) | 10 |

---

## 1. 인증 / 회원

소셜 로그인(Google/Kakao) 기반 인증과 회원 정보 관리, 선호 팀 관리를 담당합니다.  
자체 비밀번호 없이 OAuth2 Provider가 발급한 ID로 회원을 식별합니다.

| Method | Path | Auth | 설명 |
|---|---|---|---|
| GET | `/oauth2/authorization/{registrationId}` | N | 소셜 로그인 진입 |
| GET | `/oauth2/callback` | N | 소셜 로그인 콜백 (토큰 발급) |
| POST | `/api/auth/token/refresh` | N | 액세스 토큰 갱신 |
| POST | `/api/auth/logout` | O | 로그아웃 |
| GET | `/api/members/me` | O | 내 정보 조회 |
| PATCH | `/api/members/me/nickname` | O | 닉네임 수정 |
| DELETE | `/api/members/me` | O | 회원 탈퇴 |
| POST | `/api/members/me/favorite-teams` | O | 선호 팀 추가 |
| GET | `/api/members/me/favorite-teams` | O | 선호 팀 목록 조회 |
| PATCH | `/api/members/me/favorite-teams/{teamId}/priority` | O | 선호 팀 우선순위 수정 |
| DELETE | `/api/members/me/favorite-teams/{teamId}` | O | 선호 팀 삭제 |

---

### 1-1. 소셜 로그인 진입

```
GET /oauth2/authorization/{registrationId}
```

브라우저에서 직접 접근. Spring Security OAuth2 Client가 Provider 로그인 페이지로 리다이렉트합니다.

**Path Variable**

| 변수 | 값 | 설명 |
|---|---|---|
| `registrationId` | `google` \| `kakao` | 소셜 로그인 제공사 |

---

### 1-2. 소셜 로그인 콜백

```
GET /oauth2/callback?accessToken={jwt}&refreshToken={jwt}
```

인증 완료 후 리다이렉트. 클라이언트가 쿼리스트링에서 토큰을 추출합니다.

**Response (Query Parameter)**

| 필드 | 타입 | 설명 |
|---|---|---|
| `accessToken` | String | JWT 액세스 토큰 (만료: 30분) |
| `refreshToken` | String | JWT 리프레시 토큰 (만료: 30일) |

---

### 1-3. 액세스 토큰 갱신

```
POST /api/auth/token/refresh
```

리프레시 토큰으로 액세스/리프레시 토큰 쌍을 재발급합니다. 기존 리프레시 토큰은 즉시 무효화(Rotation)됩니다.

**Request Body**

```json
{
  "refreshToken": "eyJhbGciOiJIUzI1NiJ9..."
}
```

| 필드 | 타입 | 필수 | 검증 | 설명 |
|---|---|---|---|---|
| `refreshToken` | String | O | `@NotBlank` | 유효한 리프레시 토큰 |

**Response (200)**

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiJ9..."
}
```

**에러**

| 코드 | HTTP | 설명 |
|---|---|---|
| `INVALID_REFRESH_TOKEN` | 401 | 유효하지 않거나 만료된 리프레시 토큰 |

---

### 1-4. 로그아웃

```
POST /api/auth/logout
```

Redis에서 리프레시 토큰을 삭제하고 액세스 토큰을 블랙리스트에 등록합니다.

**Request Body**

```json
{
  "refreshToken": "eyJhbGciOiJIUzI1NiJ9..."
}
```

**Response** : `204 No Content`

---

### 1-5. 내 정보 조회

```
GET /api/members/me
```

**Response (200)**

```json
{
  "memberId": 1,
  "email": "user@example.com",
  "nickname": "응원왕",
  "createdAt": "2026-01-01T00:00:00"
}
```

**에러**

| 코드 | HTTP | 설명 |
|---|---|---|
| `MEMBER_NOT_FOUND` | 404 | 존재하지 않는 회원 |
| `MEMBER_WITHDRAWN` | 403 | 탈퇴한 회원 |

---

### 1-6. 닉네임 수정

```
PATCH /api/members/me/nickname
```

**Request Body**

```json
{
  "nickname": "새닉네임"
}
```

| 필드 | 타입 | 필수 | 검증 | 설명 |
|---|---|---|---|---|
| `nickname` | String | O | `@NotBlank`, `@Size(min=2, max=20)` | 변경할 닉네임 (2~20자) |

**Response (200)**

```json
{
  "memberId": 1,
  "nickname": "새닉네임"
}
```

**에러**

| 코드 | HTTP | 설명 |
|---|---|---|
| `NICKNAME_DUPLICATE` | 409 | 이미 사용 중인 닉네임 |
| `INVALID_INPUT` | 400 | 길이 2~20자 위반 |

---

### 1-7. 회원 탈퇴

```
DELETE /api/members/me
```

회원 status를 `WITHDRAWN`으로 변경합니다. 실제 데이터는 삭제되지 않습니다.

**Response** : `204 No Content`

---

### 1-8. 선호 팀 추가

```
POST /api/members/me/favorite-teams
```

**Request Body**

```json
{
  "teamId": 3,
  "priority": 1
}
```

| 필드 | 타입 | 필수 | 검증 | 설명 |
|---|---|---|---|---|
| `teamId` | Long | O | `@NotNull` | 추가할 팀 ID |
| `priority` | Integer | N | `@Positive` | 선호 순위 (미지정 시 자동 할당) |

**Response (201)**

```json
{
  "favoriteTeamId": 10,
  "teamId": 3,
  "teamName": "KIA 타이거즈",
  "shortName": "KIA",
  "sportType": "BASEBALL",
  "priority": 1
}
```

**에러**

| 코드 | HTTP | 설명 |
|---|---|---|
| `TEAM_NOT_FOUND` | 404 | 존재하지 않는 팀 |
| `FAVORITE_TEAM_ALREADY_EXISTS` | 409 | 이미 등록된 선호 팀 |

---

### 1-9. 선호 팀 목록 조회

```
GET /api/members/me/favorite-teams
```

**Response (200)**

```json
[
  {
    "favoriteTeamId": 10,
    "teamId": 3,
    "teamName": "KIA 타이거즈",
    "shortName": "KIA",
    "sportType": "BASEBALL",
    "priority": 1
  }
]
```

---

### 1-10. 선호 팀 우선순위 수정

```
PATCH /api/members/me/favorite-teams/{teamId}/priority
```

**Path Variable** : `teamId` — 우선순위를 변경할 팀 ID

**Request Body**

```json
{
  "priority": 2
}
```

| 필드 | 타입 | 필수 | 검증 | 설명 |
|---|---|---|---|---|
| `priority` | Integer | O | `@NotNull`, `@Min(1)` | 변경할 우선순위 (1 이상) |

**에러**

| 코드 | HTTP | 설명 |
|---|---|---|
| `FAVORITE_TEAM_NOT_FOUND` | 404 | 선호 팀으로 등록되지 않은 팀 |
| `INVALID_PRIORITY` | 400 | 우선순위 범위 오류 |

---

### 1-11. 선호 팀 삭제

```
DELETE /api/members/me/favorite-teams/{teamId}
```

**Response** : `204 No Content`

**에러**

| 코드 | HTTP | 설명 |
|---|---|---|
| `FAVORITE_TEAM_NOT_FOUND` | 404 | 선호 팀으로 등록되지 않은 팀 |

---

## 2. 팀

스포츠 팀 마스터 데이터를 조회합니다. 팀 데이터는 관리자가 등록하며 샘플 데이터로 운영됩니다.

| Method | Path | Auth | 설명 |
|---|---|---|---|
| GET | `/api/teams` | N | 팀 목록 조회 (필터 지원) |
| GET | `/api/teams/{teamId}` | N | 팀 상세 조회 |

---

### 2-1. 팀 목록 조회

```
GET /api/teams?sportType={sportType}&isActive={isActive}
```

**Query Parameter**

| 파라미터 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `sportType` | String | N | `BASEBALL` \| `FOOTBALL` \| `BASKETBALL` |
| `isActive` | Boolean | N | 활동 중인 팀만 조회 (`true`) |

**Response (200)**

```json
[
  {
    "teamId": 1,
    "name": "KIA 타이거즈",
    "shortName": "KIA",
    "sportType": "BASEBALL",
    "logoUrl": "https://cdn.sportsify.com/teams/kia.png",
    "active": true
  }
]
```

---

### 2-2. 팀 상세 조회

```
GET /api/teams/{teamId}
```

**에러**

| 코드 | HTTP | 설명 |
|---|---|---|
| `TEAM_NOT_FOUND` | 404 | 존재하지 않는 팀 |

---

## 3. 경기 / 좌석

경기 일정 조회 및 경기별 좌석 현황을 제공합니다.  
경기 상태는 `SCHEDULED → ON_SALE → SALE_CLOSED → IN_PROGRESS → FINISHED` 순으로 자동 전환됩니다.

| Method | Path | Auth | 설명 |
|---|---|---|---|
| GET | `/api/games` | N | 경기 목록 조회 |
| GET | `/api/games/{gameId}` | N | 경기 상세 조회 |
| GET | `/api/games/{gameId}/seats` | N | 좌석 목록 조회 |
| POST | `/api/games` | O | 경기 등록 |

---

### 3-1. 경기 목록 조회

```
GET /api/games?sportType=&teamId=&status=&from=&to=
```

**Query Parameter**

| 파라미터 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `sportType` | String | N | 종목 필터 (`BASEBALL` \| `FOOTBALL` \| `BASKETBALL`) |
| `teamId` | Long | N | 특정 팀이 참여한 경기만 조회 |
| `status` | String | N | 경기 상태 필터 |
| `from` | LocalDateTime | N | 조회 시작 일시 |
| `to` | LocalDateTime | N | 조회 종료 일시 |

**Response (200)**

```json
[
  {
    "gameId": 1,
    "sportType": "BASEBALL",
    "teams": [
      { "teamId": 1, "name": "KIA 타이거즈", "side": "HOME" },
      { "teamId": 2, "name": "삼성 라이온즈", "side": "AWAY" }
    ],
    "gameTime": "2026-05-25T14:00:00",
    "stadium": "광주-기아 챔피언스 필드",
    "status": "ON_SALE",
    "totalSeats": 20000,
    "availableSeats": 1234,
    "isRivalMatch": false
  }
]
```

---

### 3-2. 경기 상세 조회

```
GET /api/games/{gameId}
```

목록 조회 응답에 `seatGradeSummary`(등급별 잔여 좌석 요약)와 `maxTicketPerUser`가 추가됩니다.

**Response (200)**

```json
{
  "gameId": 1,
  "sportType": "BASEBALL",
  "teams": [...],
  "gameTime": "2026-05-25T14:00:00",
  "venue": "광주-기아 챔피언스 필드",
  "status": "ON_SALE",
  "totalSeats": 20000,
  "availableSeats": 1234,
  "isRivalMatch": false,
  "seatGradeSummary": [
    { "grade": "VIP", "total": 500, "available": 12, "price": 80000 },
    { "grade": "R", "total": 2000, "available": 340, "price": 50000 }
  ],
  "maxTicketPerUser": 4
}
```

**에러**

| 코드 | HTTP | 설명 |
|---|---|---|
| `GAME_NOT_FOUND` | 404 | 존재하지 않는 경기 |

---

### 3-3. 좌석 목록 조회

```
GET /api/games/{gameId}/seats?grade=&status=
```

**Query Parameter**

| 파라미터 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `grade` | String | N | 등급 필터 (`VIP` \| `R` \| `S` \| `A` \| `OUTFIELD`) |
| `status` | String | N | 상태 필터 (`AVAILABLE` \| `RESERVED` \| `SOLD`) |

**Response (200)**

```json
[
  {
    "seatId": 101,
    "grade": "R",
    "section": "1루 내야 1",
    "rowNumber": "A",
    "seatNumber": "15",
    "price": 50000,
    "status": "AVAILABLE"
  }
]
```

---

### 3-4. 경기 등록

```
POST /api/games
```

`saleStartAt`이 설정되면 해당 시각에 자동으로 `ON_SALE` 상태로 전환됩니다.

**Request Body**

```json
{
  "stadiumId": 1,
  "homeTeamId": 1,
  "awayTeamId": 2,
  "sportType": "BASEBALL",
  "startAt": "2026-05-25T14:00:00",
  "durationMinutes": 180,
  "status": "SCHEDULED",
  "dayType": "WEEKEND",
  "gameGrade": "NORMAL",
  "maxTicketPerUser": 4,
  "saleStartAt": "2026-05-01T10:00:00",
  "saleEndAt": "2026-05-25T12:00:00"
}
```

| 필드 | 타입 | 필수 | 검증 | 설명 |
|---|---|---|---|---|
| `stadiumId` | Long | O | `@NotNull` | 경기장 ID |
| `startAt` | LocalDateTime | O | `@NotNull` | 경기 시작 일시 |
| `status` | String | O | `@NotNull` | 초기 경기 상태 |
| `homeTeamId` | Long | N | - | 홈팀 ID |
| `awayTeamId` | Long | N | - | 원정팀 ID |
| `saleStartAt` | LocalDateTime | N | - | 예매 시작 일시 (지정 시 자동 ON_SALE 전환) |
| `saleEndAt` | LocalDateTime | N | - | 예매 종료 일시 |

**에러**

| 코드 | HTTP | 설명 |
|---|---|---|
| `STADIUM_NOT_FOUND` | 404 | 존재하지 않는 경기장 |
| `TEAM_NOT_FOUND` | 404 | 존재하지 않는 팀 |
| `PRICE_POLICY_NOT_FOUND` | 404 | 가격 정책 없음 |

---

## 4. 예매

좌석 선점과 티켓 발급을 처리합니다.  
선점 후 **15분 내 결제가 완료되지 않으면** 주문이 자동 만료(`CANCELLED`)됩니다.

| Method | Path | Auth | 설명 |
|---|---|---|---|
| POST | `/api/seats/reservations` | O | 좌석 선점 (주문 생성) |
| GET | `/api/tickets` | O | 내 티켓 목록 조회 |

---

### 4-1. 좌석 선점 (주문 생성)

```
POST /api/seats/reservations
```

DB 비관적 락으로 동시 선점 충돌을 방지합니다. 요청한 좌석 전체가 선점 가능한 경우에만 처리됩니다 (All-or-Nothing).

**Request Body**

```json
{
  "gameId": 1,
  "seatIds": [101, 102]
}
```

| 필드 | 타입 | 필수 | 검증 | 설명 |
|---|---|---|---|---|
| `gameId` | Long | O | `@NotNull` | 경기 ID |
| `seatIds` | List\<Long\> | O | `@NotEmpty`, 중복 불가 | 선점할 좌석 ID 목록 |

**Response (201)**

```json
{
  "orderId": 42,
  "gameId": 1,
  "memberId": 7,
  "status": "PENDING",
  "reservedAt": "2026-05-20T10:00:00",
  "amount": 100000,
  "seats": [
    {
      "seatId": 101,
      "seatGrade": "R",
      "seatSection": "1루 내야 1",
      "price": 50000
    }
  ],
  "expiresAt": "2026-05-20T10:15:00"
}
```

**에러**

| 코드 | HTTP | 설명 |
|---|---|---|
| `GAME_NOT_FOUND` | 404 | 존재하지 않는 경기 |
| `GAME_NOT_ON_SALE` | 422 | 판매 중이 아닌 경기 |
| `SEAT_NOT_FOUND` | 404 | 존재하지 않는 좌석 |
| `SEAT_ALREADY_RESERVED` | 409 | 이미 선점된 좌석 |
| `SEAT_IS_NULL` | 404 | 선택된 좌석 없음 |
| `SEAT_DUPLICATED` | 400 | 중복 좌석 ID |
| `GAME_MISMATCH` | 400 | 다른 경기의 좌석 혼합 |
| `TICKET_LIMIT_EXCEEDED` | 422 | 1인 최대 4매 초과 |

---

### 4-2. 내 티켓 목록 조회

```
GET /api/tickets?page=0&size=10
```

결제 완료 후 발급된 티켓 목록을 조회합니다.

**Query Parameter**

| 파라미터 | 타입 | 기본값 | 설명 |
|---|---|---|---|
| `page` | Integer | 0 | 페이지 번호 (0부터 시작) |
| `size` | Integer | 10 | 페이지 크기 |

**Response (200)**

```json
{
  "items": [
    {
      "ticketId": 1,
      "ticketNumber": "550e8400-e29b-41d4-a716-446655440000",
      "gameId": 1,
      "sportType": "BASEBALL",
      "team1Name": "KIA 타이거즈",
      "team2Name": "삼성 라이온즈",
      "gameTime": "2026-05-25T14:00:00",
      "venue": "광주-기아 챔피언스 필드",
      "seatGrade": "R",
      "seatSection": "1루 내야 1",
      "seatNumber": "A-15",
      "price": 50000,
      "status": "CONFIRMED",
      "issuedAt": "2026-05-20T10:05:00"
    }
  ],
  "currentPage": 0,
  "totalPages": 3,
  "totalCount": 25,
  "hasNext": true
}
```

---

## 5. 결제

Toss Payments PG와 연동합니다. 결제는 **생성 → 확인** 2단계로 진행됩니다.

| Method | Path | Auth | 설명 |
|---|---|---|---|
| POST | `/api/payments` | O | 결제 생성 (Toss 결제 진행 전 등록) |
| POST | `/api/payments/confirm` | O | 결제 확인 (Toss 승인 후 금액 검증) |
| POST | `/api/payments/{paymentId}/cancel` | O | 결제 취소 |

---

### 5-1. 결제 생성

```
POST /api/payments
```

Toss 결제 위젯 실행 전 내부 결제 레코드를 생성합니다. `idempotencyKey`로 중복 결제를 방지합니다.

**Request Body**

```json
{
  "orderId": 42,
  "matchId": 1,
  "seatId": 101,
  "amount": 50000,
  "paymentMethod": "CARD",
  "idempotencyKey": "order-42-member-7-1716192000000"
}
```

| 필드 | 타입 | 필수 | 검증 | 설명 |
|---|---|---|---|---|
| `orderId` | Long | O | `@NotNull`, `@Positive` | 내부 주문 ID |
| `matchId` | Long | O | `@NotNull`, `@Positive` | 경기 ID |
| `seatId` | Long | O | `@NotNull`, `@Positive` | 좌석 ID |
| `amount` | Long | O | `@NotNull`, `@Positive` | 결제 금액 |
| `paymentMethod` | String | O | `@NotBlank` | 결제 수단 (`CARD` \| `KAKAO_PAY` \| `TOSS_PAY`) |
| `idempotencyKey` | String | O | `@NotBlank` | 중복 결제 방지 키 |

**Response (200)**

```json
{
  "paymentId": 99,
  "orderId": 42,
  "tossOrderId": "order-42-member-7-abc",
  "paymentKey": null,
  "amount": 50000,
  "paymentMethod": "CARD",
  "status": "PENDING",
  "requestedAt": "2026-05-20T10:00:00",
  "approvedAt": null
}
```

---

### 5-2. 결제 확인

```
POST /api/payments/confirm
```

Toss Payments에서 승인된 결제를 검증하고 확정합니다. 금액 위변조를 서버 측에서 재검증합니다.

**Request Body**

```json
{
  "paymentKey": "tgen_20260520abc",
  "tossOrderId": "order-42-member-7-abc",
  "amount": 50000
}
```

| 필드 | 타입 | 필수 | 검증 | 설명 |
|---|---|---|---|---|
| `paymentKey` | String | O | `@NotBlank` | Toss 발급 결제 키 |
| `tossOrderId` | String | O | `@NotBlank` | Toss 주문번호 (`orderId` alias 허용) |
| `amount` | Long | O | `@NotNull`, `@Positive` | 승인된 금액 (위변조 검증용) |

**Response (200)**

```json
{
  "paymentId": 99,
  "orderId": 42,
  "tossOrderId": "order-42-member-7-abc",
  "paymentKey": "tgen_20260520abc",
  "amount": 50000,
  "paymentMethod": "CARD",
  "status": "COMPLETED",
  "requestedAt": "2026-05-20T10:00:00",
  "approvedAt": "2026-05-20T10:01:30+09:00"
}
```

---

### 5-3. 결제 취소

```
POST /api/payments/{paymentId}/cancel
```

**Request Body**

```json
{
  "cancelReason": "단순 변심"
}
```

| 필드 | 타입 | 필수 | 검증 | 설명 |
|---|---|---|---|---|
| `cancelReason` | String | O | `@NotBlank`, `@Size(max=255)` | 취소 사유 (255자 이하) |

---

## 6. 채팅 REST

채팅방 관리(생성/조회/수정/삭제)와 참여자 관리, 메시지 이력 조회를 담당합니다.  
실시간 메시지 송수신은 [STOMP WebSocket](#7-채팅-websocket-stomp)으로 처리합니다.

| Method | Path | Auth | 설명 |
|---|---|---|---|
| POST | `/api/chat/rooms` | O | 채팅방 생성 |
| GET | `/api/chat/rooms` | O | 내 채팅방 목록 조회 |
| GET | `/api/chat/rooms/game/{gameId}` | O | 경기별 채팅방 조회 |
| GET | `/api/chat/rooms/{roomId}` | O | 채팅방 상세 조회 |
| PATCH | `/api/chat/rooms/{roomId}` | O | 채팅방 정보 수정 |
| DELETE | `/api/chat/rooms/{roomId}` | O | 채팅방 삭제 |
| PATCH | `/api/chat/rooms/{roomId}/archive` | O | 채팅방 아카이브 |
| PATCH | `/api/chat/rooms/{roomId}/unarchive` | O | 채팅방 아카이브 복원 |
| POST | `/api/chat/rooms/{roomId}/join` | O | 채팅방 입장 |
| DELETE | `/api/chat/rooms/{roomId}/leave` | O | 채팅방 나가기 |
| POST | `/api/chat/rooms/{roomId}/invite` | O | 참여자 초대 |
| PATCH | `/api/chat/rooms/{roomId}/notification` | O | 채팅 알림 설정 변경 |
| POST | `/api/chat/rooms/{roomId}/ban` | O | 멤버 BAN |
| GET | `/api/chat/messages/history/{roomId}` | O | 채팅 이력 조회 (읽음 미갱신) |
| GET | `/api/chat/messages/getMessages/{roomId}` | O | 채팅 메시지 조회 (읽음 자동 갱신) |
| DELETE | `/api/chat/messages/{messageId}` | O | 메시지 삭제 |

---

### 6-1. 채팅방 생성

```
POST /api/chat/rooms
```

DIRECT 타입은 같은 두 회원 간 중복 생성을 Advisory Lock으로 방지합니다.  
GAME 타입은 경기 ID와 연결되며 경기당 다수의 채팅방이 허용됩니다.

**Request Body**

```json
{
  "name": "KIA vs 삼성 응원방",
  "type": "GAME",
  "gameId": 1,
  "imageUrl": null
}
```

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `name` | String | O | 채팅방 이름 |
| `type` | String | O | `GAME` \| `DIRECT` |
| `gameId` | Long | N | 연결할 경기 ID (GAME 타입만) |
| `imageUrl` | String | N | 대표 이미지 URL |

**Response (201)**

```json
{
  "roomId": 5,
  "name": "KIA vs 삼성 응원방",
  "type": "GAME",
  "status": "ACTIVE",
  "participantCount": 1,
  "createdAt": "2026-05-20T10:00:00"
}
```

---

### 6-2. 내 채팅방 목록 조회

```
GET /api/chat/rooms?cursor=&limit=20
```

커서 기반 페이지네이션. 내가 참여 중인(JOINED) 채팅방을 최신순으로 반환합니다.

---

### 6-3. 경기별 채팅방 조회

```
GET /api/chat/rooms/game/{gameId}?cursor=&limit=20
```

특정 경기(`gameId`)에 연결된 GAME 타입 채팅방 목록을 반환합니다.

---

### 6-4. 채팅 이력 조회 (읽음 미갱신)

```
GET /api/chat/messages/history/{roomId}?cursor=&limit=50
```

`last_read_message_id`를 변경하지 않습니다. 이전 메시지 확인 용도로 사용합니다.

---

### 6-5. 채팅 메시지 조회 (읽음 자동 갱신)

```
GET /api/chat/messages/getMessages/{roomId}?cursor=&limit=50
```

조회 시 `last_read_message_id`를 자동 갱신합니다. 채팅방 진입 시 사용합니다.

---

### 6-6. 참여자 초대

```
POST /api/chat/rooms/{roomId}/invite?inviteeId={memberId}
```

초대된 회원은 `INVITED` 상태가 됩니다. 회원이 직접 입장(`/join`)해야 `JOINED`로 전환됩니다.

---

### 6-7. 멤버 BAN

```
POST /api/chat/rooms/{roomId}/ban?targetId={memberId}
```

방장(creator)만 가능. BAN된 회원은 재입장이 불가합니다.

---

## 7. 채팅 WebSocket (STOMP)

```
WebSocket Endpoint: ws://localhost:8080/ws/chat
```

연결 시 `Authorization: Bearer {accessToken}` 헤더로 인증합니다.  
구독 경로: `/topic/chat/room/{roomId}` (브로드캐스트)  
개인 경로: `/user/queue/chat` (개인 메시지)

| Mapping | Payload | 설명 |
|---|---|---|
| `/chat.send` | `ChatSendPayload` | 메시지 전송 |
| `/chat.read` | `ChatReadPayload` | 읽음 상태 갱신 |
| `/chat.typing` | `ChatTypingPayload` | 타이핑 인디케이터 |

### 메시지 전송

```json
// 송신 (SEND to /chat.send)
{
  "roomId": 5,
  "content": "안녕하세요!"
}

// 수신 (SUBSCRIBE /topic/chat/room/5)
{
  "messageId": 100,
  "roomId": 5,
  "senderId": 7,
  "senderNickname": "응원왕",
  "content": "안녕하세요!",
  "type": "TEXT",
  "createdAt": "2026-05-20T10:00:00"
}
```

---

## 8. 알림

Redis Streams 기반 비동기 파이프라인으로 알림을 발행·발송합니다.  
회원별 인박스(수신함), 실시간 SSE 연결, 외부 채널(Email/MQTT) 발송을 지원합니다.

| Method | Path | Auth | 설명 |
|---|---|---|---|
| GET | `/api/notifications` | O | 알림 목록 조회 (페이징) |
| PATCH | `/api/notifications/{notificationId}/read` | O | 알림 읽음 처리 |
| PATCH | `/api/notifications/read-all` | O | 전체 알림 읽음 처리 |
| GET | `/api/notifications/stream` | △ | SSE 실시간 연결 |
| GET | `/api/notifications/settings` | O | 알림 설정 조회 |
| PUT | `/api/notifications/settings` | O | 알림 설정 수정 |
| GET | `/api/notifications/channels` | O | 채널 목록 조회 |
| POST | `/api/notifications/channels` | O | 채널 등록 |
| DELETE | `/api/notifications/channels/{channelId}` | O | 채널 삭제 |
| PATCH | `/api/notifications/channels/{channelId}/toggle` | O | 채널 활성화/비활성화 토글 |

> △ SSE: 브라우저 `EventSource`의 헤더 제한으로 `?token={accessToken}` 쿼리 파라미터로 인증

---

### 8-1. 알림 목록 조회

```
GET /api/notifications?page=0&size=20
```

내 인박스 알림을 최신순으로 반환합니다.

**Response (200)**

```json
{
  "content": [
    {
      "id": 55,
      "eventType": "PAYMENT_COMPLETED",
      "payload": "{\"paymentId\":99,\"memberId\":7,\"amount\":50000}",
      "read": false,
      "createdAt": "2026-05-20T10:05:00"
    }
  ],
  "totalElements": 12,
  "totalPages": 1,
  "size": 20,
  "number": 0
}
```

| 필드 | 타입 | 설명 |
|---|---|---|
| `id` | Long | 알림 ID |
| `eventType` | String | `TICKET_OPEN` \| `GAME_START` \| `PAYMENT_COMPLETED` \| `CHAT_MENTION` |
| `payload` | String | 이벤트 원본 JSON (이벤트별 구조 상이) |
| `read` | Boolean | 읽음 여부 |
| `createdAt` | LocalDateTime | 수신 일시 |

**에러**

| 코드 | HTTP | 설명 |
|---|---|---|
| `UNAUTHORIZED` | 401 | 인증 필요 |

---

### 8-2. 알림 읽음 처리

```
PATCH /api/notifications/{notificationId}/read
```

**Response** : `204 No Content`

**에러**

| 코드 | HTTP | 설명 |
|---|---|---|
| `NOTIFICATION_NOT_FOUND` | 404 | 존재하지 않는 알림 |
| `NOTIFICATION_ALREADY_READ` | 400 | 이미 읽은 알림 |

---

### 8-3. 전체 알림 읽음 처리

```
PATCH /api/notifications/read-all
```

내 미읽음 알림 전체를 읽음 처리합니다.

**Response** : `204 No Content`

---

### 8-4. SSE 실시간 연결

```
GET /api/notifications/stream?token={accessToken}
Content-Type: text/event-stream
```

브라우저가 연결을 유지하는 동안 실시간으로 알림을 수신합니다.  
타임아웃은 **30분**이며, 만료 전 재연결해야 합니다.

**브라우저 연결 예시**

```javascript
const source = new EventSource(
  `/api/notifications/stream?token=${accessToken}`
);
source.addEventListener('notification', (e) => {
  console.log(e.data); // eventType 문자열
});
```

**수신 이벤트**

```
event: notification
data: PAYMENT_COMPLETED
```

> 이벤트 타입만 전달됩니다. 상세 내용은 알림 목록 조회 API로 확인합니다.

---

### 8-5. 알림 설정 조회

```
GET /api/notifications/settings
```

회원별 알림 수신 ON/OFF 설정을 반환합니다. 설정 항목이 없으면 모두 ON 상태의 기본값이 반환됩니다.

**Response (200)**

```json
{
  "ticketOpenAlert": true,
  "gameStartAlert": true,
  "paymentAlert": true,
  "chatMentionAlert": false
}
```

**에러**

| 코드 | HTTP | 설명 |
|---|---|---|
| `NOTIFICATION_SETTING_NOT_FOUND` | 404 | 알림 설정 없음 |

---

### 8-6. 알림 설정 수정

```
PUT /api/notifications/settings
```

4가지 알림 유형의 수신 여부를 한 번에 설정합니다. 전체 필드를 포함해야 합니다.

**Request Body**

```json
{
  "ticketOpenAlert": true,
  "gameStartAlert": true,
  "paymentAlert": true,
  "chatMentionAlert": false
}
```

| 필드 | 타입 | 필수 | 검증 | 설명 |
|---|---|---|---|---|
| `ticketOpenAlert` | Boolean | O | `@NotNull` | 티켓 오픈 알림 수신 여부 |
| `gameStartAlert` | Boolean | O | `@NotNull` | 경기 시작 30분 전 알림 수신 여부 |
| `paymentAlert` | Boolean | O | `@NotNull` | 결제 완료 알림 수신 여부 |
| `chatMentionAlert` | Boolean | O | `@NotNull` | 채팅 @멘션 알림 수신 여부 |

**Response (200)** : 수정된 설정 반환 (8-5와 동일 구조)

---

### 8-7. 채널 목록 조회

```
GET /api/notifications/channels
```

내가 등록한 외부 발송 채널 목록을 반환합니다. 채널 타입별 최대 1개 등록 가능합니다.

**Response (200)**

```json
[
  {
    "id": 3,
    "channelType": "EMAIL",
    "channelTarget": "user@example.com",
    "enabled": true
  }
]
```

---

### 8-8. 채널 등록

```
POST /api/notifications/channels
```

채널을 등록하면 해당 채널로 알림이 추가 발송됩니다. SSE는 항상 발송되며 채널 등록과 무관합니다.

**Request Body**

```json
{
  "channelType": "EMAIL",
  "channelTarget": "user@example.com"
}
```

| 필드 | 타입 | 필수 | 검증 | 설명 |
|---|---|---|---|---|
| `channelType` | String | O | `@NotNull` | `EMAIL` \| `MQTT` |
| `channelTarget` | String | O | `@NotBlank` | 이메일 주소 또는 MQTT 토픽 |

**Response (201)**

```json
{
  "id": 3,
  "channelType": "EMAIL",
  "channelTarget": "user@example.com",
  "enabled": true
}
```

**에러**

| 코드 | HTTP | 설명 |
|---|---|---|
| `NOTIFICATION_CHANNEL_ALREADY_EXISTS` | 409 | 이미 등록된 채널 타입 |
| `NOTIFICATION_CHANNEL_TYPE_UNSUPPORTED` | 400 | 지원하지 않는 채널 타입 |

---

### 8-9. 채널 삭제

```
DELETE /api/notifications/channels/{channelId}
```

**Response** : `204 No Content`

**에러**

| 코드 | HTTP | 설명 |
|---|---|---|
| `NOTIFICATION_CHANNEL_NOT_FOUND` | 404 | 존재하지 않는 채널 |

---

### 8-10. 채널 활성화/비활성화 토글

```
PATCH /api/notifications/channels/{channelId}/toggle
```

채널을 삭제하지 않고 일시적으로 비활성화하거나 재활성화합니다.

**Response (200)**

```json
{
  "id": 3,
  "channelType": "EMAIL",
  "channelTarget": "user@example.com",
  "enabled": false
}
```

**에러**

| 코드 | HTTP | 설명 |
|---|---|---|
| `NOTIFICATION_CHANNEL_NOT_FOUND` | 404 | 존재하지 않는 채널 |
