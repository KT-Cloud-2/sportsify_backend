package com.sportsify.member.presentation.controller;

import com.sportsify.common.response.CommonResponse;
import com.sportsify.member.application.service.AuthService;
import com.sportsify.member.presentation.api.AuthApi;
import com.sportsify.member.presentation.dto.LogoutRequest;
import com.sportsify.member.presentation.dto.TokenRefreshRequest;
import com.sportsify.member.presentation.dto.TokenRefreshResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController implements AuthApi {

    private final AuthService authService;

    @PostMapping("/token/refresh")
    public ResponseEntity<CommonResponse<TokenRefreshResponse>> refresh(
            @Valid @RequestBody TokenRefreshRequest request
    ) {
        TokenRefreshResponse response = TokenRefreshResponse.from(
                authService.refresh(request.refreshToken())
        );
        return ResponseEntity.ok(CommonResponse.ok(response));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @Valid @RequestBody LogoutRequest request
    ) {
        String accessToken = extractBearerToken(authorizationHeader);
        authService.logout(accessToken, request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    private String extractBearerToken(String authorizationHeader) {
        if (StringUtils.hasText(authorizationHeader) && authorizationHeader.startsWith("Bearer ")) {
            return authorizationHeader.substring(7);
        }
        return null;
    }
}
