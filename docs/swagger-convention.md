# Swagger 컨벤션 가이드

## 핵심 원칙

- **Swagger 어노테이션은 `*Api` 인터페이스에만** — Controller 구현체는 `implements`만
- **에러는 `@SwaggerApi(error/errors = ...)` 인라인으로** — JSON 예시 자동 생성
- **`@RequestBody` 파라미터가 있으면 `400 INVALID_INPUT` 자동 추가** — 중복 선언 불필요
- **`@AuthenticationPrincipal` 파라미터는 Swagger에서 자동 숨김** — springdoc-openapi-security 내장

---

## 어노테이션 레퍼런스

### 클래스 레벨 (인터페이스에)

| 어노테이션 | 효과 |
|-----------|------|
| `@CommonApiResponses` | 공통 응답 마커 |
| `@AuthRequiredApi` | `bearerAuth` 보안 요구 + `401 UNAUTHORIZED` — **클래스·메서드 둘 다 사용 가능** |

```java
// 인터페이스 전체가 인증 필요 → 클래스 레벨
@AuthRequiredApi
public interface MemberApi { ... }

// 일부 메서드만 인증 필요 → 메서드 레벨
public interface AuthApi {
    ResponseEntity<TokenRefreshResponse> refresh(...);  // 인증 불필요

    @AuthRequiredApi                                    // 이 메서드만 인증 필요
    ResponseEntity<Void> logout(...);
}
```

### 메서드 레벨

| 어노테이션 | 효과 |
|-----------|------|
| `@SwaggerApi(summary = "...")` | Operation 요약 + 200 응답 |
| `@SwaggerApi(..., responseCode = "204")` | 204 응답 (본문 없음) |
| `@SwaggerApi(..., error = ErrorCode.XXX)` | 단일 에러 인라인 선언 (`INVALID_INPUT` 제외 — 자동 추가됨) |
| `@SwaggerApi(..., errors = {XXX, YYY})` | 복수 에러 인라인 선언 (`errors` 지정 시 `error` 무시) |
| `@SwaggerApiError(ErrorCode.XXX)` | 에러 별도 선언 (인라인과 병용 가능) |
| `@SwaggerApiError(errors = {XXX, YYY})` | 복수 에러 별도 선언 |

### 자동 처리

| 조건 | 자동 추가 |
|------|----------|
| 파라미터에 `@RequestBody` 있음 | `400 INVALID_INPUT` 응답 자동 추가 |
| 파라미터에 `@AuthenticationPrincipal` 있음 | Swagger UI 파라미터 목록에서 자동 숨김 |

---

## 새 API 추가 방법

### Step 1 — `*Api` 인터페이스 작성

```java
@Tag(name = "Notification", description = "알림 API")
@AuthRequiredApi
@CommonApiResponses
public interface NotificationApi {

    // 에러 없는 단순 조회
    @SwaggerApi(summary = "알림 목록 조회")
    ResponseEntity<List<NotificationResponse>> getNotifications(Long memberId);

    // 단일 에러 인라인
    @SwaggerApi(summary = "알림 읽음 처리", responseCode = "204", responseDescription = "성공 (본문 없음)",
            error = ErrorCode.NOTIFICATION_NOT_FOUND)
    ResponseEntity<Void> readNotification(Long memberId, @PathVariable Long notificationId);

    // 복수 에러 인라인
    @SwaggerApi(summary = "알림 전송",
            errors = {ErrorCode.NOTIFICATION_NOT_FOUND, ErrorCode.MEMBER_NOT_FOUND})
    ResponseEntity<NotificationResponse> sendNotification(
            Long memberId,
            @RequestBody SendNotificationRequest request  // @RequestBody → 400 자동 추가
    );
}
```

### Step 2 — Controller는 implements만

```java
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController implements NotificationApi {

    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<List<NotificationResponse>> getNotifications(
            @AuthenticationPrincipal Long memberId  // Swagger에서 자동 숨김
    ) {
        return ResponseEntity.ok(notificationService.getAll(memberId));
    }
}
```

### Step 3 — 새 에러는 ErrorCode enum 한 줄 추가

```java
NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "NOTIFICATION_NOT_FOUND", "존재하지 않는 알림입니다."),
```

→ `error = ErrorCode.NOTIFICATION_NOT_FOUND` 즉시 사용 가능. JSON 예시 자동 생성.

---

## 에러 응답 포맷

```json
{
  "code": "NOTIFICATION_NOT_FOUND",
  "message": "존재하지 않는 알림입니다.",
  "detail": null
}
```

---

## 자주 하는 실수

| ❌ 잘못된 방법 | ✅ 올바른 방법 |
|--------------|--------------|
| Controller에 `@Operation` 직접 추가 | `*Api` 인터페이스에만 |
| `@SwaggerApiError(INVALID_INPUT)` 직접 선언 | `@RequestBody` 있으면 자동 |
| `@Parameter(hidden = true)` 수동 추가 | `@AuthenticationPrincipal`이면 자동 숨김 |
| 인터페이스에 `@Valid` 추가 | `@Valid`는 Controller 구현체에만 |
| `@SwaggerApi` + `@SwaggerApiError` 중복 선언 | `@SwaggerApi(error/errors = ...)` 인라인으로 통합 |
