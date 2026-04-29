# API 명세서 — Sortsify

> **Base URL**: `https://api.sortsify.com` (로컬: `http://localhost:8080`) \
> **인증**: Auth Required = O 인 엔드포인트는 `Authorization: Bearer {accessToken}` 헤더 필요 \
> **공통 응답 포맷**: 아래 `ApiResponse<T>` 구조를 항상 래핑

---

## 공통 응답 포맷

```json
{
  "success": true,
  "data": {},
  "error": null,
  "timestamp": "2025-04-21T10:00:00Z"
}
```

### 에러 응답

```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "SEAT_ALREADY_RESERVED",
    "message": "이미 선점된 좌석입니다.",
    "detail": null
  },
  "timestamp": "2025-04-21T10:00:00Z"
}
```

### 공통 HTTP 상태 코드

| HTTP Status | 설명 |
| --- | --- |
| 200 | 성공 |
| 201 | 리소스 생성 성공 |
| 204 | 성공 (응답 본문 없음) |
| 400 | 요청 파라미터/바디 오류 |
| 401 | 인증 실패 (토큰 없음 또는 만료) |
| 403 | 권한 없음 |
| 404 | 리소스 없음 |
| 409 | 충돌 (중복, 이미 존재) |
| 422 | 비즈니스 규칙 위반 |
| 429 | 요청 한도 초과 |
| 500 | 서버 내부 오류 |

### 공통 에러 코드

| 에러 코드 | HTTP Status | 설명 |
| --- | --- | --- |
| `UNAUTHORIZED` | 401 | 토큰 없음 또는 만료 |
| `FORBIDDEN` | 403 | 해당 리소스 접근 권한 없음 |
| `NOT_FOUND` | 404 | 리소스를 찾을 수 없음 |
| `INVALID_INPUT` | 400 | 입력값 형식 오류 |
| `INTERNAL_ERROR` | 500 | 서버 내부 오류 |

---

## 페이지네이션 (공통)

목록 API는 Cursor 기반 페이지네이션 적용.

### Query Parameter

| 파라미터 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `cursor` | String | N | 이전 응답의 `nextCursor` 값 |
| `limit` | Integer | N | 페이지 크기 (기본 20, 최대 100) |

### 목록 응답 구조

```json
{
  "success": true,
  "data": {
    "items": [],
    "nextCursor": "eyJpZCI6MTAwfQ==",
    "hasNext": true,
    "totalCount": 342
  }
}
```

---

## 1. Auth / Member 도메인

### 엔드포인트 목록

| method | path | auth required | 설명 |
| --- | --- | --- | --- |
| GET | /oauth2/authorization/{registrationId} | N | 소셜 로그인 (Google / Kakao) |
| GET | /oauth2/callback | N | 소셜 로그인 콜백 (토큰 발급) |
| POST | /api/auth/token/refresh | N | 액세스 토큰 갱신 |
| POST | /api/auth/logout | O | 로그아웃 |
| GET | /api/members/me | O | 내 정보 조회 |
| PATCH | /api/members/me/nickname | O | 내 정보 수정 (닉네임) |
| DELETE | /api/members/me | O | 회원 탈퇴 |
| POST | /api/members/me/favorite-teams | O | 선호 팀 추가 |
| GET | /api/members/me/favorite-teams | O | 선호 팀 목록 조회 |
| PATCH | /api/members/me/favorite-teams/{teamId}/priority | O | 선호 팀 우선순위 수정 |
| DELETE | /api/members/me/favorite-teams/{teamId} | O | 선호 팀 삭제 |
| GET | /api/members/me/activity/monthly | O | 월별 응원 활동 통계 |

---

### 1-1. 소셜 로그인 (OAuth2)

```
GET /oauth2/authorization/{registrationId}
```

브라우저에서 직접 접근. Spring Security OAuth2 Client가 처리.

#### Path Variable

| 변수 | 값 | 설명 |
| --- | --- | --- |
| `registrationId` | `google` \| `kakao` | 소셜 로그인 제공사 |

---

### 1-2. 소셜 로그인 콜백

```
GET /oauth2/callback?accessToken={jwt}&refreshToken={jwt}
```

인증 완료 후 리다이렉트. 클라이언트가 쿼리스트링에서 토큰을 추출.

#### Response Field

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `accessToken` | String | JWT 액세스 토큰 (만료: 30분) |
| `refreshToken` | String | JWT 리프레시 토큰 (만료: 30일) |
| `memberId` | Long | 회원 ID |
| `nickname` | String | 닉네임 |
| `isNew` | Boolean | 최초 가입 여부 (true면 선호팀 설정 유도) |

---

### 1-3. 토큰 갱신

```
POST /api/auth/token/refresh
```

#### Request Body

```json
{
  "refreshToken": "eyJhbGciOiJIUzI1NiJ9..."
}
```

#### Request Field

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `refreshToken` | String | Y | 유효한 리프레시 토큰 |

#### Response Body

```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiJ9..."
  }
}
```

#### Http Status / Error Code

| 에러 코드 | HTTP Status | 설명 |
| --- | --- | --- |
| `INVALID_REFRESH_TOKEN` | 401 | 유효하지 않거나 만료된 리프레시 토큰 |

---

### 1-4. 로그아웃

```
POST /api/auth/logout
```

서버에서 refreshToken 무효화 (Redis 블랙리스트 등록).

#### Request Body

```json
{
  "refreshToken": "eyJhbGciOiJIUzI1NiJ9..."
}
```

#### Request Field

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `refreshToken` | String | Y | 무효화할 리프레시 토큰 |

#### Response

```
HTTP 204 No Content
```

---

### 1-5. 내 정보 조회

```
GET /api/members/me
```

#### Response Body

```json
{
  "success": true,
  "data": {
    "memberId": 1,
    "email": "user@example.com",
    "nickname": "응원왕",
    "createdAt": "2025-01-01T00:00:00Z"
  }
}
```

#### Response Field

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `memberId` | Long | 회원 ID |
| `email` | String | 이메일 |
| `nickname` | String | 닉네임 |
| `createdAt` | String (ISO8601) | 가입 시각 |

---

### 1-5-1. 내 정보 수정 (닉네임)

```
PATCH /api/members/me/nickname
```

#### Request Body

```json
{
  "nickname": "새닉네임"
}
```

#### Request Field

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `nickname` | String | Y | 변경할 닉네임 (2~20자) |

#### Response Body

```json
{
  "success": true,
  "data": {
    "memberId": 1,
    "nickname": "새닉네임"
  }
}
```

#### Http Status / Error Code

| 에러 코드 | HTTP Status | 설명 |
| --- | --- | --- |
| `INVALID_INPUT` | 400 | 닉네임 형식 오류 (2~20자 범위) |
| `NICKNAME_DUPLICATE` | 409 | 이미 사용 중인 닉네임 |

---

### 1-5-2. 회원 탈퇴

```
DELETE /api/members/me
```

탈퇴 처리 시 회원 상태를 `WITHDRAWN`으로 변경. 관련 데이터는 정책에 따라 비활성화 처리.

#### Response

```
HTTP 204 No Content
```

---

### 1-6. 선호 팀 추가

```
POST /api/members/me/favorite-teams
```

#### Request Body

```json
{
  "teamId": 3,
  "priority": 1
}
```

#### Request Field

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `teamId` | Long | Y | 추가할 팀 ID |
| `priority` | Integer | N | 선호 순위 (1=최애, 기본값: 마지막 순위 + 1) |

#### Response Body

```json
{
  "success": true,
  "data": {
    "favoriteTeamId": 10,
    "teamId": 3,
    "teamName": "KIA 타이거즈",
    "shortName": "KIA",
    "sportType": "BASEBALL",
    "priority": 1
  }
}
```

#### Response Field

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `favoriteTeamId` | Long | 선호 팀 레코드 ID |
| `teamId` | Long | 팀 ID |
| `teamName` | String | 팀 이름 |
| `shortName` | String | 팀 약칭 |
| `sportType` | String | 종목 |
| `priority` | Integer | 선호 순위 |

#### Http Status / Error Code

| 에러 코드 | HTTP Status | 설명 |
| --- | --- | --- |
| `TEAM_NOT_FOUND` | 404 | 존재하지 않는 팀 |
| `FAVORITE_TEAM_ALREADY_EXISTS` | 409 | 이미 등록된 팀 |

---

### 1-7. 선호 팀 목록 조회

```
GET /api/members/me/favorite-teams
```

#### Response Body

```json
{
  "success": true,
  "data": [
    {
      "favoriteTeamId": 10,
      "teamId": 3,
      "teamName": "KIA 타이거즈",
      "shortName": "KIA",
      "sportType": "BASEBALL",
      "priority": 1
    }
  ]
}
```

#### Response Field

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `favoriteTeamId` | Long | 선호 팀 레코드 ID |
| `teamId` | Long | 팀 ID |
| `teamName` | String | 팀 이름 |
| `shortName` | String | 팀 약칭 |
| `sportType` | String | 종목 |
| `priority` | Integer | 선호 순위 |

---

### 1-7-1. 선호 팀 우선순위 수정

```
PATCH /api/members/me/favorite-teams/{teamId}/priority
```

#### Path Variable

| 변수 | 타입 | 설명 |
| --- | --- | --- |
| `teamId` | Long | 팀 ID (teams.id 기준) |

#### Request Body

```json
{
  "priority": 1
}
```

#### Request Field

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `priority` | Integer | Y | 변경할 우선순위 (1 이상, 등록된 팀 수 이하) |

#### Response Body

```json
{
  "success": true,
  "data": {
    "favoriteTeamId": 10,
    "teamId": 3,
    "teamName": "KIA 타이거즈",
    "shortName": "KIA",
    "sportType": "BASEBALL",
    "priority": 1
  }
}
```

#### Http Status / Error Code

| 에러 코드 | HTTP Status | 설명 |
| --- | --- | --- |
| `FAVORITE_TEAM_NOT_FOUND` | 404 | 선호 팀으로 등록되지 않은 팀 |
| `INVALID_PRIORITY` | 400 | 우선순위 범위 초과 또는 중복 |

---

### 1-8. 선호 팀 삭제

```
DELETE /api/members/me/favorite-teams/{teamId}
```

> `teamId`는 `member_favorite_teams.team_id` 기준 삭제 (teams.id). `member_favorite_teams.id`가 아님에 주의.

#### Path Variable

| 변수 | 타입 | 설명 |
| --- | --- | --- |
| `teamId` | Long | 삭제할 팀 ID (teams.id 기준) |

#### Response

```
HTTP 204 No Content
```

#### Http Status / Error Code

| 에러 코드 | HTTP Status | 설명 |
| --- | --- | --- |
| `FAVORITE_TEAM_NOT_FOUND` | 404 | 선호 팀으로 등록되지 않은 팀 |

---

### 1-9. 월별 응원 활동 통계

```
GET /api/members/me/activity/monthly
```

#### Query Parameter

| 파라미터 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `year` | Integer | N | 연도 (기본: 현재 연도) |
| `month` | Integer | N | 월 1~12 (기본: 현재 월) |

#### Response Body

```json
{
  "success": true,
  "data": {
    "year": 2025,
    "month": 4,
    "ticketCount": 3,
    "chatMessageCount": 127,
    "attendedGames": [
      {
        "gameId": 101,
        "team1Name": "KIA 타이거즈",
        "team2Name": "삼성 라이온즈",
        "gameTime": "2025-04-05T14:00:00Z",
        "venue": "광주-기아 챔피언스 필드"
      }
    ]
  }
}
```

#### Response Field

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `year` | Integer | 조회 연도 |
| `month` | Integer | 조회 월 |
| `ticketCount` | Integer | 해당 월 예매 티켓 수 |
| `chatMessageCount` | Integer | 해당 월 채팅 메시지 수 |
| `attendedGames` | Array | 해당 월 참여 경기 목록 |

---

## 2. Team 도메인

### 엔드포인트 목록

| method | path | auth required | 설명 |
| --- | --- | --- | --- |
| GET | /api/teams | N | 팀 목록 조회 |
| GET | /api/teams/{teamId} | N | 팀 상세 조회 |

> 팀 등록 / 수정 / 비활성화는 **Admin API (7번)** 참조.

---

### 2-1. 팀 목록 조회

```
GET /api/teams
```

#### Query Parameter

| 파라미터 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `sportType` | String | N | 종목 필터 (`BASEBALL` \| `FOOTBALL` \| `BASKETBALL` 등) |
| `isActive` | Boolean | N | 활성 팀만 조회 (기본 `true`) |

