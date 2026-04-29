package com.sportsify.member.presentation;

import com.sportsify.common.exception.BusinessException;
import com.sportsify.common.exception.ErrorCode;
import com.sportsify.member.application.dto.TokenPairResult;
import com.sportsify.member.application.service.AuthService;
import com.sportsify.member.presentation.controller.AuthController;
import com.sportsify.member.presentation.dto.LogoutRequest;
import com.sportsify.member.presentation.dto.TokenRefreshRequest;
import com.sportsify.support.WebMvcTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
class AuthControllerApiTest extends WebMvcTestSupport {

    @MockitoBean
    private AuthService authService;

    // ──────────────────────── POST /api/auth/token/refresh ────────────────────────

    @Test
    @DisplayName("POST /api/auth/token/refresh — 200 토큰 갱신 성공")
    void 토큰_갱신_성공() throws Exception {
        given(authService.refresh("valid-refresh-token"))
                .willReturn(new TokenPairResult("new-access-token", "new-refresh-token"));

        mockMvc.perform(post("/api/auth/token/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TokenRefreshRequest("valid-refresh-token"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("new-access-token"));
    }

    @Test
    @DisplayName("POST /api/auth/token/refresh — 400 refreshToken 누락")
    void 토큰_갱신_파라미터누락() throws Exception {
        mockMvc.perform(post("/api/auth/token/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("POST /api/auth/token/refresh — 401 유효하지 않은 Refresh Token")
    void 토큰_갱신_유효하지않은토큰() throws Exception {
        given(authService.refresh("expired-or-invalid-token"))
                .willThrow(new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));

        mockMvc.perform(post("/api/auth/token/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TokenRefreshRequest("expired-or-invalid-token"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_REFRESH_TOKEN"));
    }

    // ──────────────────────── POST /api/auth/logout ────────────────────────

    @Test
    @DisplayName("POST /api/auth/logout — 204 로그아웃 성공")
    void 로그아웃_성공() throws Exception {
        doNothing().when(authService).logout(null, "valid-refresh-token");

        mockMvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LogoutRequest("valid-refresh-token")))
                        .header("Authorization", bearerToken(1L, "USER")))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("POST /api/auth/logout — 400 refreshToken 누락")
    void 로그아웃_파라미터누락() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }
}
