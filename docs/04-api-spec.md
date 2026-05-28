# API 명세서 — Sportsify

> **Base URL** : `https://api.sportsify.com` (로컬: `http://localhost:8080`)  
> **인증** : `Authorization: Bearer {accessToken}` 헤더 필요 (Auth Required = O인 경우)  
> **API Docs** : 앱 실행 후 `http://localhost:8080/docs.html` (Swagger UI + Redoc)

---
## 공통 응답 포맷

### 성공 응답

도메인 DTO를 HTTP 상태 코드와 함께 직접 반환합니다.

```json
// 200 OK — 단일 객체 예시
{
    "memberId": 1,
    "nickname": "응원왕",
    "email": "user@example.com"
}

// 200 OK — 배열 예시
[
    {
        "teamId": 1,
        "name": "KIA 타이거즈"
    },
    {
        "teamId": 2,
        "name": "FC 서울"
    }
]

// 204 No Content — 본문 없음
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

---

## 페이지네이션 (공통)

목록 API는 Cursor 기반 페이지네이션 적용.

### Query Parameter

| 파라미터     | 타입      | 필수 | 설명                     |
|----------|---------|----|------------------------|
| `cursor` | String  | N  | 이전 응답의 `nextCursor` 값  |
| `limit`  | Integer | N  | 페이지 크기 (기본 20, 최대 100) |

### 목록 응답 구조

```json
{
  "items": [],
  "nextCursor": "eyJpZCI6MTAwfQ==",
  "hasNext": true,
  "totalCount": 342
}
```

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
| GET | `/api/members` | O | 내 정보 조회 |
| PATCH | `/api/members/nickname` | O | 닉네임 수정 |
| DELETE | `/api/members` | O | 회원 탈퇴 |
| POST | `/api/members/favorite-teams` | O | 선호 팀 추가 |
| GET | `/api/members/favorite-teams` | O | 선호 팀 목록 조회 |
| PATCH | `/api/members/favorite-teams/{teamId}/priority` | O | 선호 팀 우선순위 수정 |
| DELETE | `/api/members/favorite-teams/{teamId}` | O | 선호 팀 삭제 |

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
GET /api/members
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
PATCH /api/members/nickname
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
DELETE /api/members
```

회원 status를 `WITHDRAWN`으로 변경합니다. 실제 데이터는 삭제되지 않습니다.

**Response** : `204 No Content`

---

### 1-8. 선호 팀 추가