#### Response Body

```json
{
  "success": true,
  "data": [
    {
      "teamId": 1,
      "name": "KIA 타이거즈",
      "shortName": "KIA",
      "sportType": "BASEBALL",
      "isActive": true
    }
  ]
}
```

#### Response Field

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `teamId` | Long | 팀 ID |
| `name` | String | 팀 정식 명칭 |
| `shortName` | String | 팀 약칭 |
| `sportType` | String | 종목 |
| `isActive` | Boolean | 활동 중인 팀 여부 |

---

### 2-2. 팀 상세 조회

```
GET /api/teams/{teamId}
```

#### Path Variable

| 변수 | 타입 | 설명 |
| --- | --- | --- |
| `teamId` | Long | 팀 ID |

#### Response Body

```json
{
  "success": true,
  "data": {
    "teamId": 1,
    "name": "KIA 타이거즈",
    "shortName": "KIA",
    "sportType": "BASEBALL",
    "isActive": true,
    "createdAt": "2024-01-01T00:00:00Z"
  }
}
```

#### Response Field

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `teamId` | Long | 팀 ID |
| `name` | String | 팀 정식 명칭 |
| `shortName` | String | 팀 약칭 |
| `sportType` | String | 종목 |
| `isActive` | Boolean | 활동 중인 팀 여부 |
| `createdAt` | String (ISO8601) | 데이터 생성 시각 |

#### Http Status / Error Code

| 에러 코드 | HTTP Status | 설명 |
| --- | --- | --- |
| `TEAM_NOT_FOUND` | 404 | 존재하지 않는 팀 |

---

## 3. Ticketing 도메인

### 엔드포인트 목록

| method | path | auth required | 설명 |
| --- | --- | --- | --- |
| GET | /api/games | N | 경기 목록 조회 |
| GET | /api/games/{gameId} | N | 경기 상세 조회 |
| GET | /api/games/{gameId}/seats | N | 좌석 목록 조회 |
| GET | /api/tickets | O | 내 예매 내역 조회 |
| GET | /api/tickets/{ticketId} | O | 예매 상세 조회 |
| POST | /api/tickets/reserve | O | 티켓 예매 (좌석 선점) |
| POST | /api/tickets/queue/enter | O | 대기열 입장 |
| GET | /api/tickets/queue/position | O | 대기열 순번 조회 (폴링) |
| GET | /api/tickets/queue/sse | O | 대기열 순번 SSE 스트리밍 |
| DELETE | /api/tickets/queue/leave | O | 대기열 명시적 이탈 |
| POST | /api/tickets/{ticketId}/cancel | O | 티켓 취소 |

---

### 3-1. 경기 목록 조회

```
GET /api/games
```

#### Query Parameter

| 파라미터 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `sportType` | String | N | 종목 필터 |
| `teamId` | Long | N | 특정 팀이 참여한 경기만 |
| `status` | String | N | `SCHEDULED` \| `ON_SALE` \| `SOLD_OUT` \| `ONGOING` \| `FINISHED` \| `CANCELLED` |
| `from` | String (ISO8601) | N | 시작일 이후 필터 |
| `to` | String (ISO8601) | N | 종료일 이전 필터 |
| `cursor` | String | N | 페이지네이션 커서 |
| `limit` | Integer | N | 페이지 크기 (기본 20) |

#### Response Body

```json
{
  "success": true,
  "data": {
    "items": [
      {
        "gameId": 101,
        "sportType": "BASEBALL",
        "teams": [
          {
            "teamId": 1,
            "name": "KIA 타이거즈",
            "shortName": "KIA",
            "side": "HOME"
          },
          {
            "teamId": 2,
            "name": "삼성 라이온즈",
            "shortName": "삼성",
            "side": "AWAY"
          }
        ],
        "gameTime": "2025-04-21T14:00:00Z",
        "venue": "광주-기아 챔피언스 필드",
        "status": "ON_SALE",
        "totalSeats": 20000,
        "availableSeats": 1500,
        "isRivalMatch": false
      }
    ],
    "nextCursor": "eyJpZCI6MTAxfQ==",
    "hasNext": true,
    "totalCount": 48
  }
}
```

#### Response Field

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `gameId` | Long | 경기 ID |
| `sportType` | String | 종목 |
| `teams` | Array | 팀 정보 리스트 |
| `teams[].teamId` | Long | 팀 ID |
| `teams[].teamName` | String | 팀 이름 |
| `teams[].teamLogo` | String | 팀 로고 URL |
| `teams[].side` | String | 홈/어웨이 구분 (`HOME`, `AWAY`) |
| `gameTime` | String (ISO8601) | 경기 시작 시각 |
| `stadium` | String | 경기장 |
| `status` | String | 경기 상태 |
| `totalSeats` | Integer | 전체 좌석 수 |
| `availableSeats` | Integer | 남은 좌석 수 |
| `isRivalMatch` | Boolean | 라이벌전 여부 |

---

### 3-2. 경기 상세 조회

```
GET /api/games/{gameId}
```

#### Path Variable

| 변수 | 타입 | 설명 |
| --- | --- | --- |
| `gameId` | Long | 경기 ID |

#### Response Body

```json
{
  "success": true,
  "data": {
    "gameId": 101,
    "sportType": "BASEBALL",
    "teams": [
      {
        "teamId": 1,
        "name": "LG 트윈스",
        "shortName": "LG",
        "side": "HOME"
      },
      {
        "teamId": 2,
        "name": "두산 베어스",
        "shortName": "두산",
        "side": "AWAY"
      }
    ],
    "gameTime": "2025-04-21T14:00:00Z",
    "stadium": "광주-기아 챔피언스 필드",
    "status": "ON_SALE",
    "totalSeats": 20000,
    "availableSeats": 1500,
    "isRivalMatch": false,
    "seatGradeSummary": [
      { "grade": "VIP", "price": 80000, "available": 0 },
      { "grade": "R", "price": 50000, "available": 120 },
      { "grade": "S", "price": 30000, "available": 800 },
      { "grade": "A", "price": 15000, "available": 580 }
    ]
  }
}
```

#### Response Field

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `seatGradeSummary` | Array | 등급별 좌석 요약 (가격 + 잔여수) |
| `seatGradeSummary[].grade` | String | 좌석 등급 |
| `seatGradeSummary[].price` | Integer | 해당 등급 가격 (원) |
| `seatGradeSummary[].available` | Integer | 잔여 좌석 수 |

*(나머지 필드는 3-1과 동일)*

#### Http Status / Error Code

| 에러 코드 | HTTP Status | 설명 |
| --- | --- | --- |
| `GAME_NOT_FOUND` | 404 | 존재하지 않는 경기 |

---

### 3-3. 좌석 목록 조회

```
GET /api/games/{gameId}/seats
```

#### Path Variable

| 변수 | 타입 | 설명 |
| --- | --- | --- |
| `gameId` | Long | 경기 ID |

#### Query Parameter

| 파라미터 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `grade` | String | N | 좌석 등급 (`VIP` \| `R` \| `S` \| `A` \| `OUTFIELD`) |
| `status` | String | N | 좌석 상태 (기본: `AVAILABLE`) |
| `cursor` | String | N | 페이지네이션 커서 |
| `limit` | Integer | N | 페이지 크기 (기본 50, 최대 200) |

#### Response Body

```json
{
  "success": true,
  "data": {
    "items": [
      {
        "seatId": 5001,
        "grade": "S",
        "section": "1루 내야",
        "rowNumber": "C",
        "seatNumber": 22,
        "price": 30000,
        "status": "AVAILABLE"
      }
    ],
    "nextCursor": "eyJpZCI6NTAwMX0=",
    "hasNext": true,
    "totalCount": 800
  }
}
```

#### Response Field

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `seatId` | Long | 좌석 ID |
| `grade` | String | 좌석 등급 |
| `section` | String | 구역 |
| `rowNumber` | String | 열 번호 |
| `seatNumber` | Integer | 좌석 번호 |
| `price` | Integer | 현재 가격 (원) |
| `status` | String | `AVAILABLE` \| `RESERVED` \| `SOLD` |

#### Http Status / Error Code

| 에러 코드 | HTTP Status | 설명 |
| --- | --- | --- |
| `GAME_NOT_FOUND` | 404 | 존재하지 않는 경기 |

---

### 3-4. 티켓 예매 (좌석 선점)

```
POST /api/tickets/reserve
```

좌석 선점 후 티켓 생성 (상태: `PENDING`). 10분 내 결제 미완료 시 자동 취소.

#### Request Body

```json
{
  "gameId": 101,
  "seatId": 5001,
  "buyerName": "홍길동",
  "buyerPhone": "010-1234-5678"
}
```

#### Request Field

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `gameId` | Long | Y | 경기 ID |
| `seatId` | Long | Y | 좌석 ID |
| `buyerName` | String | Y | 구매자 이름 (최대 50자) |
| `buyerPhone` | String | Y | 구매자 연락처 (010-XXXX-XXXX 형식) |

#### Response Body

```json
{
  "success": true,
  "data": {
    "ticketId": 9001,
    "gameId": 101,
    "seatId": 5001,
    "seatGrade": "S",
    "seatSection": "1루 내야",
    "price": 30000,
    "status": "PENDING",
    "reservedAt": "2025-04-21T10:00:00Z",
    "expiresAt": "2025-04-21T10:10:00Z"
  }
}
```

#### Response Field

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `ticketId` | Long | 생성된 티켓 ID |
| `status` | String | 티켓 상태 (`PENDING`) |
| `reservedAt` | String (ISO8601) | 선점 시각 |
| `expiresAt` | String (ISO8601) | 결제 만료 시각 (선점 후 10분) |

#### Http Status / Error Code

| 에러 코드 | HTTP Status | 설명 |
| --- | --- | --- |
| `GAME_NOT_FOUND` | 404 | 존재하지 않는 경기 |
| `SEAT_NOT_FOUND` | 404 | 존재하지 않는 좌석 |
| `SEAT_ALREADY_RESERVED` | 409 | 이미 선점된 좌석 |
| `GAME_NOT_ON_SALE` | 422 | 판매 중이 아닌 경기 |
| `TICKET_LIMIT_EXCEEDED` | 422 | 경기당 1인 최대 4매 초과 |

---

### 3-5. 대기열 입장

```
POST /api/tickets/queue/enter
```

#### Request Body

```json
{
  "gameId": 101
}
```

#### Request Field

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `gameId` | Long | Y | 경기 ID |

#### Response Body

```json
{
  "success": true,
  "data": {
    "gameId": 101,
    "position": 1523,
    "estimatedWaitSeconds": 456
  }
}
```

#### Response Field

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `position` | Integer | 현재 대기 순번 |
| `estimatedWaitSeconds` | Integer | 예상 대기 시간 (초) |

#### Http Status / Error Code

| 에러 코드 | HTTP Status | 설명 |
| --- | --- | --- |
| `GAME_NOT_FOUND` | 404 | 존재하지 않는 경기 |
| `QUEUE_ALREADY_JOINED` | 409 | 이미 대기열 입장 상태 |

---

### 3-6. 대기열 순번 조회

```
GET /api/tickets/queue/position?gameId={gameId}
```

#### Query Parameter

| 파라미터 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `gameId` | Long | Y | 경기 ID |

#### Response Body

```json
{
  "success": true,
  "data": {
    "gameId": 101,
    "position": 802,
    "estimatedWaitSeconds": 240
  }
}
```

#### Http Status / Error Code

| 에러 코드 | HTTP Status | 설명 |
| --- | --- | --- |
| `QUEUE_NOT_JOINED` | 404 | 대기열에 입장하지 않은 상태 |

---

### 3-7. 대기열 이탈

```
DELETE /api/tickets/queue/leave?gameId={gameId}
```

#### Query Parameter

| 파라미터 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `gameId` | Long | Y | 경기 ID |

#### Response

```
HTTP 204 No Content
```

---

### 3-8. 티켓 취소

```
POST /api/tickets/{ticketId}/cancel
```

#### Path Variable

| 변수 | 타입 | 설명 |
| --- | --- | --- |
| `ticketId` | Long | 티켓 ID |

#### Response Body

```json
{
  "success": true,
  "data": {
    "ticketId": 9001,
    "status": "CANCELLED",
    "cancelledAt": "2025-04-21T11:00:00Z",
    "refundAmount": 30000
  }
}
```

