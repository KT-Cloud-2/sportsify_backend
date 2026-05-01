# Swagger 컨벤션 가이드

## 핵심 원칙

- **Swagger 어노테이션은 `*Api` 인터페이스에만** — Controller 구현체는 `implements`만
- **에러는 `@SwaggerApiError(ErrorCode.XXX)` 하나로** — JSON 예시 자동 생성
- **`@RequestBody` 파라미터가 있으면 `400 INVALID_INPUT` 자동 추가** — 중복 선언 불필요
- **`@AuthenticationPrincipal` 파라미터는 Swagger UI에서 자동 숨김**

---

## 어노테이션 레퍼런스

### 클래스 레벨 (인터페이스에)

| 어노테이션 | 효과 |
|-----------|------|
| `@CommonApiResponses` | 공통 응답 마커 (현재 커스터마이저 트리거용) |
| `@AuthRequiredApi` | `bearerAuth` 보안 요구 + `401 UNAUTHORIZED` |

### 메서드 레벨

| 어노테이션 | 효과 |
|-----------|------|
| `@SwaggerApi(summary = "...")` | Operation 요약 + 200 응답 등록 |
| `@SwaggerApi(..., responseCode = "204")` | 204 응답 (본문 없음) |
| `@SwaggerApiError(ErrorCode.XXX)` | 도메인 에러 응답 + JSON 예시 자동 생성 |
| `@SwaggerApiError(ErrorCode.XXX, detail = "...")` | detail 메시지 포함 |

### 자동 처리

| 조건 | 자동 추가 |
|------|----------|
| 파라미터에 `@RequestBody` 있음 | `400 INVALID_INPUT` 응답 자동 추가 |
| 파라미터에 `@AuthenticationPrincipal` 있음 | Swagger UI 파라미터 목록에서 숨김 |
| 전역 API 설명 | `500 INTERNAL_ERROR` 공통 에러 안내 |

---

## 새 API 추가 방법

### Step 1 — `*Api` 인터페이스 작성

```java
@Tag(name = "Notification", description = "알림 API")
@AuthRequiredApi          // 로그인 필요 → bearerAuth + 401 자동
@CommonApiResponses
public interface NotificationApi {

    @SwaggerApi(summary = "알림 목록 조회")
    ResponseEntity<CommonResponse<List<NotificationResponse>>> getNotifications(Long memberId);

    @SwaggerApi(summary = "알림 삭제", responseCode = "204", responseDescription = "삭제 성공")
    @SwaggerApiError(ErrorCode.NOTIFICATION_NOT_FOUND)
    ResponseEntity<Void> deleteNotification(
            Long memberId,
            @PathVariable Long notificationId
    );

    @SwaggerApi(summary = "알림 읽음 처리")
    @SwaggerApiError(ErrorCode.NOTIFICATION_NOT_FOUND)
    ResponseEntity<CommonResponse<NotificationResponse>> readNotification(
            Long memberId,
            @RequestBody NotificationReadRequest request  // → 400 자동 추가
    );
}
```

### Step 2 — Controller는 implements만  추가 

```java
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController implements NotificationApi {

    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<CommonResponse<List<NotificationResponse>>> getNotifications(
            @AuthenticationPrincipal Long memberId  
    ) { ... }

    @DeleteMapping("/{notificationId}")
    public ResponseEntity<Void> deleteNotification(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long notificationId
    ) { ... }

    @PatchMapping("/read")
    public ResponseEntity<CommonResponse<NotificationResponse>> readNotification(
            @AuthenticationPrincipal Long memberId,
            @Valid @RequestBody NotificationReadRequest request
    ) { ... }
}
```

### Step 3 — 새 에러는 ErrorCode enum 한 줄 추가

```java
// ErrorCode.java
NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "NOTIFICATION_NOT_FOUND", "존재하지 않는 알림입니다."),
```

→ `@SwaggerApiError(ErrorCode.NOTIFICATION_NOT_FOUND)` 즉시 사용 가능. JSON 예시 자동 생성.

---

## 실제 예시 — MemberApi 구조

```
@Tag(name = "Member")
@AuthRequiredApi          ← bearerAuth + 401
@CommonApiResponses

getMe()
  @SwaggerApi("내 정보 조회")
  @SwaggerApiError(MEMBER_NOT_FOUND)              ← 404

updateNickname()
  @SwaggerApi("닉네임 수정")
  @RequestBody → 400 자동
  @SwaggerApiError(NICKNAME_DUPLICATE)            ← 409

withdraw()
  @SwaggerApi("회원 탈퇴", responseCode = "204")
  @SwaggerApiError(MEMBER_NOT_FOUND)              ← 404

addFavoriteTeam()
  @SwaggerApi("선호 팀 추가")
  @RequestBody → 400 자동
  @SwaggerApiError(TEAM_NOT_FOUND)                ← 404
  @SwaggerApiError(FAVORITE_TEAM_ALREADY_EXISTS)  ← 409

updateFavoriteTeamPriority()
  @SwaggerApi("우선순위 수정")
  @RequestBody → 400 자동
  @SwaggerApiError(INVALID_PRIORITY)              ← 400 (비즈니스 규칙)
  @SwaggerApiError(FAVORITE_TEAM_NOT_FOUND)       ← 404

removeFavoriteTeam()
  @SwaggerApi("선호 팀 삭제", responseCode = "204")
  @SwaggerApiError(FAVORITE_TEAM_NOT_FOUND)       ← 404
```

---

## 자주 하는 실수

| ❌ 잘못된 방법 | ✅ 올바른 방법 |
|--------------|--------------|
| Controller에 `@Operation` 직접 추가 | `*Api` 인터페이스에만 |
| `@SwaggerApiError(INVALID_INPUT)` 직접 선언 | `@RequestBody` 있으면 자동 |
| `@Parameter(hidden = true)` 수동 추가 | `@AuthenticationPrincipal`이면 자동 숨김 |
| 인터페이스에 `@Valid` 추가 | `@Valid`는 Controller 구현체에만 |
| 인터페이스에 `@SwaggerApiResponse.NoContent` 사용 | `@SwaggerApi(responseCode = "204")` 사용 |
