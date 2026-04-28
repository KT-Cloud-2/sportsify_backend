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
| `team1` | Object | 첫 번째 팀 정보 |
| `team1.isHome` | Boolean | team1이 홈팀 여부 |
| `team2` | Object | 두 번째 팀 정보 |
| `gameTime` | String (ISO8601) | 경기 시작 시각 |
| `venue` | String | 경기장 |
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
    "gameTime": "2025-04-21T14:00:00Z",
    "venue": "광주-기아 챔피언스 필드",
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
| `teamSide` | String | N | 응원 구역 (`TEAM1` \| `TEAM2` \| `NEUTRAL`) |
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
        "teamSide": "TEAM1",
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
| `teamSide` | String | 응원 구역 (`TEAM1` \| `TEAM2` \| `NEUTRAL`) |
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

| method | path | auth required | 설명 |
| --- | --- | --- | --- |
| GET | /api/chat/rooms | N | 채팅방 목록 조회 |
| GET | /api/chat/rooms/team/{teamName} | N | 팀별 채팅방 조회 |
| WS | /ws/chat (STOMP) | O | WebSocket 연결 / 메시지 전송 |
| GET | /api/chat/intensity/{gameId} | N | 응원 열기 지수 조회 |
| GET | /api/chat/history/{roomId} | O | 채팅 이력 조회 |

---

### 5-1. 채팅방 목록 조회

```
GET /api/chat/rooms
```

#### Query Parameter

| 파라미터 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `type` | String | N | 채팅방 유형 (`GAME` \| `TEAM` \| `GENERAL`) |
| `cursor` | String | N | 페이지네이션 커서 |
| `limit` | Integer | N | 페이지 크기 (기본 20) |

#### Response Body

```json
{
  "success": true,
  "data": {
    "items": [
      {
        "roomId": 201,
        "type": "GAME",
        "gameId": 101,
        "teamId": null,
        "title": "KIA vs 삼성 경기 채팅",
        "currentParticipants": 432,
        "maxParticipants": 5000,
        "createdAt": "2025-04-21T09:00:00Z"
      }
    ],
    "nextCursor": "eyJpZCI6MjAxfQ==",
    "hasNext": false,
    "totalCount": 5
  }
}
```

#### Response Field

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `roomId` | Long | 채팅방 ID |
| `type` | String | 채팅방 유형 (`GAME` \| `TEAM` \| `GENERAL`) |
| `gameId` | Long \| null | 연결된 경기 ID (GAME 타입일 때) |
| `teamId` | Long \| null | 연결된 팀 ID (TEAM 타입일 때) |
| `title` | String | 채팅방 표시 이름 |
| `currentParticipants` | Integer | 현재 참여 인원 |
| `maxParticipants` | Integer | 최대 참여 인원 |

---

### 5-2. 팀별 채팅방 조회

```
GET /api/chat/rooms/team/{teamName}
```

#### Path Variable

| 변수 | 타입 | 설명 |
| --- | --- | --- |
| `teamName` | String | 팀 short_name 또는 정식 명칭 |

#### Response Body

```json
{
  "success": true,
  "data": {
    "roomId": 202,
    "type": "TEAM",
    "teamId": 1,
    "teamName": "KIA 타이거즈",
    "currentParticipants": 1205,
    "maxParticipants": 10000
  }
}
```

#### Http Status / Error Code

| 에러 코드 | HTTP Status | 설명 |
| --- | --- | --- |
| `CHAT_ROOM_NOT_FOUND` | 404 | 해당 팀 채팅방 없음 |

---

### 5-3. WebSocket 연결 / 메시지 전송 (STOMP)

```
WS /ws/chat
```

STOMP 프로토콜. HTTP 핸드쉐이크 단계에서 JWT 검증 (`ChannelInterceptor`).

#### STOMP CONNECT 헤더

| 헤더 | 설명 |
| --- | --- |
| `Authorization` | `Bearer {accessToken}` |

#### 구독 채널 (SUBSCRIBE)

| destination | 설명 |
| --- | --- |
| `/topic/chat/rooms/{roomId}` | 채팅방 메시지 수신 |
| `/topic/queue/{gameId}` | 대기열 순번 도래 시 서버 Push (예매 진입 신호) |
| `/user/queue/errors` | 개인 에러 수신 |

#### 대기열 Push 수신 구조 (`/topic/queue/{gameId}`)

```json
{
  "gameId": 101,
  "memberId": 1,
  "message": "이제 예매하실 수 있습니다.",
  "expireAt": "2025-04-21T10:15:00Z"
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `gameId` | Long | 경기 ID |
| `memberId` | Long | 순번 도달한 회원 ID |
| `message` | String | 안내 메시지 |
| `expireAt` | String (ISO8601) | 예매 진입 유효 시간 (초과 시 대기열 재진입 필요) |

#### 메시지 전송 (SEND)

**destination**: `/app/chat/rooms/{roomId}/send`

```json
{
  "content": "오늘도 파이팅!",
  "type": "MESSAGE"
}
```

#### SEND Field

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `content` | String | Y | 메시지 내용 (최대 80자) |
| `type` | String | Y | `MESSAGE` \| `CHEER` |

#### 수신 메시지 구조

```json
{
  "messageId": 30001,
  "roomId": 201,
  "senderId": 1,
  "senderNickname": "응원왕",
  "content": "오늘도 파이팅!",
  "type": "MESSAGE",
  "isFiltered": false,
  "createdAt": "2025-04-21T14:00:01Z"
}
```

#### 수신 Field

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `messageId` | Long | 메시지 ID |
| `senderId` | Long | 발신자 회원 ID |
| `senderNickname` | String | 발신자 닉네임 |
| `type` | String | `MESSAGE` \| `CHEER` \| `SYSTEM` |
| `isFiltered` | Boolean | 욕설 필터링 여부 |

---

### 5-4. 응원 열기 지수 조회

```
GET /api/chat/intensity/{gameId}
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
    "team1Score": 8421,
    "team2Score": 3150,
    "totalMessages": 11571,
    "updatedAt": "2025-04-21T14:05:00Z"
  }
}
```

#### Response Field

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `team1Score` | Long | team1 응원 열기 점수 (Redis Sorted Set score 기반) |
| `team2Score` | Long | team2 응원 열기 점수 |
| `totalMessages` | Long | 총 메시지 수 |
| `updatedAt` | String (ISO8601) | 마지막 갱신 시각 |

---

### 5-5. 채팅 이력 조회

```
GET /api/chat/history/{roomId}
```

#### Path Variable

| 변수 | 타입 | 설명 |
| --- | --- | --- |
| `roomId` | Long | 채팅방 ID |

#### Query Parameter

| 파라미터 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `cursor` | String | N | 이전 메시지 기준 커서 (messageId 인코딩) |
| `limit` | Integer | N | 페이지 크기 (기본 50, 최대 100) |

#### Response Body

```json
{
  "success": true,
  "data": {
    "items": [
      {
        "messageId": 30001,
        "senderId": 1,
        "senderNickname": "응원왕",
        "content": "오늘도 파이팅!",
        "type": "MESSAGE",
        "isFiltered": false,
        "createdAt": "2025-04-21T14:00:01Z"
      }
    ],
    "nextCursor": "eyJtZXNzYWdlSWQiOjMwMDAwfQ==",
    "hasNext": true
  }
}
```

#### Http Status / Error Code

| 에러 코드 | HTTP Status | 설명 |
| --- | --- | --- |
| `CHAT_ROOM_NOT_FOUND` | 404 | 존재하지 않는 채팅방 |

---

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