#### Response Field

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `ticketId` | Long | 티켓 ID |
| `status` | String | 변경된 상태 (`CANCELLED`) |
| `cancelledAt` | String (ISO8601) | 취소 시각 |
| `refundAmount` | Integer | 환불 예정 금액 (원) |

#### Http Status / Error Code

| 에러 코드 | HTTP Status | 설명 |
| --- | --- | --- |
| `TICKET_NOT_FOUND` | 404 | 존재하지 않는 티켓 |
| `FORBIDDEN` | 403 | 본인 티켓이 아님 |
| `TICKET_NOT_CANCELLABLE` | 422 | 취소 불가 상태 |

---

### 3-9. 내 예매 내역 조회

```
GET /api/tickets
```

#### Query Parameter

| 파라미터 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `status` | String | N | `CONFIRMED` \| `USED` \| `CANCELLED` |
| `cursor` | String | N | 페이지네이션 커서 |
| `limit` | Integer | N | 페이지 크기 (기본 20) |

#### Response Body

```json
{
  "success": true,
  "data": {
    "items": [
      {
        "ticketId": 9001,
        "ticketNumber": "550e8400-e29b-41d4-a716-446655440000",
        "gameId": 101,
        "sportType": "BASEBALL",
        "team1Name": "KIA 타이거즈",
        "team2Name": "삼성 라이온즈",
        "gameTime": "2025-04-21T14:00:00Z",
        "venue": "광주-기아 챔피언스 필드",
        "seatGrade": "S",
        "seatSection": "1루 내야",
        "seatNumber": "C-22",
        "price": 30000,
        "status": "CONFIRMED",
        "issuedAt": "2025-04-21T10:05:00Z"
      }
    ],
    "nextCursor": "eyJpZCI6OTAwMH0=",
    "hasNext": false,
    "totalCount": 3
  }
}
```

#### Response Field

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `ticketId` | Long | 티켓 ID |
| `ticketNumber` | String | 고유 티켓 번호 (UUID) |
| `status` | String | `CONFIRMED` \| `USED` \| `CANCELLED` |
| `issuedAt` | String (ISO8601) | 발급 시각 |

---

### 3-10. 예매 상세 조회

```
GET /api/tickets/{ticketId}
```

#### Path Variable

| 변수 | 타입 | 설명 |
| --- | --- | --- |
| `ticketId` | Long | 티켓 ID |

#### Response Body

`3-9` 목록 응답의 단건 항목과 동일 + 결제 정보 포함.

```json
{
  "success": true,
  "data": {
    "ticketId": 9001,
    "ticketNumber": "550e8400-e29b-41d4-a716-446655440000",
    "gameId": 101,
    "sportType": "BASEBALL",
    "team1Name": "KIA 타이거즈",
    "team2Name": "삼성 라이온즈",
    "gameTime": "2025-04-21T14:00:00Z",
    "venue": "광주-기아 챔피언스 필드",
    "seatGrade": "S",
    "seatSection": "1루 내야",
    "seatNumber": "C-22",
    "teamSide": "TEAM1",
    "price": 30000,
    "status": "CONFIRMED",
    "issuedAt": "2025-04-21T10:05:00Z",
    "payment": {
      "paymentId": 7001,
      "method": "TOSS_PAY",
      "finalAmount": 30000,
      "paidAt": "2025-04-21T10:05:00Z"
    }
  }
}
```

#### Http Status / Error Code

| 에러 코드 | HTTP Status | 설명 |
| --- | --- | --- |
| `TICKET_NOT_FOUND` | 404 | 존재하지 않는 티켓 |
| `FORBIDDEN` | 403 | 본인 티켓이 아님 |

---

### 3-11. 대기열 SSE 스트리밍

```
GET /api/tickets/queue/sse?gameId={gameId}&uuid={entryUuid}
```

대기열 입장(`3-5`) 후 발급된 UUID로 SSE 연결 수립. 순번 변경 시마다 이벤트 Push.

#### Query Parameter

| 파라미터 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `gameId` | Long | Y | 경기 ID |
| `uuid` | String | Y | 대기열 입장 시 발급된 진입 UUID |

#### SSE 이벤트 구조 (대기 중)

```
event: queue-position
data: {"gameId": 101, "position": 523, "estimatedWaitSeconds": 156}
```

#### SSE 이벤트 구조 (예매 진입 가능)

```
event: queue-admitted
data: {"gameId": 101, "reservationUuid": "abc-...", "expireAt": "2025-04-21T10:15:00Z"}
```

> `queue-admitted` 이벤트 수신 시 클라이언트는 `reservationUuid`를 보관하고 좌석 선점(`3-4`) 요청에 사용.  
> 30초 내 SSE 재연결 시 기존 순번 유지. 30초 초과 시 자동 이탈 처리.

---

## 4. Payment 도메인

### 엔드포인트 목록

| method | path | auth required | 설명 |
| --- | --- | --- | --- |
| POST | /api/payments/request | O | 결제 요청 등록 |
| POST | /api/payments/verify | O | 결제 검증 (금액 확인 + 티켓 확정) |
| POST | /api/payments/webhook | N | PG Webhook 수신 |
| GET | /api/payments | O | 내 결제 내역 조회 |
| GET | /api/payments/{paymentId} | O | 결제 상세 조회 |
| POST | /api/payments/{paymentId}/refund | O | 환불 요청 |

---

### 4-1. 결제 요청

```
POST /api/payments/request
```

PG사 결제 UI 실행 전, 서버에 결제 요청을 먼저 등록한다.

#### Request Body

```json
{
  "ticketId": 9001,
  "method": "TOSS_PAY",
  "idempotencyKey": "550e8400-e29b-41d4-a716-446655440000"
}
```

#### Request Field

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `ticketId` | Long | Y | 예매한 티켓 ID |
| `method` | String | Y | 결제 수단 (`CARD` \| `KAKAO_PAY` \| `TOSS_PAY`) |
| `idempotencyKey` | String | Y | 클라이언트 생성 UUID v4 (중복 결제 방지) |

#### Response Body

```json
{
  "success": true,
  "data": {
    "paymentId": 7001,
    "ticketId": 9001,
    "amount": 30000,
    "discountAmount": 0,
    "finalAmount": 30000,
    "method": "TOSS_PAY",
    "status": "PENDING",
    "idempotencyKey": "550e8400-e29b-41d4-a716-446655440000"
  }
}
```

#### Response Field

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `paymentId` | Long | 서버 결제 ID |
| `amount` | Integer | 원래 금액 (원) |
| `discountAmount` | Integer | 할인 금액 (원) |
| `finalAmount` | Integer | 최종 결제 금액 (원) |
| `status` | String | 결제 상태 (`PENDING`) |

#### Http Status / Error Code

| 에러 코드 | HTTP Status | 설명 |
| --- | --- | --- |
| `TICKET_NOT_FOUND` | 404 | 존재하지 않는 티켓 |
| `FORBIDDEN` | 403 | 본인 티켓이 아님 |
| `TICKET_NOT_PAYABLE` | 422 | 결제 가능 상태가 아닌 티켓 (PENDING 아님) |
| `PAYMENT_ALREADY_EXISTS` | 409 | 동일 idempotencyKey 결제 요청 중복 |
| `TICKET_RESERVATION_EXPIRED` | 422 | 선점 10분 만료 |

---

### 4-2. 결제 검증

```
POST /api/payments/verify
```

PG사 결제 완료 후 클라이언트가 서버에 검증 요청. 금액 위변조 확인 + 좌석 상태 최종 확정.

#### Request Body

```json
{
  "paymentId": 7001,
  "pgTransactionId": "pg-toss-123456",
  "paidAmount": 30000
}
```

#### Request Field

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `paymentId` | Long | Y | 서버 결제 ID |
| `pgTransactionId` | String | Y | PG사 거래 ID |
| `paidAmount` | Integer | Y | 실제 결제된 금액 (위변조 검증용) |

#### Response Body

```json
{
  "success": true,
  "data": {
    "paymentId": 7001,
    "ticketId": 9001,
    "finalAmount": 30000,
    "status": "COMPLETED",
    "paidAt": "2025-04-21T10:05:00Z"
  }
}
```

#### Response Field

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `status` | String | 결제 상태 (`COMPLETED`) |
| `paidAt` | String (ISO8601) | 결제 완료 시각 |

#### Http Status / Error Code

| 에러 코드 | HTTP Status | 설명 |
| --- | --- | --- |
| `PAYMENT_NOT_FOUND` | 404 | 존재하지 않는 결제 |
| `FORBIDDEN` | 403 | 본인 결제가 아님 |
| `PAYMENT_AMOUNT_MISMATCH` | 422 | 결제 금액 불일치 (위변조 의심) |
| `PAYMENT_ALREADY_COMPLETED` | 409 | 이미 검증 완료된 결제 |
| `SEAT_RESERVATION_EXPIRED` | 422 | 좌석 선점 만료로 결제 불가 |

---

### 4-3. PG Webhook 수신

```
POST /api/payments/webhook
```

PG사에서 서버로 직접 호출하는 엔드포인트. `X-PG-Signature` 헤더로 서명 검증 필수.  
200 응답 후 Kafka를 통해 비동기 처리.

#### Request Header

| 헤더 | 설명 |
| --- | --- |
| `X-PG-Signature` | PG사 HMAC 서명 |
| `X-PG-Provider` | PG사 구분 (`TOSS` \| `IAMPORT`) |

#### Request Body

```json
{
  "orderId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "DONE",
  "amount": 30000,
  "transactionKey": "pg-toss-123456"
}
```

#### Response

```
HTTP 200 OK
```

#### Http Status / Error Code

| 에러 코드 | HTTP Status | 설명 |
| --- | --- | --- |
| `INVALID_WEBHOOK_SIGNATURE` | 401 | 서명 검증 실패 |

---

### 4-3-1. 내 결제 내역 조회

```
GET /api/payments
```

#### Query Parameter

| 파라미터 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `status` | String | N | `PENDING` \| `COMPLETED` \| `REFUNDED` \| `FAILED` \| `CANCELLED` |
| `cursor` | String | N | 페이지네이션 커서 |
| `limit` | Integer | N | 페이지 크기 (기본 20) |

#### Response Body

```json
{
  "success": true,
  "data": {
    "items": [
      {
        "paymentId": 7001,
        "ticketId": 9001,
        "method": "TOSS_PAY",
        "finalAmount": 30000,
        "status": "COMPLETED",
        "paidAt": "2025-04-21T10:05:00Z",
        "game": {
          "gameId": 101,
          "team1Name": "KIA 타이거즈",
          "team2Name": "삼성 라이온즈",
          "gameTime": "2025-04-21T14:00:00Z"
        }
      }
    ],
    "nextCursor": "eyJpZCI6NzAwMH0=",
    "hasNext": false,
    "totalCount": 5
  }
}
```

---

### 4-3-2. 결제 상세 조회

```
GET /api/payments/{paymentId}
```

#### Path Variable

| 변수 | 타입 | 설명 |
| --- | --- | --- |
| `paymentId` | Long | 결제 ID |

#### Response Body

`4-3-1` 목록 단건 + 전체 상세 포함. 환불 정보가 있으면 `refund` 필드 포함.

#### Http Status / Error Code

| 에러 코드 | HTTP Status | 설명 |
| --- | --- | --- |
| `PAYMENT_NOT_FOUND` | 404 | 존재하지 않는 결제 |
| `FORBIDDEN` | 403 | 본인 결제가 아님 |

---

### 4-4. 환불

```
POST /api/payments/{paymentId}/refund
```

#### Path Variable

| 변수 | 타입 | 설명 |
| --- | --- | --- |
| `paymentId` | Long | 결제 ID |

#### Request Body

```json
{
  "reason": "단순 변심"
}
```

#### Request Field

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `reason` | String | N | 환불 사유 |

#### Response Body

```json
{
  "success": true,
  "data": {
    "paymentId": 7001,
    "refundAmount": 30000,
    "status": "REFUNDED",
    "refundedAt": "2025-04-21T12:00:00Z"
  }
}
```

#### Response Field

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `refundAmount` | Integer | 환불 금액 (원) |
| `status` | String | 결제 상태 (`REFUNDED`) |
| `refundedAt` | String (ISO8601) | 환불 완료 시각 |

#### Http Status / Error Code

| 에러 코드 | HTTP Status | 설명 |
| --- | --- | --- |
| `PAYMENT_NOT_FOUND` | 404 | 존재하지 않는 결제 |
| `FORBIDDEN` | 403 | 본인 결제가 아님 |
| `PAYMENT_NOT_REFUNDABLE` | 422 | 환불 불가 상태 |