```
POST /api/members/favorite-teams
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
GET /api/members/favorite-teams
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
PATCH /api/members/favorite-teams/{teamId}/priority
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
DELETE /api/members/favorite-teams/{teamId}
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

## 5. Chat 도메인

### 엔드포인트 목록

| method | path                                    | auth required | 설명                              |
|--------|-----------------------------------------|---------------|---------------------------------|
| POST   | /api/chat/rooms                         | O             | 채팅방 생성 (5-1)                    |
| GET    | /api/chat/rooms                         | O             | 내 채팅방 목록 조회 (5-2)               |
| GET    | /api/chat/rooms/game/{gameId}           | N             | 게임별 채팅방 조회 (5-3)                |
| GET    | /api/chat/rooms/{roomId}                | O/N           | 채팅방 상세 조회 (5-4)                 |
| PATCH  | /api/chat/rooms/{roomId}                | O             | 채팅방 정보 수정 name/imageUrl (5-5)   |
| DELETE | /api/chat/rooms/{roomId}                | O             | 채팅방 삭제 (5-6, 방장만 가능)            |
| PATCH  | /api/chat/rooms/{roomId}/archive        | O             | 채팅방 아카이브 (5-7)                  |
| PATCH  | /api/chat/rooms/{roomId}/unarchive      | O             | 채팅방 아카이브 복원 (5-8)               |
| POST   | /api/chat/rooms/{roomId}/join           | O             | 채팅방 입장 (5-9)                    |
| DELETE | /api/chat/rooms/{roomId}/leave          | O             | 채팅방 나가기 (5-10)                  |
| POST   | /api/chat/rooms/{roomId}/invite         | O             | 참여자 초대 (5-11)                   |
| PATCH  | /api/chat/rooms/{roomId}/notification   | O             | 채팅방 알림 설정 변경 (5-12)             |
| POST   | /api/chat/rooms/{roomId}/ban            | O             | 멤버 BAN (5-13)                   |
| GET    | /api/chat/rooms/getMyInvites            | O             | 내 초대 목록 조회 (5-14)               |
| POST   | /api/chat/rooms/{roomId}/reject         | O             | 초대 거부 (5-15)                    |
| GET    | /api/chat/messages/history/{roomId}     | O             | 채팅 이력 조회 — 내가 있을 때 메시지 (5-16)   |
| WS     | /ws/chat                                | O/N           | websocket 연결(5-17-1)            |
| SUB    | /topic/rooms/{roomId}                   | O/N           | 구독 (5-17-2-1)                   |
| UNSUB  | id:subscription-id                      | O/N           | 구독 취소 (5-17-2-2)                |
| PUB    | SEND  /app/chat.send                    | O             | 메시지 전송 (5-17-3-1)               |
| PUB    | SEND  /app/chat.read                    | O             | 읽음 상태 갱신 (5-17-3-2)             |
| PUB    | SEND  /app/chat.typing                  | O             | 타이핑 인디케이터 (5-17-3-3)            |
| DELETE | /api/chat/messages/{messageId}          | O             | 메시지 삭제 soft delete (5-18)       |
| GET    | /api/chat/messages/getMessages/{roomId} | O             | 채팅방 메시지 조회 + lastRead 갱신 (5-19) |

---

### 5-1. 채팅방 생성

```
POST /api/chat/rooms
```

Auth Required: **O**

Request Body

| 필드           | 타입      | 필수  | 설명                                                 |
|--------------|---------|-----|----------------------------------------------------|
| `type`       | String  | Y   | 채팅방 유형 (`GAME` \| `DIRECT`)                        |
| `name`       | String* | Y/N | 채팅방 표시 이름. `DIRECT` 의 경우 자동 생성되어 생략 가능. `GAME`은 필수 |
| `imageUrl`   | String* | N   | 채팅방 프로필 이미지 URL                                    |
| `gameId`     | Long*   | Y/N | `type=GAME` 일 때 필수                                 |
| `inviteeIds` | Long[]* | Y/N | `DM` / 비공개 방 생성 시 함께 초대할 사용자 ID 목록                 |

Response Body

```json
{
  "success": true,
  "data": {
    "roomId": 201,
    "type": "GAME",
    "gameId": 101,
    "name": "KIA vs 삼성 경기 채팅",
    "imageUrl": null,
    "createdBy": 9,
    "createdAt": "2026-04-21T09:00:00Z"
  }
}
```

Validation / Business Rules

- 인증된 사용자만 생성 가능.
- 동일 사용자 간 `DM` 중복 생성 시 기존 방을 반환 (`DUPLICATE_DIRECT_ROOM` 대신 200 OK + 기존 방).
- `roomId` 는 서버에서 발급, 클라이언트가 보낸 값은 무시.
- `type=GAME` 인 경우 `gameId` 가 존재 여부를 외부 컨텍스트 호출로 검증.

---

### 5-2. 내 채팅방 목록 조회

```
GET /api/chat/rooms
```

Auth Required: **O**

Query Parameter

| 파라미터     | 타입      | 필수 | 설명                      |
|----------|---------|----|-------------------------|
| `type`   | String  | Y  | 채팅방 유형 (`GAME` \| `DM`) |
| `cursor` | Integer | N  | 페이지네이션 커서               |
| `limit`  | Integer | N  | 페이지 크기 (기본 20, 최대 100)  |

Response Body

```
{
  "items": [
    {
      "roomId": 201,
      "type": "GAME",
      "gameId": 101,
      "title": "KIA vs 삼성 경기 채팅",
      "imageUrl": null,
      "currentParticipants": 432,
      "lastMessage": {
        "messageId": 9981,
        "content": "방금 홈런!!",
        "type": "TEXT",
        "createdAt": "2026-04-27T14:22:15Z"
      },
      "unRead": 50,
      "notificationEnabled": true,
      "createdAt": "2026-04-21T09:00:00Z",
      "updatedAt": "2026-04-27T14:22:15Z"
    }
  ],
  "nextCursor": 0,
  "hasNext": false,
  "totalCount": 5

}
```

Response Field

| 필드                    | 타입             | 설명                     |
|-----------------------|----------------|------------------------|
| `roomId`              | Long           | 채팅방 ID                 |
| `type`                | String         | 채팅방 유형                 |
| `gameId`              | Long \| null   | 연결된 경기 ID (`GAME` 일 때) |
| `title`               | String         | 채팅방 표시 이름              |
| `imageUrl`            | String \| null | 프로필 이미지                |
| `currentParticipants` | Integer        | 현재 참여 인원               |
| `lastMessage`         | Object \| null | 마지막 메시지 요약             |
| `unRead`              | Long           | 읽지 않은 message 수        |
| `notificationEnabled` | Boolean        | 해당 방 알림 수신 여부          |
| `createdAt`           | DateTime       | 생성 시각                  |
| `updatedAt`           | DateTime       | 마지막 갱신 시각              |
| `nextCursor`          | Long \| null   | 다음 page 커서             |
| `hasNext`             | Boolean        | 다음 page 존재여부           |
| `totalCount`          | Integer        | 가져온 채팅방 목록의 수          |

Validation / Business Rules

- 인증된 사용자만 조회 가능.
- `JOINED` 상태인 방만 반환 (`LEFT`, `BANNED` 제외).

---

### 5-3. 게임별 채팅방 조회

```
GET /api/chat/rooms/game/{gameId}
```

Auth Required: **N**

Path Parameter

| 파라미터     | 타입     | 필수 | 설명    |
|----------|--------|----|-------|
| `gameId` | String | Y  | 게임 Id |

Query Parameter

| 파라미터     | 타입           | 필수 | 설명                     |
|----------|--------------|----|------------------------|
| `cursor` | Long \| null | N  | 페이지네이션 커서              |
| `limit`  | Integer      | N  | 페이지 크기 (기본 20, 최대 100) |

Response Body

```
{
  "success": true,
  "data": {
    "items": [
      {
        "roomId": 305,
        "type": "GAME",
        "gameId": 5,
        "title": "KIA 타이거즈 응원방",
        "imageUrl": "https://cdn.example.com/teams/kia.png",
        "currentParticipants": 2154,
        "createdAt": "2026-03-01T00:00:00Z"
      }
    ],
    "nextCursor": null,
    "hasNext": false,
    "totalCount": 1
  }
}
```

Response Field

| 필드                    | 타입             | 설명            |
|-----------------------|----------------|---------------|
| `roomId`              | Long           | 채팅방 ID        |
| `type`                | String         | 채팅방 유형        |
| `gameId`              | Long \| null   | 연결된 경기 ID     |
| `title`               | String         | 채팅방 표시 이름     |
| `imageUrl`            | String \| null | 프로필 이미지       |
| `currentParticipants` | Integer        | 현재 참여 인원      |
| `createdAt`           | DateTime       | 생성 시각         |
| `nextCursor`          | Long \| null   | 다음 page 커서    |
| `hasNext`             | Boolean        | 다음 page 존재여부  |
| `totalCount`          | Integer        | 가져온 채팅방 목록의 수 |

Validation / Business Rules

- 비인증 사용자도 조회 가능 (둘러보기 용).
- `type = GAME` 이고 `status = ACTIVE` 인 방만 반환.

---

### 5-4. 채팅방 상세 조회

```
GET /api/chat/rooms/{roomId}
```

Auth Required: **O/N**

Path Parameter

| 파라미터     | 타입   | 필수 | 설명     |
|----------|------|----|--------|
| `roomId` | Long | Y  | 채팅방 ID |

Response Body

```
{
  "success": true,
  "data": {
    "roomId": 201,
    "type": "GAME",
    "gameId": 101,
    "title": "KIA vs 삼성 경기 채팅",
    "imageUrl": null,
    "currentParticipants": 432,
    "createdBy": 9,
    "createdAt": "2026-04-21T09:00:00Z",
    "myMembership": {
      "status": "JOINED",
      "notificationEnabled": true,
      "lastReadMessageId": 9974,
      "joinedAt": "2026-04-21T09:00:00Z"
    }
  }
}
```

Response Field

| 필드                    | 타입             | 설명                          |
|-----------------------|----------------|-----------------------------|
| `success`             | Boolean        | 성공여부                        |
| `roomId`              | Long           | 채팅방 ID                      |
| `type`                | String         | 채팅방 유형                      |
| `gameId`              | Long \| null   | 연결된 경기 ID                   |
| `title`               | String         | 채팅방 표시 이름                   |
| `imageUrl`            | String \| null | 프로필 이미지                     |
| `currentParticipants` | Integer        | 현재 참여 인원                    |
| `createdBy`           | Long           | 생성자                         |
| `createdAt`           | DateTime       | 생성 시각                       |
| `status`              | String         | 사용자의 채팅방 상태                 |
| `notificationEnabled` | Boolean        | 사용자의 채팅방 알림 여부              |
| `lastReadMessageId`   | Integer        | 사용자의 채팅방의 마지막 읽은 message id |
| `joinedAt`            | DateTime       | 사용자의 채팅방 입장 날짜              |

Validation / Business Rules

- `GAME`  은 비멤버도 메타 정보 조회 가능 (단 `myMembership = null`).
- `DM` 은 멤버만 조회 가능 (`FORBIDDEN`).
- 삭제된 방은 `ROOM_NOT_FOUND`.

---

### 5-5. 채팅방 정보 수정

```
PATCH /api/chat/rooms/{roomId}
```

Auth Required: **O**

Path Parameter

| 파라미터     | 타입   | 필수 | 설명     |
|----------|------|----|--------|
| `roomId` | Long | Y  | 채팅방 ID |

Request Body

| 필드         | 타입     | 필수 | 설명                          |
|------------|--------|----|-----------------------------|
| `title`    | String | N  | 새 제목 (1~100자)               |
| `imageUrl` | String | N  | 새 이미지 URL (빈 문자열 → null 처리) |

Response Body

```
{
  "success": true,
  "data": {
    "roomId": 201,
    "title": "KIA vs 삼성 응원방",
    "imageUrl": "https://cdn.example.com/rooms/201.png",
    "updatedAt": "2026-04-27T15:00:00Z"
  }
}
```

Response Field

| 필드          | 타입             | 설명          |
|-------------|----------------|-------------|
| `success`   | String         | 성공여부        |
| `roomId`    | Long           | 채팅방 ID      |
| `title`     | String         | 채팅방 표시 이름   |
| `imageUrl`  | String \| null | 프로필 이미지     |
| `updatedAt` | DateTime       | 마지막 엡데이트 날짜 |

Validation / Business Rules

- 인증된 사용자 + 해당 채팅방의 활성 멤버만 가능.
- `DM` 은 수정 불가 (`FORBIDDEN`).
- 변경 후 `ROOM_UPDATED` STOMP 이벤트가 같은 방 멤버에게 브로드캐스트된다.

---

### 5-6. 채팅방 입장

```
POST /api/chat/rooms/{roomId}/join
```

Auth Required: **O**

Path Parameter

| 파라미터     | 타입   | 필수 | 설명     |
|----------|------|----|--------|
| `roomId` | Long | Y  | 채팅방 ID |

Response Body

```
{
  "success": true,
  "data": {
    "roomId": 201,
    "memberId": 9,
    "status": "JOINED",
    "joinedAt": "2026-04-27T15:01:11Z"
  }
}
```

Response Field

| 필드         | 타입       | 설명               |
|------------|----------|------------------|
| `success`  | String   | 성공여부             |
| `roomId`   | Long     | 채팅방 ID           |
| `memberId` | Long     | 사용자 ID           |
| `status`   | String   | 사용자의 채팅방에서의 현 상태 |
| `joinedAt` | DateTime | 사용자의 채팅방 입장 날짜   |

Validation / Business Rules

- 인증된 사용자만 입장 가능.
- `DM` / 비공개 방은 사전에 `INVITED` 상태여야 입장 가능.
- 이미 `JOINED` → `ALREADY_JOINED`.
- `BANNED` → `BANNED_MEMBER`.
- 입장 성공 시 `MEMBER_JOINED` 이벤트가 같은 방 멤버에게 브로드캐스트된다.

---

### 5-7. 채팅방 아카이브

```
PATCH /api/chat/rooms/{roomId}/archive
```

Auth Required: **O**

Path Parameter

| 파라미터     | 타입   | 필수 | 설명     |
|----------|------|----|--------|
| `roomId` | Long | Y  | 채팅방 ID |

Response Body

```json
{
  "roomId": 12,
  "status": "ARCHIVED",
  "updatedAt": "2026-04-27T14:22:15Z"
}
```

Response Field

| 필드          | 타입       | 설명               |
|-------------|----------|------------------|
| `roomId`    | Long     | 채팅방 ID           |
| `status`    | String   | 사용자의 채팅방에서의 현 상태 |
| `updatedAt` | DateTime | 채팅방 마지막 업데이트 일   |

Validation / Business Rules

- 인증된 사용자 + 해당 채팅방의 생성자 멤버만 가능.
- 변경 후 `ROOM_ARCHIVED` STOMP 이벤트가 같은 방 멤버에게 브로드캐스트된다.

---

### 5-8. 채팅방 아카이브 복원

```
PATCH /api/chat/rooms/{roomId}/unarchive
```

Auth Required: **O**

Path Parameter

| 파라미터     | 타입   | 필수 | 설명     |
|----------|------|----|--------|
| `roomId` | Long | Y  | 채팅방 ID |

Response Body

```json
{
  "roomId": 12,
  "status": "UNARCHIVED",
  "updatedAt": "2026-04-27T14:22:15Z"
}
```

Response Field

| 필드          | 타입       | 설명               |
|-------------|----------|------------------|
| `roomId`    | Long     | 채팅방 ID           |
| `status`    | String   | 사용자의 채팅방에서의 현 상태 |
| `updatedAt` | DateTime | 채팅방 마지막 업데이트 일   |

Validation / Business Rules

- 인증된 사용자 + 해당 채팅방의 생성자 멤버만 가능.
- 변경 후 `ROOM_UNARCHIVED` STOMP 이벤트가 같은 방 멤버에게 브로드캐스트된다.

---

### 5-9. 채팅방 삭제

```
DELETE /api/chat/rooms/{roomId}
```

Auth Required: **O**

Path Parameter

| 파라미터     | 타입   | 필수 | 설명     |
|----------|------|----|--------|
| `roomId` | Long | Y  | 채팅방 ID |

Response Body

```json
{
  
}
```

Response Field

Validation / Business Rules

- 인증된 사용자 + 해당 채팅방의 생성자 멤버만 가능.
- 삭제 후 `ROOM_DELETE` STOMP 이벤트가 같은 방 멤버에게 브로드캐스트된다.

---

### 5-10. 채팅방 나가기

```
DELETE /api/chat/rooms/{roomId}/leave
```

Auth Required: **O**

Path Parameter

| 파라미터     | 타입   | 필수 | 설명     |
|----------|------|----|--------|
| `roomId` | Long | Y  | 채팅방 ID |

Response Body

```json
{
  "roomId": 201,
  "memberId": 9,
  "status": "LEFT",
  "updatedAt": "2026-04-27T15:10:00Z"
}
```

Response Field

| 필드          | 타입       | 설명                      |
|-------------|----------|-------------------------|
| `success`   | String   | 성공여부                    |
| `roomId`    | Long     | 채팅방 ID                  |
| `memberId`  | Long     | 사용자 ID                  |
| `status`    | String   | 사용자의 채팅방에서의 현 상태        |
| `updatedAt` | DateTime | 사용자의 채팅방에서의 마지막 업데이트 날짜 |

Validation / Business Rules

- 인증된 사용자 + 해당 방의 멤버만 가능.
- 이미 `LEFT` → `ALREADY_LEFT`.
- 나간 후 `MEMBER_LEFT` STOMP 이벤트가 같은 방 멤버에게 브로드캐스트된다.

---

### 5-11. 참여자 초대

```
POST /api/chat/rooms/{roomId}/invite
```

Auth Required: **O**

Path Parameter

| 파라미터     | 타입   | 필수 | 설명     |
|----------|------|----|--------|
| `roomId` | Long | Y  | 채팅방 ID |

Request Body

| 필드          | 타입   | 필수 | 설명         |
|-------------|------|----|------------|
| `inviteeId` | Long | Y  | 초대할 사용자 ID |

Response Body

```json
{
  "roomId": 24,
  "memberId": 466,
  "status": "INVITED",
  "joinedAt: "2026-04-27T14:22:15Z"
}
```

Response Field

| 필드         | 타입       | 설명               |
|------------|----------|------------------|
| `roomId`   | Long     | 채팅방 ID           |
| `memberId` | Long     | 사용자 ID           |
| `status`   | String   | 사용자의 채팅방에서의 현 상태 |
| `joinedAt` | DateTime | 사용자의 채팅방에서의 참가일  |

Validation / Business Rules

- 초대 가능 대상은 `DM` 외의 비공개 그룹 방. (공개 방은 그냥 join)
- 초대자 본인이 해당 방의 멤버여야 함.
- 이미 `JOINED` / `BANNED` 인 사용자는 `skipped` 에 사유와 함께 반환.
- 초대 후 `MEMBER_INVITED` STOMP 이벤트가 같은 방 멤버에게 브로드캐스트된다.

---

### 5-12. 알림 설정 변경

```
PATCH /api/chat/rooms/{roomId}/notification
```

Auth Required: **O**

Path Parameter

| 파라미터     | 타입   | 필수 | 설명     |
|----------|------|----|--------|
| `roomId` | Long | Y  | 채팅방 ID |

Request Body

| 필드        | 타입      | 필수 | 설명       |
|-----------|---------|----|----------|
| `enabled` | Boolean | Y  | 알림 수신 여부 |

Response Body

```json
{
  "roomId": 201,
  "memberId": 9,
  "notificationEnabled": false,
  "updatedAt": "2026-04-27T15:20:00Z"
}
```

Response Field

| 필드                    | 타입       | 설명                      |
|-----------------------|----------|-------------------------|
| `success`             | String   | 성공여부                    |
| `roomId`              | Long     | 채팅방 ID                  |
| `notificationEnabled` | String   | 사용자의 채팅방에서의 아림 여부       |
| `updatedAt`           | DateTime | 사용자의 채팅방에서의 마지막 업데이트 날짜 |

Validation / Business Rules

- 인증된 사용자 + 해당 방의 활성 멤버만 가능.

---

### 5-13. 채팅방 멤버 ban

```
POST /api/chat/rooms/{roomId}/ban
```

Auth Required: **O**

Path Parameter

| 파라미터     | 타입   | 필수 | 설명     |
|----------|------|----|--------|
| `roomId` | Long | Y  | 채팅방 ID |

Request Parameter

| 필드         | 타입   | 필수 | 설명             |
|------------|------|----|----------------|
| `targetId` | Long | Y  | ban할 채팅방 멤버 ID |

Response Body

```json
{
  "roomId": 201,
  "memberId": 9,
  "status": "BANNED",
  "updatedAt": "2026-04-27T15:20:00Z"
}
```

Response Field

| 필드          | 타입       | 설명                      |
|-------------|----------|-------------------------|
| `roomId`    | Long     | 채팅방 ID                  |
| `memberId`  | Long     | 채팅방 멤버 ID               |
| `status`    | String   | 사용자의 채팅방에서의 상태          |
| `updatedAt` | DateTime | 사용자의 채팅방에서의 마지막 업데이트 날짜 |

Validation / Business Rules

- 인증된 사용자 + 해당 방의 활성 멤버만 가능.
- 초대 후 `MEMBER_BANNED` STOMP 이벤트가 같은 방 멤버에게 브로드캐스트된다.

---

### 5-14. 내 초대 목록 조회

```
GET /api/chat/rooms/getMyInvites
```

Auth Required: **O**

Response Body

```json
{
  "invites": [
    {
      "roomId": 201,
      "status": "INVITED",
      "updatedAt": "2026-04-27T15:20:00Z"
  }
  ]

}
```

Response Field

| 필드          | 타입       | 설명                      |
|-------------|----------|-------------------------|
| `roomId`    | Long     | 채팅방 ID                  |
| `status`    | String   | 사용자의 채팅방에서의 상태          |
| `updatedAt` | DateTime | 사용자의 채팅방에서의 마지막 업데이트 날짜 |

Validation / Business Rules

- 인증된 사용자만 가능.

---

### 5-15. 초대 거부

```
POST /api/chat/rooms/{roomId}/reject
```

Auth Required: **O**

Path Parameter

| 파라미터     | 타입   | 필수 | 설명     |
|----------|------|----|--------|
| `roomId` | Long | Y  | 채팅방 ID |

Response Body

```json
{
}
```

Validation / Business Rules

- 인증된 사용자 + 해당 초대 대상만 가능.

---

### 5-16. 채팅 이력 조회

```
GET /api/chat/messages/history/{roomId}
```

Auth Required: **O**

Path Parameter

| 파라미터     | 타입   | 필수 | 설명     |
|----------|------|----|--------|
| `roomId` | Long | Y  | 채팅방 ID |

Query Parameter

| 파라미터     | 타입      | 필수 | 설명                                    |
|----------|---------|----|---------------------------------------|
| `cursor` | Long    | N  | 이 메시지 ID 보다 과거의 것만 조회. 미지정 시 최신 메시지부터 |
| `limit`  | Integer | N  | 페이지 크기 (기본 30, 최대 100)                |

Response Body

```json
{
  "messages": [
    {
      "messageId": 9981,
      "roomId": 201,
      "type": "TEXT",
      "content": "방금 홈런!!",
      "status": "ACTIVE",
      "createdAt": "2026-04-27T14:22:15Z"
    },
    {
      "messageId": 9980,
      "roomId": 201,
      "type": "IMAGE",
      "content": "https://cdn.example.com/chat/9980.png",
      "status": "ACTIVE",
      "createdAt": "2026-04-27T14:21:00Z"
    }
  ],
  "members": [
    {
      "memberId": 12,
      "lastReadMessageId": 554  
     }
  ],
  "nextCursor": 9980,
  "hasNext": true,
  "totalCount": 200
}
```

Response Field

| 필드           | 타입           | 설명                                   |
|--------------|--------------|--------------------------------------|
| `messageId`  | Long         | 메시지 ID                               |
| `roomId`     | Long         | 채팅방 ID                               |
| `senderId`   | Long         | 발신자 ID (시스템 메시지는 0)                  |
| `type`       | String       | `TEXT` / `IMAGE` / `FILE` / `SYSTEM` |
| `content`    | String       | 텍스트 본문 또는 파일 URL                     |
| `status`     | String       | `ACTIVE` / `DELETED`                 |
| `createdAt`  | DateTime     | 생성 시각                                |
| `nextCursor` | Long \| null | 다음 페이지 커서                            |
| `hasNext`    | Boolean      | 다음 페이지 존재 여부                         |
| `totalCount` | int          | 검색된 item 수                           |

Validation / Business Rules

- 인증된 사용자 + 해당 방의 멤버 (`DM`/`GROUP` 비공개 방의 경우)만 조회 가능.
- `GAME`  공개 방은 비멤버도 조회 가능하도록 옵션 가능 (정책에 따라 결정).
- 정렬: `messageId DESC` (최신순).
- `DELETED` 메시지는 `content` 가 `"[삭제된 메시지입니다]"` 로 마스킹되어 반환.
- 가져온 마지막 message의 id가 read로 전송
- 채팅방 멤버에게 `read` 이벤트 발송.

---

### 5-17. WebSocket (STOMP)

### 5-17-1. 연결 (CONNECT)

```
WS  /ws/chat   (STOMP over WebSocket, SockJS fallback 지원)
```

CONNECT 헤더

| 헤더               | 필수 | 설명                     |
|------------------|----|------------------------|
| `Authorization`  | Y  | `Bearer {accessToken}` |
| `accept-version` | Y  | `1.1,1.2`              |
| `heart-beat`     | N  | `10000,10000`          |

연결 시 처리

- 인증 실패 시 STOMP `ERROR` 프레임 후 연결 종료.
- 서버는 `userId ↔ session ↔ subscriptions` 매핑을 in-memory(예: ConcurrentHashMap) 에 저장.
- 동일 사용자의 다중 디바이스 접속 허용 (sessionId 별로 분리 관리).
- 연결 성공 시 `/user/queue/system` 으로 다음 페이로드를 푸시:

```json
{
  "event": "CONNECTED",
  "userId": 9,
  "sessionId": "ws-3f1a..."
}
```

### 5-17-2. 구독 (SUBSCRIBE)

#### 5-17-2-1. 구독 destination

| Destination             | 설명                             |
|-------------------------|--------------------------------|
| `/topic/rooms/{roomId}` | 해당 방의 모든 이벤트 (메시지/입퇴장/수정/읽음 등) |

CONNECT 헤더

| 헤더              | 필수 | 설명                          |
|-----------------|----|-----------------------------|
| `id`            | Y  | `subscription id `          |
| `lastMessageId` | Y  | `lastMessageId {MessageId}` |

#### SUBSCRIBE Example

```text
SUBSCRIBE
id:sub-room-10
destination:/topic/rooms/10
lastMessageId:532
```

- 구독 시점에 서버가 `roomId` 에 대한 멤버 권한을 검증한다. 비멤버가 비공개 방을 구독하면 `ERROR` 프레임 반환.
- 재연결시 마지막으로 받은 lastMessageId를 header로 같이 subscribe 한다

#### 5-17-3. 구독 해제 (UNSUBSCRIBE)

CONNECT 헤더

| 헤더   | 필수 | 설명                |
|------|----|-------------------|
| `id` | Y  | `subscription id` |

#### UNSUBSCRIBE Example

```text
UNSUBSCRIBE
id:sub-room-10
```

- UNSUBSCRIBE 는 destination 기반이 아니라 `subscription id` 기반으로 처리한다.
- 서버는 `sessionId + subscriptionId` 조합으로 구독 정보를 제거한다.
- 구독 해제 후 해당 subscription 에 대한 이벤트 전달은 중단된다.

### 5-17-3. 발신 destination (SEND)

#### 5-17-3-1. 메시지 전송

```
SEND  /app/chat.send
```

Payload

```json
{
  "clientMessageId": "cm-9b2f-...",
  "roomId": 201,
  "type": "TEXT",
  "content": "방금 홈런!!"
}
```

| 필드                | 타입     | 필수 | 설명                                                         |
|-------------------|--------|----|------------------------------------------------------------|
| `clientMessageId` | String | Y  | 클라이언트가 발급한 임시 ID (가짜 message id). 재전송 시 동일 값 유지하여 중복 저장 방지 |
| `roomId`          | Long   | Y  | 대상 채팅방 ID                                                  |
| `type`            | String | Y  | `TEXT` / `IMAGE` / `FILE`                                  |
| `content`         | String | Y  | 본문 또는 업로드 결과 URL                                           |

Server → Client (성공)

`/topic/rooms/{roomId}/messages` 로 브로드캐스트:

```json
{
  "event": "MESSAGE_SENT",
  "roomId": 201,
  "occurredAt": "2026-04-27T14:22:30Z",
  "messageId": null,
  "payload": {
    "messageId": 9982,
    "clientMessageId": "cm-9b2f-...",
    "senderId": 9,
    "type": "TEXT",
    "content": "방금 홈런!!"
  }

}
```

Server → Sender (실패)

`/user/queue/system` 으로 송신자 본인에게만:

```json
{
  "event": "MESSAGE_FAILED",
  "clientMessageId": "cm-9b2f-...",
  "errorCode": "INVALID_INPUT",
  "message": "MessageContent must be <= 500 characters but 600"
}
```

Validation / Business Rules

- 인증된 사용자 + (비공개 방이면) 활성 멤버만 발송 가능.
- `clientMessageId` 가 동일한 요청은 멱등 처리 (이미 저장된 messageId 를 재반환).
- 동일 채팅방 메시지는 `messageId` (BIGSERIAL) 의 단조 증가로 순서 보장.

---

#### 5-17-3-2. 읽음 상태 갱신

```
SEND  /app/chat.read
```

Payload

```json
{
  "roomId": 201,
  "lastReadMessageId": 9982
}
```

| 필드                  | 타입   | 필수 | 설명                   |
|---------------------|------|----|----------------------|
| `roomId`            | Long | Y  | 채팅방 ID               |
| `lastReadMessageId` | Long | Y  | 사용자가 마지막으로 읽은 메시지 ID |

처리

1. 사용자/방 권한 검증.
2. Redis `chat:room:{roomId}:user:{userId}:lastRead` 와 비교 후 더 큰 경우에만 갱신 (단조 증가 검증).
3. 같은 방의 다른 멤버에게 `/topic/rooms/{roomId}` 로 multicast.
4. DB 영속화는 별도 스케줄러(2~3초 주기) 또는 disconnect/shutdown 시점에서 일괄 반영.

Server → Other Members

```json
{
  "event": "READ_UPDATED",
  "roomId": 201,
  "messageId": null,
  "occurredAt": "2026-04-27T14:22:35Z",
  "payload" : {
    "memberId": 9,
    "lastReadMessageId": 9982
  }
  
}
```

Validation / Business Rules

- `DM` / 비공개 방 멤버에게만 의미. 공개 방의 읽음 표시는 정책에 따라 비활성화 가능.
- 이전 값보다 작거나 같은 `lastReadMessageId` 는 무시(역행 방지).

---

#### 5-17-3-3. 타이핑 인디케이터

```
SEND  /app/chat.typing
```

Payload

```json
{
  "roomId": 201,
  "typing": true
}
```

| 필드       | 타입      | 필수 | 설명        |
|----------|---------|----|-----------|
| `roomId` | Long    | Y  | 채팅방 ID    |
| `typing` | Boolean | Y  | 타이핑 시작/종료 |

Server → Other Members (`/topic/rooms/{roomId}/typing`)

```json
{
  "event": "TYPING",
  "roomId": 201,
  "userId": 9,
  "typing": true,
  "occurredAt": "2026-04-27T14:22:36Z"
}
```

Validation / Business Rules

- 휘발성 이벤트, DB/Redis 영속화 없음.
- 같은 사용자가 5초 이내에 다시 `typing=true` 를 보내면 디바운스로 throttle 권장.

---

#### 5-17-3-4. 서버 발신 이벤트 종류 (`/topic/rooms/{roomId}` 구독자에게 푸시)

| event             | 발생 시점       | 페이로드 핵심 필드                                                    |
|-------------------|-------------|---------------------------------------------------------------|
| `MESSAGE_SENT`    | 새 메시지 발송 성공 | `messageId`, `clientMessageId`, `senderId`, `type`, `content` |
| `MESSAGE_DELETED` | 메시지 삭제 성공   | `messageId`                                                   |
| `MEMBER_JOINED`   | 사용자 입장      | `inviterId`, `invitedId`                                      |
| `MEMBER_LEFT`     | 사용자 나감      | `memberId`                                                    |
| `MEMBER_INVITED`  | 새 사용자 초대    | `inviterId`, `invitedId`                                      |
| `MEMBER_BANNED`   | 사용자 ban     | `memberId`                                                    |
| `ROOM_UPDATED`    | 채팅방 메타 변경   | `name`, `imageUrl`                                            |
| `ROOM_DELETED`    | 채팅방 삭제      |                                                               |
| `ROOM_ARCHIVED`   | 채팅방 영속화     |                                                               |
| `ROOM_UNARCHIVED` | 채팅방 영속화 취소  |                                                               |
| `READ_RECEIPT`    | 읽음 상태 갱신    | `memberId`, `lastReadMessageId`                               |
| `TYPING`          | 타이핑 인디케이터   | `userId`, `typing`                                            |

각 이벤트는 다음 공통 envelope 를 따른다.

```json
{
  "event": "MESSAGE_SENT",
  "roomId": 201,
  "messageId": null,
  "occurredAt": "2026-04-27T14:22:30Z",
  "payload": { ... 이벤트별 필드 ... }
}
```

---

#### 5-17-3-5. DISCONNECT

- STOMP `DISCONNECT` 또는 TCP 연결 종료 시 서버는 다음을 수행한다:
    - in-memory `userId ↔ session` 매핑에서 해당 sessionId 제거.
    - Redis 의 `chat:room:{roomId}:online:{userId}` (해당 사용자가 구독 중이던 방들) 정리.
    - 마지막 read 값을 DB 에 flush.
- 동일 사용자의 다른 세션이 남아있다면 사용자 단위의 online 상태는 유지.

---


---

### 5-18. 메시지 삭제

```
DELETE /api/chat/messages/{messageId}
```

Auth Required: **O**

Path Parameter

| 파라미터        | 타입   | 필수 | 설명     |
|-------------|------|----|--------|
| `messageId` | Long | Y  | 메시지 ID |

Response Body

```json
{
  "messageId": 9981,
  "roomId": 201,
  "status": "DELETED",
  "updatedAt": "2026-04-27T15:30:00Z"
}
```

Validation / Business Rules

- 인증된 사용자 + 해당 방의 멤버만 가능.
- 본인이 보낸 메시지만 삭제 가능 (`FORBIDDEN`).
- 이미 `DELETED` → 멱등 처리 (200 OK).
- 삭제 성공 시 `chat.message.deleted` 이벤트가 같은 방 멤버에게 브로드캐스트.

---

### 5-19. 채팅방 메시지 조회

```
GET /api/chat/messages/getMessages/{roomId}
```

Auth Required: **O**

Path Parameter

| 파라미터     | 타입   | 필수 | 설명     |
|----------|------|----|--------|
| `roomId` | Long | Y  | 채팅방 ID |

Query Parameter

| 파라미터     | 타입      | 필수 | 설명                                    |
|----------|---------|----|---------------------------------------|
| `cursor` | Long    | N  | 이 메시지 ID 보다 과거의 것만 조회. 미지정 시 최신 메시지부터 |
| `limit`  | Integer | N  | 페이지 크기 (기본 30, 최대 100)                |

Response Body

```json
{

  "items": [
    {
      "messageId": 9981,
      "roomId": 201,
      "type": "TEXT",
      "content": "방금 홈런!!",
      "status": "ACTIVE",
      "createdAt": "2026-04-27T14:22:15Z"
    },
    {
      "messageId": 9980,
      "roomId": 201,
      "type": "IMAGE",
      "content": "https://cdn.example.com/chat/9980.png",
      "status": "ACTIVE",
      "createdAt": "2026-04-27T14:21:00Z"
    }
  ],
  "nextCursor": 9980,
  "hasNext": true
  "totalCount": 1000
}
```

Response Field

| 필드           | 타입           | 설명                                   |
|--------------|--------------|--------------------------------------|
| `messageId`  | Long         | 메시지 ID                               |
| `roomId`     | Long         | 채팅방 ID                               |
| `senderId`   | Long         | 발신자 ID (시스템 메시지는 0)                  |
| `type`       | String       | `TEXT` / `IMAGE` / `FILE` / `SYSTEM` |
| `content`    | String       | 텍스트 본문 또는 파일 URL                     |
| `status`     | String       | `ACTIVE` / `DELETED`                 |
| `createdAt`  | DateTime     | 생성 시각                                |
| `nextCursor` | Long \| null | 다음 페이지 커서                            |
| `hasNext`    | Boolean      | 다음 페이지 존재 여부                         |
| `totalCount` | int          | 검색된 item 수                           |

Validation / Business Rules

- 인증된 사용자 + 해당 방의 멤버 (`DM`/`GROUP` 비공개 방의 경우)만 조회 가능.
- `GAME`  공개 방은 비멤버도 조회 가능하도록 옵션 가능 (정책에 따라 결정).
- 정렬: `messageId DESC` (최신순).
- `DELETED` 메시지는 `content` 가 `"[삭제된 메시지입니다]"` 로 마스킹되어 반환.

---

### 5-20. 메시지 첨부 파일 업로드

```
POST /api/chat/messages/upload
```

Auth Required: **O**

Content-Type: `multipart/form-data`

Form Field

| 필드       | 타입   | 필수 | 설명                  |
|----------|------|----|---------------------|
| `file`   | File | Y  | 업로드할 파일 (이미지/일반 파일) |
| `roomId` | Long | Y  | 대상 채팅방 ID (멤버 검증용)  |

Response Body

```json
{
  "uploadId": "u-2c0a9f1b",
  "url": "https://cdn.example.com/chat/2026/04/27/u-2c0a9f1b.png",
  "type": "IMAGE",
  "size": 215433,
  "mimeType": "image/png",
  "expiresAt": "2026-04-27T15:40:00Z"
}
```

Validation / Business Rules

- 인증된 사용자 + 해당 방의 멤버만 가능.
- 업로드 결과로 받은 `url` 을 STOMP `SEND /app/chat.send` 의 `content` 에 담아 메시지를 발송한다.
- 이미지 5MB / 파일 25MB 기본 제한 (정책 가변).
- 허용 MIME: 이미지 (`image/jpeg`, `image/png`, `image/webp`, `image/gif`), 문서 (`application/pdf`, ...).
- `expiresAt` 이 지나도록 메시지를 보내지 않으면 업로드 파일은 GC.

---

## 부록. 비기능적 정책

| 항목                    | 정책                                                      |
|-----------------------|---------------------------------------------------------|
| 메시지 본문 최대 길이          | 4,000 자                                                 |
| 채팅방 이름 길이             | 1~100 자                                                 |
| 페이지 사이즈 기본/최대         | 20 / 100                                                |
| 메시지 발송 속도 제한          | 사용자당 초당 10건 (`RATE_LIMITED`)                            |
| Redis Key 네이밍         | `chat:room:{roomId}:user:{userId}:lastRead` 등 prefix 통일 |
| 메시지 순서 보장             | DB `messageId` (BIGSERIAL) 단조 증가 + `(room_id, id)` 인덱스  |
| Multi-instance 브로드캐스트 | Redis Pub/Sub 또는 STOMP relay 채택 (배포 환경에 따라 결정)          |

---

## 6. 알림

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

### 6-1. 알림 목록 조회

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

### 6-2. 알림 읽음 처리

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

### 6-3. 전체 알림 읽음 처리

```
PATCH /api/notifications/read-all
```

내 미읽음 알림 전체를 읽음 처리합니다.

**Response** : `204 No Content`

---

### 6-4. SSE 실시간 연결

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

### 6-5. 알림 설정 조회

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

### 6-6. 알림 설정 수정

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

### 6-7. 채널 목록 조회

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

### 6-8. 채널 등록

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

### 6-9. 채널 삭제

```
DELETE /api/notifications/channels/{channelId}
```

**Response** : `204 No Content`

**에러**

| 코드 | HTTP | 설명 |
|---|---|---|
| `NOTIFICATION_CHANNEL_NOT_FOUND` | 404 | 존재하지 않는 채널 |

---

### 6-10. 채널 활성화/비활성화 토글

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

---

## 7. 결제

Toss Payments PG와 연동합니다. 결제는 **생성 → 확인** 2단계로 진행됩니다.

| Method | Path | Auth | 설명 |
|---|---|---|---|
| POST | `/api/payments` | O | 결제 생성 (Toss 결제 진행 전 등록) |
| POST | `/api/payments/confirm` | O | 결제 확인 (Toss 승인 후 금액 검증) |
| POST | `/api/payments/{paymentId}/cancel` | O | 결제 취소 |

---

### 7-1. 결제 생성

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

### 7-2. 결제 확인

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

### 7-3. 결제 취소

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
