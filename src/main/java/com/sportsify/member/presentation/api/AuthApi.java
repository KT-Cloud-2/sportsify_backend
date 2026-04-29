package com.sportsify.member.presentation.api;

import com.sportsify.common.response.CommonResponse;
import com.sportsify.common.swagger.AuthRequiredApi;
import com.sportsify.common.swagger.CommonApiResponses;
import com.sportsify.common.swagger.SwaggerApiResponse;
import com.sportsify.member.presentation.dto.LogoutRequest;
import com.sportsify.member.presentation.dto.TokenRefreshRequest;
import com.sportsify.member.presentation.dto.TokenRefreshResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@Tag(name = "Auth", description = "인증 API (토큰 갱신, 로그아웃)")
public interface AuthApi {

    @Operation(summary = "액세스 토큰 갱신", description = """
            Refresh Token으로 새 Access Token / Refresh Token 쌍을 발급합니다. (RFC 6749 §5.1)

            **클라이언트 처리 방법**
            응답 JSON의 `data.accessToken`을 이후 모든 인증 요청 헤더에 포함합니다.
            ```
            Authorization: Bearer {accessToken}
            ```
            기존 Refresh Token은 즉시 폐기하고 응답의 `data.refreshToken`으로 교체합니다.""")
    @CommonApiResponses
    @SwaggerApiResponse.InvalidRefreshToken
    @ApiResponses(@ApiResponse(responseCode = "200", description = "토큰 갱신 성공",
            content = @Content(schema = @Schema(implementation = TokenRefreshResponse.class))))
    ResponseEntity<CommonResponse<TokenRefreshResponse>> refresh(
            @Valid @RequestBody TokenRefreshRequest request
    );

    @Operation(summary = "로그아웃", description = "Refresh Token을 무효화하고 Access Token을 블랙리스트에 등록합니다.")
    @AuthRequiredApi
    @CommonApiResponses
    @SwaggerApiResponse.NoContent
    ResponseEntity<Void> logout(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @Valid @RequestBody LogoutRequest request
    );
}