---

## 5. Chat 도메인

### 엔드포인트 목록

| method | path                                  | auth required | 설명                    |
|--------|---------------------------------------|---------------|-----------------------|
| POST   | /api/chat/create                      | O             | 채팅방 생성                |
| GET    | /api/chat/rooms                       | O             | 내 채팅방 목록 조회           |
| GET    | /api/chat/rooms/game/{gameId}         | N             | 게임별 채팅방 조회            |
| GET    | /api/chat/rooms/{roomId}              | O/N           | 채팅방 상세 조회             |
| PATCH  | /api/chat/rooms/{roomId}              | O             | 채팅방 정보 수정             |
| POST   | /api/chat/rooms/{roomId}/join         | O             | 채팅방 입장                |
| POST   | /api/chat/rooms/{roomId}/leave        | O             | 채팅방 상세 나가기            |
| POST   | /api/chat/rooms/{roomId}/invite       | O             | 채팅방 상세 초대             |
| PATCH  | /api/chat/rooms/{roomId}/notification | O             | 채팅방 알림 설정 변경          |
| GET    | /api/chat/history/{roomId}            | O             | 채팅 이력 조회              |
| WS     | /ws/chat (STOMP)                      | O/N           | WebSocket 연결 / 메시지 전송 |
| DELETE | /api/chat/messages/{messageId}        | O             | 메시지 삭제                |
| POST   | /api/chat/message/upload              | O             | 메시지 첨부 파일 업로드         |



---

### 5-1. 채팅방 생성

```
POST /api/chat/create
```

Auth Required: **O**

Request Body

| 필드                | 타입      | 필수  | 설명                                                             |
|-------------------|---------|-----|----------------------------------------------------------------|
| `type`            | String  | Y   | 채팅방 유형 (`GAME` \|  `DM`)                                       |
| `name`            | String* | Y/N | 채팅방 표시 이름. `DM` 의 경우 자동 생성되어 생략 가능                             |
| `imageUrl`        | String* | N   | 채팅방 프로필 이미지 URL                                                |
| `gameId`          | Long*   | Y/N | `type=GAME` 일 때 필수                                             |
| `inviteeIds`      | Long[]* | Y/N | `DM` / 비공개 방 생성 시 함께 초대할 사용자 ID 목록                             |

Response Body

