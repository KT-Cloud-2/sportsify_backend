package com.sportsify.member.presentation;

import com.epages.restdocs.apispec.Schema;
import com.sportsify.common.exception.BusinessException;
import com.sportsify.common.exception.ErrorCode;
import com.sportsify.member.application.dto.TokenPairResult;
import com.sportsify.member.application.service.AuthService;
import com.sportsify.member.presentation.dto.LogoutRequest;
import com.sportsify.member.presentation.dto.TokenRefreshRequest;
import com.sportsify.support.ApiTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document;
import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.resourceDetails;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.restdocs.payload.PayloadDocumentation.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerApiTest extends ApiTestSupport {

    @MockitoBean
    private AuthService authService;

    private static org.springframework.restdocs.payload.FieldDescriptor[] errorResponseFields() {
        return new org.springframework.restdocs.payload.FieldDescriptor[]{
                fieldWithPath("success").type(JsonFieldType.BOOLEAN).description("성공 여부 (`false`)"),
                fieldWithPath("data").type(JsonFieldType.NULL).description("데이터 없음"),
                fieldWithPath("error.code").type(JsonFieldType.STRING).description("에러 코드"),
                fieldWithPath("error.message").type(JsonFieldType.STRING).description("에러 메시지"),
                fieldWithPath("error.detail").type(JsonFieldType.STRING).description("상세 정보").optional(),
                fieldWithPath("timestamp").type(JsonFieldType.STRING).description("응답 시각")
        };
    }

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
                .andExpect(jsonPath("$.data.accessToken").value("new-access-token"))
                .andDo(document("토큰-갱신",
                        resourceDetails()
                                .tag("Auth")
                                .summary("액세스 토큰 갱신")
                                .description("""
                                        Refresh Token으로 새 Access Token / Refresh Token 쌍을 발급합니다. (RFC 6749 §5.1)

                                        **클라이언트 처리 방법**
                                        응답 JSON의 `data.accessToken`을 이후 모든 인증 요청의 헤더에 포함합니다.
                                        ```
                                        Authorization: Bearer {accessToken}
                                        ```
                                        기존 Refresh Token은 즉시 폐기하고 응답의 `data.refreshToken`으로 교체합니다.""")
                                .requestSchema(Schema.schema("TokenRefreshRequest"))
                                .responseSchema(Schema.schema("TokenRefreshResponse")),
                        requestFields(
                                fieldWithPath("refreshToken").type(JsonFieldType.STRING).description("유효한 리프레시 토큰")
                        ),
                        responseFields(
                                fieldWithPath("success").type(JsonFieldType.BOOLEAN).description("성공 여부"),
                                fieldWithPath("data.accessToken").type(JsonFieldType.STRING).description("새 액세스 토큰 (30분 만료) — 이후 요청의 `Authorization: Bearer` 헤더에 사용"),
                                fieldWithPath("data.refreshToken").type(JsonFieldType.STRING).description("새 리프레시 토큰 (30일 만료) — 기존 토큰 즉시 폐기 후 교체"),
                                fieldWithPath("error").type(JsonFieldType.NULL).description("에러 정보"),
                                fieldWithPath("timestamp").type(JsonFieldType.STRING).description("응답 시각")
                        )
                ));
    }

    @Test
    @DisplayName("POST /api/auth/token/refresh — 400 refreshToken 누락")
    void 토큰_갱신_파라미터누락() throws Exception {
        mockMvc.perform(post("/api/auth/token/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
                .andDo(document("토큰-갱신-400",
                        resourceDetails().tag("Auth"),
                        responseFields(errorResponseFields())
                ));
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
                .andExpect(jsonPath("$.error.code").value("INVALID_REFRESH_TOKEN"))
                .andDo(document("토큰-갱신-401",
                        resourceDetails().tag("Auth"),
                        responseFields(errorResponseFields())
                ));
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
                .andExpect(status().isNoContent())
                .andDo(document("로그아웃",
                        resourceDetails()
                                .tag("Auth")
                                .summary("로그아웃")
                                .description("Refresh Token을 무효화하고 Access Token을 블랙리스트에 등록합니다."),
                        requestFields(
                                fieldWithPath("refreshToken").type(JsonFieldType.STRING).description("무효화할 리프레시 토큰")
                        )
                ));
    }

    @Test
    @DisplayName("POST /api/auth/logout — 400 refreshToken 누락")
    void 로그아웃_파라미터누락() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
                .andDo(document("로그아웃-400",
                        resourceDetails().tag("Auth"),
                        responseFields(errorResponseFields())
                ));
    }
}
