package com.sportsify.member.presentation.api;

import com.sportsify.common.exception.ErrorCode;
import com.sportsify.common.swagger.AuthRequiredApi;
import com.sportsify.common.swagger.CommonApiResponses;
import com.sportsify.common.swagger.SwaggerApi;
import com.sportsify.common.swagger.SwaggerApiError;
import com.sportsify.member.presentation.dto.LogoutRequest;
import com.sportsify.member.presentation.dto.TokenRefreshRequest;
import com.sportsify.member.presentation.dto.TokenRefreshResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@Tag(name = "인증", description = "인증 API (토큰 갱신, 로그아웃)")
public interface AuthApi {

    @SwaggerApi(
            summary = "액세스 토큰 갱신",
            description = """
                    Refresh Token으로 새 Access Token / Refresh Token 쌍을 발급합니다. (RFC 6749 §5.1)

                    **일반 API 인증**
                    응답 JSON의 `data.accessToken`을 이후 모든 인증 요청 헤더에 포함합니다.
                    ```
                    Authorization: Bearer {accessToken}
                    ```

                    **SSE 연결 인증**
                    브라우저 `EventSource`는 커스텀 헤더를 지원하지 않으므로 쿼리 파라미터로 전달합니다. (WHATWG HTML Living Standard)
                    ```
                    GET /api/notifications/stream?token={accessToken}
                    ```

                    기존 Refresh Token은 즉시 폐기하고 응답의 `data.refreshToken`으로 교체합니다."""
    )
    @CommonApiResponses
    @SwaggerApiError(ErrorCode.INVALID_REFRESH_TOKEN)
    ResponseEntity<TokenRefreshResponse> refresh(
            @RequestBody TokenRefreshRequest request
    );

    @SwaggerApi(summary = "로그아웃", description = "Refresh Token을 무효화하고 Access Token을 블랙리스트에 등록합니다.", responseCode = "204", responseDescription = "성공 (본문 없음)")
    @AuthRequiredApi
    @CommonApiResponses
    ResponseEntity<Void> logout(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestBody LogoutRequest request
    );
}