```
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

| 파라미터 | 타입      | 필수 | 설명                      |
|----------|---------|----|-------------------------|
| `type` | String  | Y  | 채팅방 유형 (`GAME` \| `DM`) |
| `cursor` | Integer | N  | 페이지네이션 커서               |
| `limit` | Integer | N  | 페이지 크기 (기본 20, 최대 100)  |

Response Body

```
{
  "success": true,
  "data": {
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
        "unreadCount": 12,
        "notificationEnabled": true,
        "createdAt": "2026-04-21T09:00:00Z",
        "updatedAt": "2026-04-27T14:22:15Z"
      }
    ],
    "nextCursor": 0,
    "hasNext": false,
    "totalCount": 5
  }
}
```

Response Field

| 필드 | 타입             | 설명                     |
|------|----------------|------------------------|
| `roomId` | Long           | 채팅방 ID                 |
| `type` | String         | 채팅방 유형                 |
| `gameId` | Long \| null   | 연결된 경기 ID (`GAME` 일 때) |
| `title` | String         | 채팅방 표시 이름              |
| `imageUrl` | String \| null | 프로필 이미지                |
| `currentParticipants` | Integer        | 현재 참여 인원               |
| `lastMessage` | Object \| null | 마지막 메시지 요약             |
| `unreadCount` | Integer        | 안읽은 메시지 개수             |
| `notificationEnabled` | Boolean        | 해당 방 알림 수신 여부          |
| `createdAt` | DateTime       | 생성 시각                  |
| `updatedAt` | DateTime       | 마지막 갱신 시각              |
| `nextCursor` | Long \| null        | 다음 page 커서             |
| `hasNext` | Boolean        | 다음 page 존재여부           |
| `totalCount` | Integer        | 가져온 채팅방 목록의 수          |

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

| 파라미터     | 타입 | 필수 | 설명    |
|----------|------|------|-------|
| `gameId` | String | Y | 게임 Id |

Query Parameter

| 파라미터 | 타입 | 필수 | 설명 |
|----------|------|------|------|
| `cursor` | Long \| null | N | 페이지네이션 커서 |
| `limit` | Integer | N | 페이지 크기 (기본 20, 최대 100) |

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

| 필드 | 타입             | 설명                     |
|------|----------------|------------------------|
| `roomId` | Long           | 채팅방 ID                 |
| `type` | String         | 채팅방 유형                 |
| `gameId` | Long \| null   | 연결된 경기 ID  |
| `title` | String         | 채팅방 표시 이름              |
| `imageUrl` | String \| null | 프로필 이미지                |
| `currentParticipants` | Integer        | 현재 참여 인원               |
| `createdAt` | DateTime       | 생성 시각                  |
| `nextCursor` | Long \| null        | 다음 page 커서             |
| `hasNext` | Boolean        | 다음 page 존재여부           |
| `totalCount` | Integer        | 가져온 채팅방 목록의 수          |

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

| 파라미터 | 타입 | 필수 | 설명 |
|----------|------|------|------|
| `roomId` | Long | Y | 채팅방 ID |

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
| `success`              | Boolean        | 성공여부                        |
| `roomId`              | Long           | 채팅방 ID                      |
| `type`                | String         | 채팅방 유형                      |
| `gameId`              | Long \| null   | 연결된 경기 ID                   |
| `title`               | String         | 채팅방 표시 이름                   |
| `imageUrl`            | String \| null | 프로필 이미지                     |
| `currentParticipants` | Integer        | 현재 참여 인원                    |
| `createdBy`            | Long           | 생성자                         |
| `createdAt`           | DateTime       | 생성 시각                       |
| `status`          | String         | 사용자의 채팅방 상태                 |
| `notificationEnabled`             | Boolean        | 사용자의 채팅방 알림 여부              |
| `lastReadMessageId`          | Integer        | 사용자의 채팅방의 마지막 읽은 message id |
| `joinedAt`          | DateTime       | 사용자의 채팅방 입장 날짜              |



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

| 파라미터 | 타입 | 필수 | 설명 |
|----------|------|------|------|
| `roomId` | Long | Y | 채팅방 ID |

Request Body

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `title` | String | N | 새 제목 (1~100자) |
| `imageUrl` | String | N | 새 이미지 URL (빈 문자열 → null 처리) |

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
| `success`   | String        | 성공여부        |
| `roomId`    | Long   | 채팅방 ID      |
| `title`     | String         | 채팅방 표시 이름   |
| `imageUrl`  | String \| null | 프로필 이미지     |
| `updatedAt` | DateTime       | 마지막 엡데이트 날짜 |



Validation / Business Rules

- 인증된 사용자 + 해당 채팅방의 활성 멤버만 가능.
- `DM` 은 수정 불가 (`FORBIDDEN`).
- 변경 후 `chat.room.updated` STOMP 이벤트가 같은 방 멤버에게 브로드캐스트된다.

---

### 5-6. 채팅방 입장

```
POST /api/chat/rooms/{roomId}/join
```

Auth Required: **O**

Path Parameter

| 파라미터 | 타입 | 필수 | 설명 |
|----------|------|------|------|
| `roomId` | Long | Y | 채팅방 ID |

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

| 필드         | 타입             | 설명               |
|------------|----------------|------------------|
| `success`  | String        | 성공여부             |
| `roomId`   | Long   | 채팅방 ID           |
| `memberId` | Long   | 사용자 ID           |
| `status`   | String         | 사용자의 채팅방에서의 현 상태 |
| `joinedAt` | DateTime       | 사용자의 채팅방 입장 날짜   |



Validation / Business Rules

- 인증된 사용자만 입장 가능.
- `DM` / 비공개 방은 사전에 `INVITED` 상태여야 입장 가능.
- 이미 `JOINED` → `ALREADY_JOINED`.
- `BANNED` → `BANNED_MEMBER`.
- 입장 성공 시 `chat.room.member.joined` 이벤트가 같은 방 멤버에게 브로드캐스트된다.

---

### 5-7. 채팅방 나가기

```
POST /api/chat/rooms/{roomId}/leave
```

Auth Required: **O**

Path Parameter

| 파라미터 | 타입 | 필수 | 설명 |
|----------|------|------|------|
| `roomId` | Long | Y | 채팅방 ID |

Response Body

```
{
  "success": true,
  "data": {
    "roomId": 201,
    "memberId": 9,
    "status": "LEFT",
    "updatedAt": "2026-04-27T15:10:00Z"
  }
}
```

Response Field

| 필드         | 타입             | 설명                      |
|------------|----------------|-------------------------|
| `success`  | String        | 성공여부                    |
| `roomId`   | Long   | 채팅방 ID                  |
| `memberId` | Long   | 사용자 ID                  |
| `status`   | String         | 사용자의 채팅방에서의 현 상태        |
| `updatedAt` | DateTime       | 사용자의 채팅방에서의 마지막 업데이트 날짜 |

Validation / Business Rules

- 인증된 사용자 + 해당 방의 멤버만 가능.
- 이미 `LEFT` → `ALREADY_LEFT`.
- 나가기 성공 시 `chat.room.member.left` 이벤트가 같은 방 멤버에게 브로드캐스트된다.

---

### 5-8. 참여자 초대

```
POST /api/chat/rooms/{roomId}/invite
```

Auth Required: **O**

Path Parameter

| 파라미터 | 타입 | 필수 | 설명 |
|----------|------|------|------|
| `roomId` | Long | Y | 채팅방 ID |

Request Body

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `inviteeIds` | Long[] | Y | 초대할 사용자 ID 목록 (1~50명) |

Response Body

```
{
  "success": true,
  "data": {
    "invited": [
      { "memberId": 12, "status": "INVITED" },
      { "memberId": 13, "status": "INVITED" }
    ],
    "skipped": [
      { "memberId": 14, "reason": "ALREADY_JOINED" }
    ]
  }
}
```

Response Field

| 필드          | 타입       | 설명                      |
|-------------|----------|-------------------------|
| `success`   | String   | 성공여부                    |
| `memberId`  | Long     | 사용자 ID                  |
| `invited`   | List     | 초대된 사용자 그룹              |
| `status`    | String   | 사용자의 채팅방에서의 현 상태        |
| `updatedAt` | DateTime | 사용자의 채팅방에서의 마지막 업데이트 날짜 |
| `skipped`   | List     | 초대되지 못한 사용자 그룹          |
| `reason`    | String   | 초대되지 못한 이유              |


Validation / Business Rules

- 초대 가능 대상은 `DM` 외의 비공개 그룹 방. (공개 방은 그냥 join)
- 초대자 본인이 해당 방의 멤버여야 함.
- 이미 `JOINED` / `BANNED` 인 사용자는 `skipped` 에 사유와 함께 반환.
- 신규 `INVITED` 멤버에게 `chat.room.member.invited` 이벤트 발송.

---

### 5-9. 알림 설정 변경

```
PATCH /api/chat/rooms/{roomId}/notification
```

Auth Required: **O**

Path Parameter

| 파라미터 | 타입 | 필수 | 설명 |
|----------|------|------|------|
| `roomId` | Long | Y | 채팅방 ID |

Request Body

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `enabled` | Boolean | Y | 알림 수신 여부 |

Response Body

```
{
  "success": true,
  "data": {
    "roomId": 201,
    "memberId": 9,
    "notificationEnabled": false,
    "updatedAt": "2026-04-27T15:20:00Z"
  }
}
```

Response Field

| 필드          | 타입       | 설명                      |
|-------------|----------|-------------------------|
| `success`   | String   | 성공여부                    |
| `roomId`    | Long     | 채팅방 ID                  |
| `notificationEnabled`    | String   | 사용자의 채팅방에서의 아림 여부       |
| `updatedAt` | DateTime | 사용자의 채팅방에서의 마지막 업데이트 날짜 |


Validation / Business Rules

- 인증된 사용자 + 해당 방의 활성 멤버만 가능.

---


### 5-10. 채팅 이력 조회

```
GET /api/chat/history/{roomId}
```

Auth Required: **O**

Path Parameter

| 파라미터 | 타입 | 필수 | 설명 |
|----------|------|------|------|
| `roomId` | Long | Y | 채팅방 ID |

Query Parameter

| 파라미터 | 타입 | 필수 | 설명 |
|----------|------|------|------|
| `before` | Long | N | 이 메시지 ID 보다 과거의 것만 조회. 미지정 시 최신 메시지부터 |
| `limit` | Integer | N | 페이지 크기 (기본 30, 최대 100) |

Response Body

```
{
  "success": true,
  "senderId": 9,
  "data": {
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
  }
}
```

Response Field

| 필드 | 타입 | 설명 |
|------|------|------|
| `messageId` | Long | 메시지 ID |
| `roomId` | Long | 채팅방 ID |
| `senderId` | Long | 발신자 ID (시스템 메시지는 0) |
| `type` | String | `TEXT` / `IMAGE` / `FILE` / `SYSTEM` |
| `content` | String | 텍스트 본문 또는 파일 URL |
| `status` | String | `ACTIVE` / `DELETED` |
| `createdAt` | DateTime | 생성 시각 |
| `nextCursor` | Long \| null | 다음 페이지 커서  |
| `hasNext` | Boolean | 다음 페이지 존재 여부 |

Validation / Business Rules

- 인증된 사용자 + 해당 방의 멤버 (`DM`/`GROUP` 비공개 방의 경우)만 조회 가능.
- `GAME`  공개 방은 비멤버도 조회 가능하도록 옵션 가능 (정책에 따라 결정).
- 정렬: `messageId DESC` (최신순).
- `DELETED` 메시지는 `content` 가 `"[삭제된 메시지입니다]"` 로 마스킹되어 반환.

---


### 5-11. WebSocket (STOMP)

### 5-11-1. 연결 (CONNECT)

```
WS  /ws/chat   (STOMP over WebSocket, SockJS fallback 지원)
```

CONNECT 헤더

| 헤더 | 필수 | 설명 |
|------|------|------|
| `Authorization` | Y | `Bearer {accessToken}` |
| `accept-version` | Y | `1.1,1.2` |
| `heart-beat` | N | `10000,10000` 권장 |

연결 시 처리

- 인증 실패 시 STOMP `ERROR` 프레임 후 연결 종료.
- 서버는 `userId ↔ session ↔ subscriptions` 매핑을 in-memory(예: ConcurrentHashMap) 에 저장.
- 동일 사용자의 다중 디바이스 접속 허용 (sessionId 별로 분리 관리).
- 연결 성공 시 `/user/queue/system` 으로 다음 페이로드를 푸시:

```
{
  "event": "CONNECTED",
  "userId": 9,
  "sessionId": "ws-3f1a..."
}
```

### 5-11-2. 구독 destination (SUBSCRIBE)

| Destination | 설명 |
|-------------|------|
| `/topic/rooms/{roomId}` | 해당 방의 모든 이벤트 (메시지/입퇴장/수정/읽음 등) |
| `/topic/rooms/{roomId}/messages` | 해당 방의 메시지 전용 채널 (위와 분리하고 싶을 때) |
| `/topic/rooms/{roomId}/typing` | 타이핑 인디케이터 채널 (휘발성, DB 저장 안 함) |
| `/user/queue/notifications` | 자기 자신에게 오는 개인 알림 (초대, mentioned 등) |
| `/user/queue/system` | 자기 자신에게 오는 시스템 메시지 (연결/오류) |

구독 시점에 서버가 `roomId` 에 대한 멤버 권한을 검증한다. 비멤버가 비공개 방을 구독하면 `ERROR` 프레임 반환.

### 5-11-3. 발신 destination (SEND)

#### 5-11-3-1. 메시지 전송

```
SEND  /app/chat.send
```

Headers

| 헤더 | 필수 | 설명 |
|------|------|------|
| `Authorization` | Y | `Bearer {accessToken}` (CONNECT 시 검증되었어도 destination 별 재검증) |

Payload

```
{
  "clientMessageId": "cm-9b2f-...",
  "roomId": 201,
  "type": "TEXT",
  "content": "방금 홈런!!"
}
```

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `clientMessageId` | String | Y | 클라이언트가 발급한 임시 ID (가짜 message id). 재전송 시 동일 값 유지하여 중복 저장 방지 |
| `roomId` | Long | Y | 대상 채팅방 ID |
| `type` | String | Y | `TEXT` / `IMAGE` / `FILE` |
| `content` | String | Y | 본문 또는 업로드 결과 URL |

Server → Client (성공)

`/topic/rooms/{roomId}/messages` 로 브로드캐스트:

```
{
  "event": "MESSAGE_SENT",
  "clientMessageId": "cm-9b2f-...",
  "messageId": 9982,
  "roomId": 201,
  "senderId": 9,
  "type": "TEXT",
  "content": "방금 홈런!!",
  "createdAt": "2026-04-27T14:22:30Z"
}
```

Server → Sender (실패)

`/user/queue/system` 으로 송신자 본인에게만:

```
{
  "event": "MESSAGE_FAILED",
  "clientMessageId": "cm-9b2f-...",
  "errorCode": "MESSAGE_TOO_LONG",
  "message": "메시지 본문은 4000자를 초과할 수 없습니다."
}
```

Validation / Business Rules

- 인증된 사용자 + (비공개 방이면) 활성 멤버만 발송 가능.
- `clientMessageId` 가 동일한 요청은 멱등 처리 (이미 저장된 messageId 를 재반환).
- DB 저장 실패 시 최대 3회 재시도, 그래도 실패하면 `MESSAGE_FAILED` 응답.
- 동일 채팅방 메시지는 `messageId` (BIGSERIAL) 의 단조 증가로 순서 보장.

---

#### 5-11-3-2. 읽음 상태 갱신

```
SEND  /app/chat.read
```

Payload

```
{
  "roomId": 201,
  "lastReadMessageId": 9982
}
```

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `roomId` | Long | Y | 채팅방 ID |
| `lastReadMessageId` | Long | Y | 사용자가 마지막으로 읽은 메시지 ID |

처리

1. 사용자/방 권한 검증.
2. Redis `chat:room:{roomId}:user:{userId}:lastRead` 와 비교 후 더 큰 경우에만 갱신 (단조 증가 검증).
3. 같은 방의 다른 멤버에게 `/topic/rooms/{roomId}` 로 multicast.
4. DB 영속화는 별도 스케줄러(2~3초 주기) 또는 disconnect/shutdown 시점에서 일괄 반영.

Server → Other Members

```
{
  "event": "READ_UPDATED",
  "roomId": 201,
  "userId": 9,
  "lastReadMessageId": 9982,
  "updatedAt": "2026-04-27T14:22:35Z"
}
```

Validation / Business Rules

- `DM` / 비공개 방 멤버에게만 의미. 공개 방의 읽음 표시는 정책에 따라 비활성화 가능.
- 이전 값보다 작거나 같은 `lastReadMessageId` 는 무시(역행 방지).

---

#### 5-11-3-3. 타이핑 인디케이터

```
SEND  /app/chat.typing
```

Payload

```
{
  "roomId": 201,
  "typing": true
}
```

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `roomId` | Long | Y | 채팅방 ID |
| `typing` | Boolean | Y | 타이핑 시작/종료 |

Server → Other Members (`/topic/rooms/{roomId}/typing`)

```
{
  "event": "TYPING",
  "roomId": 201,
  "userId": 9,
  "typing": true,
  "at": "2026-04-27T14:22:36Z"
}
```

Validation / Business Rules

- 휘발성 이벤트, DB/Redis 영속화 없음.
- 같은 사용자가 5초 이내에 다시 `typing=true` 를 보내면 디바운스로 throttle 권장.

---

#### 5-11-3-4. 서버 발신 이벤트 종류 (`/topic/rooms/{roomId}` 구독자에게 푸시)

| event | 발생 시점 | 페이로드 핵심 필드 |
|-------|-----------|-------------------|
| `MESSAGE_SENT` | 새 메시지 발송 성공 | `messageId`, `senderId`, `type`, `content`, `createdAt` |
| `MESSAGE_DELETED` | 메시지 삭제 성공 | `messageId`, `deletedBy`, `at` |
| `MEMBER_JOINED` | 사용자 입장 | `memberId`, `joinedAt` |
| `MEMBER_LEFT` | 사용자 나감 | `memberId`, `at` |
| `MEMBER_INVITED` | 새 사용자 초대 | `memberIds[]`, `invitedBy`, `at` |
| `ROOM_UPDATED` | 채팅방 메타 변경 | `title`, `imageUrl`, `updatedAt` |
| `READ_UPDATED` | 읽음 상태 갱신 | `userId`, `lastReadMessageId`, `updatedAt` |
| `TYPING` | 타이핑 인디케이터 | `userId`, `typing` |

각 이벤트는 다음 공통 envelope 를 따른다.

```
{
  "event": "MESSAGE_SENT",
  "roomId": 201,
  "occurredAt": "2026-04-27T14:22:30Z",
  "payload": { ... 이벤트별 필드 ... }
}
```

---

#### 5-11-3-5. DISCONNECT

- STOMP `DISCONNECT` 또는 TCP 연결 종료 시 서버는 다음을 수행한다:
    - in-memory `userId ↔ session` 매핑에서 해당 sessionId 제거.
    - Redis 의 `chat:room:{roomId}:online:{userId}` (해당 사용자가 구독 중이던 방들) 정리.
    - 마지막 read 값을 DB 에 flush.
- 동일 사용자의 다른 세션이 남아있다면 사용자 단위의 online 상태는 유지.

---


---

### 5-12. 메시지 삭제

```
DELETE /api/chat/messages/{messageId}
```

Auth Required: **O**

Path Parameter

| 파라미터 | 타입 | 필수 | 설명 |
|----------|------|------|------|
| `messageId` | Long | Y | 메시지 ID |

Response Body

```
{
  "success": true,
  "data": {
    "messageId": 9981,
    "roomId": 201,
    "status": "DELETED",
    "updatedAt": "2026-04-27T15:30:00Z"
  }
}
```

Validation / Business Rules

- 인증된 사용자 + 해당 방의 멤버만 가능.
- 본인이 보낸 메시지만 삭제 가능 (`FORBIDDEN`).
- 이미 `DELETED` → 멱등 처리 (200 OK).
- 삭제 성공 시 `chat.message.deleted` 이벤트가 같은 방 멤버에게 브로드캐스트.

---

### 5-13. 메시지 첨부 파일 업로드

```
POST /api/chat/messages/upload
```

Auth Required: **O**

Content-Type: `multipart/form-data`

Form Field

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `file` | File | Y | 업로드할 파일 (이미지/일반 파일) |
| `roomId` | Long | Y | 대상 채팅방 ID (멤버 검증용) |

Response Body

```
{
  "success": true,
  "data": {
    "uploadId": "u-2c0a9f1b",
    "url": "https://cdn.example.com/chat/2026/04/27/u-2c0a9f1b.png",
    "type": "IMAGE",
    "size": 215433,
    "mimeType": "image/png",
    "expiresAt": "2026-04-27T15:40:00Z"
  }
}
```

Validation / Business Rules

- 인증된 사용자 + 해당 방의 멤버만 가능.
- 업로드 결과로 받은 `url` 을 STOMP `SEND /app/chat.send` 의 `content` 에 담아 메시지를 발송한다.
- 이미지 5MB / 파일 25MB 기본 제한 (정책 가변).
- 허용 MIME: 이미지 (`image/jpeg`, `image/png`, `image/webp`, `image/gif`), 문서 (`application/pdf`, ...).
- `expiresAt` 이 지나도록 메시지를 보내지 않으면 업로드 파일은 GC.

---

## 부록 A.  비기능적 정책

| 항목 | 정책 |
|------|------|
| 메시지 본문 최대 길이 | 4,000 자 |
| 채팅방 이름 길이 | 1~100 자 |
| 페이지 사이즈 기본/최대 | 20 / 100 |
| 메시지 발송 속도 제한 | 사용자당 초당 10건 (`RATE_LIMITED`) |
| Redis Key 네이밍 | `chat:room:{roomId}:user:{userId}:lastRead` 등 prefix 통일 |
| 메시지 순서 보장 | DB `messageId` (BIGSERIAL) 단조 증가 + `(room_id, id)` 인덱스 |
| Multi-instance 브로드캐스트 | Redis Pub/Sub 또는 STOMP relay 채택 (배포 환경에 따라 결정) |


## 부록 B. 도메인 ↔ API 매핑 요약

| 도메인 행위 | REST/STOMP                                                 |
|-------------|------------------------------------------------------------|
| `ChatRoom.create()` | `POST /api/chat/create`                                    |
| `ChatRoom.join()` | `POST /api/chat/rooms/{roomId}/join`                       |
| `ChatRoom.leave()` | `POST /api/chat/rooms/{roomId}/leave`                      |
| `ChatRoom.invite()` | `POST /api/chat/rooms/{roomId}/invite`                     |
| `ChatRoom.rename()` / `changeImage()` | `PATCH /api/chat/rooms/{roomId}`                           |
| `ChatRoom.changeNotification()` | `PATCH /api/chat/rooms/{roomId}/notification`              |
| `ChatRoom.markRead()` | `SEND /app/chat.read`                                      |
| `Message.send()` | `SEND /app/chat.send`                                      |
| `Message.softDelete()` | `DELETE /api/chat/messages/{messageId}`                    |
| 첨부 파일 업로드 | `POST /api/chat/messages/upload`                           |
| 이력 조회 | `GET /api/chat/history/{roomId}`                           |
| 목록 조회 | `GET /api/chat/rooms`, `GET /api/chat/rooms/game/{gameId}` |


## 6. Notification 도메인

### 엔드포인트 목록

| method | path | auth required | 설명 |
| --- | --- | --- | --- |
| GET | /api/notifications/settings | O | 알림 설정 조회 |
| PUT | /api/notifications/settings | O | 알림 설정 변경 |
| GET | /api/notifications/channels | O | 알림 채널 목록 조회 |
| POST | /api/notifications/channels | O | 알림 채널 추가 |
| PUT | /api/notifications/channels/{channel} | O | 알림 채널 수정 |
| DELETE | /api/notifications/channels/{channel} | O | 알림 채널 삭제 |
| GET | /api/notifications/history | O | 알림 발송 이력 조회 |

---

### 6-1. 알림 설정 조회

```
GET /api/notifications/settings
```

#### Response Body

```json
{
  "success": true,
  "data": {
    "ticketOpenAlert": true,
    "gameStartAlert": true,
    "paymentAlert": true
  }
}
```

#### Response Field

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `ticketOpenAlert` | Boolean | 티켓 오픈 알림 ON/OFF |
| `gameStartAlert` | Boolean | 경기 시작 알림 ON/OFF |
| `paymentAlert` | Boolean | 결제 알림 ON/OFF |

---

### 6-2. 알림 설정 변경

```
PUT /api/notifications/settings
```

변경할 필드만 포함해도 됨. 미포함 필드는 기존 값 유지.

#### Request Body

```json
{
  "ticketOpenAlert": true,
  "gameStartAlert": false,
  "paymentAlert": true
}
```

#### Request Field

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `ticketOpenAlert` | Boolean | N | 티켓 오픈 알림 ON/OFF |
| `gameStartAlert` | Boolean | N | 경기 시작 알림 ON/OFF |
| `paymentAlert` | Boolean | N | 결제 알림 ON/OFF |

#### Response Body

```json
{
  "success": true,
  "data": {
    "ticketOpenAlert": true,
    "gameStartAlert": false,
    "paymentAlert": true
  }
}
```

---

### 6-3. 알림 채널 목록 조회

```
GET /api/notifications/channels
```

#### Response Body

```json
{
  "success": true,
  "data": [
    {
      "channel": "EMAIL",
      "channelTarget": "user@example.com",
      "isEnabled": true
    },
    {
      "channel": "DISCORD",
      "channelTarget": "https://discord.com/api/webhooks/xxx/yyy",
      "isEnabled": false
    }
  ]
}
```

#### Response Field

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `channel` | String | 채널 종류 (`EMAIL` \| `SMS` \| `DISCORD` \| `PUSH`) |
| `channelTarget` | String | 수신 대상 (이메일 / 전화번호 / 웹훅 URL 등) |
| `isEnabled` | Boolean | 채널 활성화 여부 |

---

### 6-4. 알림 채널 추가

```
POST /api/notifications/channels
```

#### Request Body

```json
{
  "channel": "DISCORD",
  "channelTarget": "https://discord.com/api/webhooks/xxx/yyy",
  "isEnabled": true
}
```

#### Request Field

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `channel` | String | Y | 채널 종류 (`EMAIL` \| `SMS` \| `DISCORD` \| `PUSH`) |
| `channelTarget` | String | Y | 수신 대상 |
| `isEnabled` | Boolean | N | 활성화 여부 (기본 `true`) |

#### Response Body

```json
{
  "success": true,
  "data": {
    "channel": "DISCORD",
    "channelTarget": "https://discord.com/api/webhooks/xxx/yyy",
    "isEnabled": true
  }
}
```

#### Http Status / Error Code

| 에러 코드 | HTTP Status | 설명 |
| --- | --- | --- |
| `CHANNEL_ALREADY_EXISTS` | 409 | 이미 등록된 채널 종류 |
| `INVALID_CHANNEL_TARGET` | 400 | 채널 대상 형식 오류 |

---

### 6-5. 알림 채널 수정

```
PUT /api/notifications/channels/{channel}
```

#### Path Variable

| 변수 | 타입 | 설명 |
| --- | --- | --- |
| `channel` | String | 채널 종류 (`EMAIL` \| `SMS` \| `DISCORD` \| `PUSH`) |

#### Request Body

```json
{
  "channelTarget": "new@example.com",
  "isEnabled": true
}
```

#### Request Field

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `channelTarget` | String | N | 변경할 수신 대상 |
| `isEnabled` | Boolean | N | 변경할 활성화 여부 |

#### Response Body

```json
{
  "success": true,
  "data": {
    "channel": "EMAIL",
    "channelTarget": "new@example.com",
    "isEnabled": true
  }
}
```

#### Http Status / Error Code

| 에러 코드 | HTTP Status | 설명 |
| --- | --- | --- |
| `CHANNEL_NOT_FOUND` | 404 | 등록되지 않은 채널 |
| `INVALID_CHANNEL_TARGET` | 400 | 채널 대상 형식 오류 |

---

### 6-6. 알림 채널 삭제

```
DELETE /api/notifications/channels/{channel}
```

#### Path Variable

| 변수 | 타입 | 설명 |
| --- | --- | --- |
| `channel` | String | 채널 종류 (`EMAIL` \| `SMS` \| `DISCORD` \| `PUSH`) |

#### Response

```
HTTP 204 No Content
```

#### Http Status / Error Code

| 에러 코드 | HTTP Status | 설명 |
| --- | --- | --- |
| `CHANNEL_NOT_FOUND` | 404 | 등록되지 않은 채널 |

---

### 6-7. 알림 발송 이력 조회

```
GET /api/notifications/history
```

#### Query Parameter

| 파라미터 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `channel` | String | N | 채널 필터 (`EMAIL` \| `SMS` \| `DISCORD` \| `PUSH`) |
| `type` | String | N | 알림 종류 필터 (`TICKET_OPEN` \| `GAME_START` \| `PAYMENT` \| `MENTION`) |
| `cursor` | String | N | 페이지네이션 커서 |
| `limit` | Integer | N | 페이지 크기 (기본 20) |

#### Response Body

```json
{
  "success": true,
  "data": {
    "items": [
      {
        "historyId": 50001,
        "channel": "EMAIL",
        "type": "PAYMENT",
        "title": "결제가 완료되었습니다",
        "content": "KIA vs 삼성 | S석 22번 좌석 예매가 완료되었습니다.",
        "status": "SENT",
        "sentAt": "2025-04-21T10:05:30Z"
      }
    ],
    "nextCursor": "eyJpZCI6NTAwMDB9",
    "hasNext": false,
    "totalCount": 15
  }
}
```

#### Response Field

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `historyId` | Long | 이력 ID |
| `channel` | String | 발송 채널 |
| `type` | String | 알림 종류 |
| `title` | String | 알림 제목 |
| `content` | String | 알림 내용 |
| `status` | String | 발송 결과 (`SENT` \| `FAILED`) |
| `sentAt` | String (ISO8601) | 실제 발송 시각 |

---

## 부록 A. 전체 엔드포인트 요약

| method | path | auth required | 설명 |
| --- | --- | --- | --- |
| GET | /oauth2/authorization/{registrationId} | N | 소셜 로그인 (Google / Kakao) |
| GET | /oauth2/callback | N | 소셜 로그인 콜백 (토큰 발급) |
| POST | /api/auth/token/refresh | N | 액세스 토큰 갱신 |
| POST | /api/auth/logout | O | 로그아웃 |
| GET | /api/members/me | O | 내 정보 조회 |
| PATCH | /api/members/me/nickname | O | 내 정보 수정 (닉네임) |
| DELETE | /api/members/me | O | 회원 탈퇴 |
| POST | /api/members/me/favorite-teams | O | 선호 팀 추가 |
| GET | /api/members/me/favorite-teams | O | 선호 팀 목록 조회 |
| PATCH | /api/members/me/favorite-teams/{teamId}/priority | O | 선호 팀 우선순위 수정 |
| DELETE | /api/members/me/favorite-teams/{teamId} | O | 선호 팀 삭제 |
| GET | /api/members/me/activity/monthly | O | 월별 응원 활동 통계 |
| GET | /api/teams | N | 팀 목록 조회 |
| GET | /api/teams/{teamId} | N | 팀 상세 조회 |
| GET | /api/games | N | 경기 목록 조회 |
| GET | /api/games/{gameId} | N | 경기 상세 조회 |
| GET | /api/games/{gameId}/seats | N | 좌석 목록 조회 |
| GET | /api/tickets | O | 내 예매 내역 조회 |
| GET | /api/tickets/{ticketId} | O | 예매 상세 조회 |
| POST | /api/tickets/reserve | O | 티켓 예매 (좌석 선점) |
| POST | /api/tickets/queue/enter | O | 대기열 입장 |
| GET | /api/tickets/queue/position | O | 대기열 순번 조회 (폴링) |
| GET | /api/tickets/queue/sse | O | 대기열 순번 SSE 스트리밍 |
| DELETE | /api/tickets/queue/leave | O | 대기열 명시적 이탈 |
| POST | /api/tickets/{ticketId}/cancel | O | 티켓 취소 |
| POST | /api/payments/request | O | 결제 요청 등록 |
| POST | /api/payments/verify | O | 결제 검증 |
| POST | /api/payments/webhook | N | PG Webhook 수신 |
| GET | /api/payments | O | 내 결제 내역 조회 |
| GET | /api/payments/{paymentId} | O | 결제 상세 조회 |
| POST | /api/payments/{paymentId}/refund | O | 환불 요청 |
| GET | /api/chat/rooms | O | 내가 참여한 채팅방 목록 조회 |
| GET | /api/chat/rooms/public | N | 공개 채팅방 목록 조회 (GAME/TEAM) |
| GET | /api/chat/rooms/team/{teamName} | N | 팀별 채팅방 조회 |
| WS | /ws/chat (STOMP) | O | WebSocket 연결 / 메시지 전송 |
| GET | /api/chat/intensity/{gameId} | N | 응원 열기 지수 조회 |
| GET | /api/chat/history/{roomId} | O | 채팅 이력 조회 |
| GET | /api/notifications/settings | O | 알림 설정 조회 |
| PUT | /api/notifications/settings | O | 알림 설정 변경 |
| GET | /api/notifications/channels | O | 알림 채널 목록 조회 |
| POST | /api/notifications/channels | O | 알림 채널 추가 |
| PUT | /api/notifications/channels/{channel} | O | 알림 채널 수정 |
| DELETE | /api/notifications/channels/{channel} | O | 알림 채널 삭제 |
| GET | /api/notifications/history | O | 알림 발송 이력 조회 |
| POST | /api/admin/teams | O (ADMIN) | 팀 등록 |
| PUT | /api/admin/teams/{teamId} | O (ADMIN) | 팀 수정 |
| DELETE | /api/admin/teams/{teamId} | O (ADMIN) | 팀 비활성화 |
| POST | /api/admin/games | O (ADMIN) | 경기 등록 |
| PUT | /api/admin/games/{gameId} | O (ADMIN) | 경기 수정 |
| PATCH | /api/admin/games/{gameId}/status | O (ADMIN) | 경기 상태 변경 |
| DELETE | /api/admin/games/{gameId} | O (ADMIN) | 경기 삭제 (soft delete) |
| POST | /api/admin/games/{gameId}/seats/bulk | O (ADMIN) | 좌석 일괄 생성 |
| PATCH | /api/admin/games/{gameId}/seats/price | O (ADMIN) | 좌석 등급 가격 수정 |
| PATCH | /api/admin/games/{gameId}/seats/{seatId}/status | O (ADMIN) | 좌석 상태 수동 변경 |
| POST | /api/admin/chat/rooms | O (ADMIN) | 채팅방 생성 |
| POST | /api/admin/notifications/dlq/retry | O (ADMIN) | DLQ 알림 재처리 |

---

## 부록 B. 에러 코드 전체 목록

| 에러 코드 | HTTP Status | 도메인 | 설명 |
| --- | --- | --- | --- |
| `UNAUTHORIZED` | 401 | 공통 | 인증 실패 |
| `FORBIDDEN` | 403 | 공통 | 권한 없음 |
| `NOT_FOUND` | 404 | 공통 | 리소스 없음 |
| `INVALID_INPUT` | 400 | 공통 | 입력값 오류 |
| `INTERNAL_ERROR` | 500 | 공통 | 서버 오류 |
| `INVALID_REFRESH_TOKEN` | 401 | Auth | 유효하지 않은 리프레시 토큰 |
| `TEAM_NOT_FOUND` | 404 | Team | 팀 없음 |
| `FAVORITE_TEAM_ALREADY_EXISTS` | 409 | Member | 선호 팀 중복 |
| `FAVORITE_TEAM_NOT_FOUND` | 404 | Member | 선호 팀 미등록 |
| `GAME_NOT_FOUND` | 404 | Ticketing | 경기 없음 |
| `GAME_NOT_ON_SALE` | 422 | Ticketing | 판매 중이 아닌 경기 |
| `SEAT_NOT_FOUND` | 404 | Ticketing | 좌석 없음 |
| `SEAT_ALREADY_RESERVED` | 409 | Ticketing | 이미 선점된 좌석 |
| `SEAT_RESERVATION_EXPIRED` | 422 | Ticketing/Payment | 좌석 선점 만료 |
| `TICKET_NOT_FOUND` | 404 | Ticketing | 티켓 없음 |
| `TICKET_LIMIT_EXCEEDED` | 422 | Ticketing | 경기당 1인 최대 4매 초과 |
| `TICKET_NOT_CANCELLABLE` | 422 | Ticketing | 취소 불가 상태 |
| `TICKET_NOT_PAYABLE` | 422 | Payment | 결제 불가 티켓 상태 |
| `TICKET_RESERVATION_EXPIRED` | 422 | Payment | 선점 10분 만료 |
| `QUEUE_NOT_JOINED` | 404 | Ticketing | 대기열 미입장 |
| `QUEUE_ALREADY_JOINED` | 409 | Ticketing | 이미 대기열 입장 상태 |
| `PAYMENT_NOT_FOUND` | 404 | Payment | 결제 없음 |
| `PAYMENT_ALREADY_EXISTS` | 409 | Payment | idempotencyKey 중복 |
| `PAYMENT_AMOUNT_MISMATCH` | 422 | Payment | 결제 금액 불일치 |
| `PAYMENT_ALREADY_COMPLETED` | 409 | Payment | 이미 검증 완료된 결제 |
| `PAYMENT_NOT_REFUNDABLE` | 422 | Payment | 환불 불가 상태 |
| `INVALID_WEBHOOK_SIGNATURE` | 401 | Payment | Webhook 서명 검증 실패 |
| `CHAT_ROOM_NOT_FOUND` | 404 | Chat | 채팅방 없음 |
| `CHANNEL_ALREADY_EXISTS` | 409 | Notification | 채널 중복 |
| `CHANNEL_NOT_FOUND` | 404 | Notification | 채널 없음 |
| `INVALID_CHANNEL_TARGET` | 400 | Notification | 채널 대상 형식 오류 |
| `TEAM_NAME_DUPLICATE` | 409 | Admin | 동일 종목 내 팀 이름 중복 |
| `INVALID_SPORT_TYPE` | 400 | Admin | 지원하지 않는 종목 값 |
| `TEAM_HAS_ACTIVE_GAMES` | 422 | Admin | 진행 중인 경기가 있어 팀 비활성화 불가 |
| `SAME_TEAM_GAME` | 400 | Admin | team1Id와 team2Id가 동일 |
| `INVALID_GAME_TIME` | 400 | Admin | 현재 시각 이전의 경기 시각 |
| `SPORT_TYPE_MISMATCH` | 400 | Admin | 두 팀의 sport_type 불일치 |
| `GAME_NOT_MODIFIABLE` | 422 | Admin | SCHEDULED 상태 아님 — 수정 불가 |
| `INVALID_STATUS_TRANSITION` | 422 | Admin | 허용되지 않는 경기 상태 전이 |
| `NO_SEATS_REGISTERED` | 422 | Admin | 좌석 없는 상태에서 ON_SALE 전환 불가 |
| `SEATS_ALREADY_EXISTS` | 409 | Admin | 이미 좌석이 등록된 경기 |
| `INVALID_SEAT_DATA` | 400 | Admin | 좌석 데이터 형식 오류 |
| `CHAT_ROOM_ALREADY_EXISTS` | 409 | Admin | 해당 game/team 채팅방 이미 존재 |
| `NICKNAME_DUPLICATE` | 409 | Member | 중복 닉네임 |
| `INVALID_PRIORITY` | 400 | Member | 선호 팀 우선순위 범위/중복 오류 |
| `MEMBER_ALREADY_WITHDRAWN` | 422 | Member | 이미 탈퇴한 회원 |
| `QUEUE_ENTRY_BEFORE_SALE` | 422 | Ticketing | 예매 오픈 전 대기열 진입 시도 |
| `RESERVATION_SESSION_EXPIRED` | 422 | Ticketing | 예매 세션(UUID) 만료 |
| `RESERVATION_UUID_INVALID` | 401 | Ticketing | 유효하지 않은 예매 진입 UUID |
| `PURCHASE_LIMIT_EXCEEDED` | 422 | Ticketing | 인당 좌석 제한 초과 |
| `GAME_DELETE_FORBIDDEN` | 422 | Admin | 확정 예매가 있어 경기 삭제 불가 |
| `INVALID_SEAT_STATUS_TRANSITION` | 422 | Admin | 허용되지 않는 좌석 상태 전이 |

---

## 부록 C. Redis Streams 이벤트 정의

> Kafka 대신 Redis Streams + Consumer Group 방식 채택 (인프라 단순화).

| Stream Key | Producer | Consumer | 트리거 | Payload 예시 |
| --- | --- | --- | --- | --- |
| `ticket.opened` | Ticketing | Notification | 경기 상태 `OPEN` 전환 시 | `{"gameId": 101, "saleStartAt": "..."}` |
| `payment.completed` | Payment | Notification, Ticketing | 결제 검증 완료 시 | `{"paymentId": 7001, "memberId": 1, "ticketId": 9001}` |
| `game.starting` | Ticketing (@Scheduled) | Notification | 경기 시작 1시간 전 | `{"gameId": 101, "startAt": "..."}` |
| `chat.mentioned` | Chat | Notification | 채팅 @멘션 발생 시 | `{"roomId": 201, "mentionedMemberId": 2, "messageId": 30001}` |

### Consumer Group 처리 원칙

- 모든 Stream은 Consumer Group 방식으로 소비 (`XREADGROUP`)
- 처리 성공 시 `XACK` 발행
- 처리 실패 시 3회 재시도 → Redis DLQ 키에 저장 (`notification:dlq:{streamKey}`)
- 관리자가 DLQ 재처리 API(`POST /api/admin/notifications/dlq/retry`)로 수동 재처리 가능

---

## 부록 D. 설계 결정 사항

### D-1. Cursor 기반 페이지네이션
Offset 방식은 대규모 데이터에서 `OFFSET N` 쿼리가 풀스캔에 가까워진다. 좌석(경기당 20,000건), 채팅 메시지(무한 증가) 등 대규모 테이블에서 Cursor 방식은 인덱스 탐색으로 일정한 응답 시간을 보장한다.

### D-2. 결제 2단계 흐름 (request → verify)
- 클라이언트 → PG SDK → PG사: 실제 카드 정보 교환 (서버 비개입)
- 클라이언트 → 서버 `/verify`: 금액·좌석 최종 확인
- Webhook: PG사 → 서버 — 클라이언트 verify 실패 시 백업 처리

이중 구조로 결제 누락·위변조를 방지한다.

### D-3. WebSocket 인증
HTTP 핸드쉐이크 단계에서 JWT 검증 (`ChannelInterceptor`). STOMP CONNECT 프레임의 `Authorization` 헤더 사용. 토큰 만료 시 연결 종료 후 클라이언트가 갱신 후 재연결.

### D-4. 알림 설정 partial update
`PUT /api/notifications/settings`는 RFC 7231 원칙상 전체 교체이나, 알림 설정 필드가 3개로 적고 클라이언트가 항상 전체 상태를 보유하므로 미포함 필드는 기존 값 유지로 처리한다. 명시적 `null` 전송은 `INVALID_INPUT` 오류로 거부한다.

### D-5. Admin API 별도 분리
팀/경기 등록은 `/api/admin/**` 네임스페이스로 분리한다. Spring Security에서 `ROLE_ADMIN` 권한 체크를 레이어 단에서 일괄 적용하여 개별 서비스 레이어 권한 검증 중복을 제거한다. 프로덕션에서는 IP 화이트리스트 또는 내부망 전용 포트로 추가 제한을 권장한다.

---

## 7. Admin 도메인

> **대상**: 팀/경기/좌석/채팅방 마스터 데이터 관리  
> **인증**: JWT + `ROLE_ADMIN` 필수 (모든 엔드포인트)  
> **용도**: 테스트 시나리오 실행 및 운영 마스터 데이터 세팅

### 엔드포인트 목록

| method | path | auth required | 설명 |
| --- | --- | --- | --- |
| POST | /api/admin/teams | O (ADMIN) | 팀 등록 |
| PUT | /api/admin/teams/{teamId} | O (ADMIN) | 팀 수정 |
| DELETE | /api/admin/teams/{teamId} | O (ADMIN) | 팀 비활성화 |
| POST | /api/admin/games | O (ADMIN) | 경기 등록 |
| PUT | /api/admin/games/{gameId} | O (ADMIN) | 경기 수정 |
| PATCH | /api/admin/games/{gameId}/status | O (ADMIN) | 경기 상태 변경 |
| POST | /api/admin/games/{gameId}/seats/bulk | O (ADMIN) | 좌석 일괄 생성 |
| POST | /api/admin/chat/rooms | O (ADMIN) | 채팅방 생성 |

---

### 7-1. 팀 등록

```
POST /api/admin/teams
```

#### Request Body

```json
{
  "name": "KIA 타이거즈",
  "shortName": "KIA",
  "sportType": "BASEBALL",
  "isActive": true
}
```

#### Request Field

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `name` | String | Y | 팀 정식 명칭 (최대 100자) |
| `shortName` | String | N | 팀 약칭 UI 표시용 (최대 20자) |
| `sportType` | String | Y | 종목 (`BASEBALL` \| `FOOTBALL` \| `BASKETBALL` 등) |
| `isActive` | Boolean | N | 활성 여부 (기본 `true`) |

#### Response Body

```json
{
  "success": true,
  "data": {
    "teamId": 1,
    "name": "KIA 타이거즈",
    "shortName": "KIA",
    "sportType": "BASEBALL",
    "isActive": true,
    "createdAt": "2025-04-21T00:00:00Z"
  }
}
```

#### Http Status / Error Code

| 에러 코드 | HTTP Status | 설명 |
| --- | --- | --- |
| `TEAM_NAME_DUPLICATE` | 409 | 동일 종목 내 팀 이름 중복 |
| `INVALID_SPORT_TYPE` | 400 | 지원하지 않는 종목 값 |

---

### 7-2. 팀 수정

```
PUT /api/admin/teams/{teamId}
```

#### Path Variable

| 변수 | 타입 | 설명 |
| --- | --- | --- |
| `teamId` | Long | 수정할 팀 ID |

#### Request Body

```json
{
  "name": "KIA 타이거즈",
  "shortName": "KIA",
  "isActive": true
}
```

#### Request Field

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `name` | String | N | 팀 정식 명칭 |
| `shortName` | String | N | 팀 약칭 |
| `isActive` | Boolean | N | 활성 여부 |

#### Response Body

```json
{
  "success": true,
  "data": {
    "teamId": 1,
    "name": "KIA 타이거즈",
    "shortName": "KIA",
    "sportType": "BASEBALL",
    "isActive": true
  }
}
```

#### Http Status / Error Code

| 에러 코드 | HTTP Status | 설명 |
| --- | --- | --- |
| `TEAM_NOT_FOUND` | 404 | 존재하지 않는 팀 |

---

### 7-3. 팀 비활성화

```
DELETE /api/admin/teams/{teamId}
```

> 물리 삭제가 아닌 `is_active = false` 처리. 진행 중인 경기가 있으면 거부.

#### Path Variable

| 변수 | 타입 | 설명 |
| --- | --- | --- |
| `teamId` | Long | 비활성화할 팀 ID |

#### Response

```
HTTP 204 No Content
```

#### Http Status / Error Code

| 에러 코드 | HTTP Status | 설명 |
| --- | --- | --- |
| `TEAM_NOT_FOUND` | 404 | 존재하지 않는 팀 |
| `TEAM_HAS_ACTIVE_GAMES` | 422 | 진행 중인 경기가 있어 비활성화 불가 |

---

### 7-4. 경기 등록

```
POST /api/admin/games
```

#### Request Body

```json
{
  "sportType": "BASEBALL",
  "team1Id": 1,
  "team2Id": 2,
  "team1IsHome": true,
  "gameTime": "2025-05-10T14:00:00Z",
  "venue": "광주-기아 챔피언스 필드",
  "isRivalMatch": false
}
```

#### Request Field

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `sportType` | String | Y | 종목 |
| `team1Id` | Long | Y | 첫 번째 팀 ID |
| `team2Id` | Long | Y | 두 번째 팀 ID |
| `team1IsHome` | Boolean | Y | team1이 홈팀인지 여부 |
| `gameTime` | String (ISO8601) | Y | 경기 시작 시각 |
| `venue` | String | Y | 경기장 이름 (최대 100자) |
| `isRivalMatch` | Boolean | N | 라이벌전 여부 (기본 `false`) |

#### Response Body

```json
{
  "success": true,
  "data": {
    "gameId": 101,
    "sportType": "BASEBALL",
    "team1": {
      "teamId": 1,
      "name": "KIA 타이거즈",
      "shortName": "KIA",
      "isHome": true
    },
    "team2": {
      "teamId": 2,
      "name": "삼성 라이온즈",
      "shortName": "삼성",
      "isHome": false
    },
    "gameTime": "2025-05-10T14:00:00Z",
    "venue": "광주-기아 챔피언스 필드",
    "status": "SCHEDULED",
    "totalSeats": 0,
    "availableSeats": 0,
    "isRivalMatch": false
  }
}
```

> 경기 등록 직후 `status = SCHEDULED`, `totalSeats = 0`.  
> 좌석 일괄 생성(7-7) 후 `status`를 `ON_SALE`로 변경(7-6).

#### Http Status / Error Code

| 에러 코드 | HTTP Status | 설명 |
| --- | --- | --- |
| `TEAM_NOT_FOUND` | 404 | team1Id 또는 team2Id 팀 없음 |
| `SAME_TEAM_GAME` | 400 | team1Id와 team2Id가 동일 |
| `INVALID_GAME_TIME` | 400 | 현재 시각 이전의 경기 시각 |
| `SPORT_TYPE_MISMATCH` | 400 | 두 팀의 sport_type이 다름 |

---

### 7-5. 경기 수정

```
PUT /api/admin/games/{gameId}
```

> `status = SCHEDULED` 상태일 때만 수정 가능.

#### Path Variable

| 변수 | 타입 | 설명 |
| --- | --- | --- |
| `gameId` | Long | 수정할 경기 ID |

#### Request Body

```json
{
  "gameTime": "2025-05-10T18:00:00Z",
  "venue": "대구 삼성 라이온즈 파크",
  "isRivalMatch": true
}
```

#### Request Field

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `gameTime` | String (ISO8601) | N | 변경할 경기 시작 시각 |
| `venue` | String | N | 변경할 경기장 |
| `isRivalMatch` | Boolean | N | 라이벌전 여부 |

#### Response Body

```json
{
  "success": true,
  "data": {
    "gameId": 101,
    "gameTime": "2025-05-10T18:00:00Z",
    "venue": "대구 삼성 라이온즈 파크",
    "isRivalMatch": true,
    "status": "SCHEDULED"
  }
}
```

#### Http Status / Error Code

| 에러 코드 | HTTP Status | 설명 |
| --- | --- | --- |
| `GAME_NOT_FOUND` | 404 | 존재하지 않는 경기 |
| `GAME_NOT_MODIFIABLE` | 422 | SCHEDULED 상태가 아니어서 수정 불가 |

---

### 7-6. 경기 상태 변경

```
PATCH /api/admin/games/{gameId}/status
```

> 상태 전이 규칙: `SCHEDULED → ON_SALE → SOLD_OUT / ONGOING → FINISHED`  
> 역방향 전이 불가.

#### Path Variable

| 변수 | 타입 | 설명 |
| --- | --- | --- |
| `gameId` | Long | 경기 ID |

#### Request Body

```json
{
  "status": "ON_SALE"
}
```

#### Request Field

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `status` | String | Y | 변경할 상태 (`SCHEDULED` \| `ON_SALE` \| `ONGOING` \| `FINISHED` \| `CANCELLED`) |

#### Response Body

```json
{
  "success": true,
  "data": {
    "gameId": 101,
    "previousStatus": "SCHEDULED",
    "currentStatus": "ON_SALE"
  }
}
```

#### Http Status / Error Code

| 에러 코드 | HTTP Status | 설명 |
| --- | --- | --- |
| `GAME_NOT_FOUND` | 404 | 존재하지 않는 경기 |
| `INVALID_STATUS_TRANSITION` | 422 | 허용되지 않는 상태 전이 |
| `NO_SEATS_REGISTERED` | 422 | 좌석이 없는 상태에서 ON_SALE 전환 불가 |

---

### 7-7. 좌석 일괄 생성

```
POST /api/admin/games/{gameId}/seats/bulk
```

> 경기 등록 후 좌석 구성 세팅에 사용.  
> 동일 경기에 이미 좌석이 있으면 거부 (중복 방지).

#### Path Variable

| 변수 | 타입 | 설명 |
| --- | --- | --- |
| `gameId` | Long | 경기 ID |

#### Request Body

```json
{
  "seats": [
    {
      "grade": "VIP",
      "section": "내야 1루",
      "rowNumber": "A",
      "seatNumber": 1,
      "price": 80000,
      "teamSide": "NEUTRAL"
    },
    {
      "grade": "S",
      "section": "외야 1루",
      "rowNumber": "C",
      "seatNumber": 10,
      "price": 15000,
      "teamSide": "TEAM1"
    }
  ]
}
```

#### Request Field (seats 배열 요소)

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `grade` | String | Y | 좌석 등급 (`VIP` \| `R` \| `S` \| `A` \| `OUTFIELD`) |
| `section` | String | N | 구역명 |
| `rowNumber` | String | N | 열 번호 |
| `seatNumber` | Integer | Y | 좌석 번호 |
| `price` | Integer | Y | 가격 (원, 0 이상) |
| `teamSide` | String | Y | 응원 구역 (`TEAM1` \| `TEAM2` \| `NEUTRAL`) |

#### Response Body

```json
{
  "success": true,
  "data": {
    "gameId": 101,
    "createdCount": 20000,
    "totalSeats": 20000
  }
}
```

#### Http Status / Error Code

| 에러 코드 | HTTP Status | 설명 |
| --- | --- | --- |
| `GAME_NOT_FOUND` | 404 | 존재하지 않는 경기 |
| `SEATS_ALREADY_EXISTS` | 409 | 이미 좌석이 등록된 경기 |
| `INVALID_SEAT_DATA` | 400 | 좌석 데이터 형식 오류 |

---

### 7-7-1. 좌석 등급 가격 수정

```
PATCH /api/admin/games/{gameId}/seats/price
```

#### Path Variable

| 변수 | 타입 | 설명 |
| --- | --- | --- |
| `gameId` | Long | 경기 ID |

#### Request Body

```json
{
  "grade": "S",
  "price": 35000
}
```

#### Request Field

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `grade` | String | Y | 좌석 등급 (`VIP` \| `R` \| `S` \| `A` \| `OUTFIELD`) |
| `price` | Integer | Y | 변경할 가격 (원, 0 이상) |

#### Response Body

```json
{
  "success": true,
  "data": {
    "gameId": 101,
    "grade": "S",
    "price": 35000,
    "updatedCount": 800
  }
}
```

#### Http Status / Error Code

| 에러 코드 | HTTP Status | 설명 |
| --- | --- | --- |
| `GAME_NOT_FOUND` | 404 | 존재하지 않는 경기 |
| `INVALID_SEAT_DATA` | 400 | 가격 유효성 오류 (0 미만) |

---

### 7-7-2. 좌석 상태 수동 변경

```
PATCH /api/admin/games/{gameId}/seats/{seatId}/status
```

> 운영 장애 대응용. 변경 이력은 audit log로 기록 권장.

#### Path Variable

| 변수 | 타입 | 설명 |
| --- | --- | --- |
| `gameId` | Long | 경기 ID |
| `seatId` | Long | game_seats.id |

#### Request Body

```json
{
  "status": "AVAILABLE"
}
```

#### Request Field

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `status` | String | Y | `AVAILABLE` \| `RESERVED` \| `SOLD` |

#### Response

```
HTTP 204 No Content
```

#### Http Status / Error Code

| 에러 코드 | HTTP Status | 설명 |
| --- | --- | --- |
| `GAME_NOT_FOUND` | 404 | 존재하지 않는 경기 |
| `SEAT_NOT_FOUND` | 404 | 존재하지 않는 좌석 |
| `INVALID_SEAT_STATUS_TRANSITION` | 422 | SOLD 상태로 수동 전환 불가 (결제 내역 없음) |

---

### 7-7-3. 경기 삭제 (soft delete)

```
DELETE /api/admin/games/{gameId}
```

> 물리 삭제가 아닌 `deleted_at` 처리. 확정 예매가 있으면 거부.

#### Path Variable

| 변수 | 타입 | 설명 |
| --- | --- | --- |
| `gameId` | Long | 경기 ID |

#### Response

```
HTTP 204 No Content
```

#### Http Status / Error Code

| 에러 코드 | HTTP Status | 설명 |
| --- | --- | --- |
| `GAME_NOT_FOUND` | 404 | 존재하지 않는 경기 |
| `GAME_DELETE_FORBIDDEN` | 422 | 확정 예매(CONFIRMED)가 존재하여 삭제 불가 |

---

### 7-9. DLQ 알림 재처리

```
POST /api/admin/notifications/dlq/retry
```

Redis DLQ에 쌓인 실패 알림 이벤트를 수동으로 재발송.

#### Request Body

```json
{
  "streamKey": "payment.completed",
  "limit": 50
}
```

#### Request Field

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `streamKey` | String | N | 특정 Stream만 재처리 (미입력 시 전체) |
| `limit` | Integer | N | 처리 건수 (기본 50, 최대 500) |

#### Response Body

```json
{
  "success": true,
  "data": {
    "retriedCount": 12,
    "failedCount": 0
  }
}
```

---

### 7-8. 채팅방 생성

```
POST /api/admin/chat/rooms
```

> 경기 등록 후 해당 경기의 실시간 채팅방을 수동 생성.  
> 자동 생성 정책을 쓸 경우 이 API는 필요 없으나, 테스트 시나리오에서는 명시적 생성을 권장.

#### Request Body

```json
{
  "type": "GAME",
  "gameId": 101,
  "teamId": null,
  "maxParticipants": 5000
}
```

#### Request Field

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `type` | String | Y | 채팅방 유형 (`GAME` \| `TEAM` \| `GENERAL`) |
| `gameId` | Long | 조건부 | `type=GAME`일 때 필수 |
| `teamId` | Long | 조건부 | `type=TEAM`일 때 필수 |
| `maxParticipants` | Integer | N | 최대 참여 인원 (기본 5000) |

#### Response Body

```json
{
  "success": true,
  "data": {
    "roomId": 201,
    "type": "GAME",
    "gameId": 101,
    "teamId": null,
    "maxParticipants": 5000,
    "createdAt": "2025-04-21T09:00:00Z"
  }
}
```

#### Http Status / Error Code

| 에러 코드 | HTTP Status | 설명 |
| --- | --- | --- |
| `GAME_NOT_FOUND` | 404 | `type=GAME`이고 gameId 없음 |
| `TEAM_NOT_FOUND` | 404 | `type=TEAM`이고 teamId 없음 |
| `CHAT_ROOM_ALREADY_EXISTS` | 409 | 해당 game/team의 채팅방 이미 존재 |
