package com.sportsify.infrastructure.security;

import com.sportsify.common.response.ApiResponse;
import com.sportsify.common.response.ErrorDetail;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationEntryPointTest {

    @Mock
    private JsonMapper jsonMapper;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private AuthenticationException authException;

    private JwtAuthenticationEntryPoint entryPoint;
    private StringWriter responseWriter;
    private PrintWriter printWriter;

    @BeforeEach
    void setUp() throws IOException {
        entryPoint = new JwtAuthenticationEntryPoint(jsonMapper);
        responseWriter = new StringWriter();
        printWriter = new PrintWriter(responseWriter);
        given(response.getWriter()).willReturn(printWriter);
    }

    // ──────────────────────── HTTP 응답 설정 ────────────────────────

    @Test
    @DisplayName("인증 실패 시 HTTP 상태를 401로 설정한다")
    void commence_401상태설정() throws IOException {
        given(jsonMapper.writeValueAsString(any())).willReturn("{}");

        entryPoint.commence(request, response, authException);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    @DisplayName("인증 실패 시 Content-Type을 application/json으로 설정한다")
    void commence_ContentType설정() throws IOException {
        given(jsonMapper.writeValueAsString(any())).willReturn("{}");

        entryPoint.commence(request, response, authException);

        verify(response).setContentType(MediaType.APPLICATION_JSON_VALUE);
    }

    @Test
    @DisplayName("인증 실패 시 인코딩을 UTF-8로 설정한다")
    void commence_인코딩UTF8설정() throws IOException {
        given(jsonMapper.writeValueAsString(any())).willReturn("{}");

        entryPoint.commence(request, response, authException);

        verify(response).setCharacterEncoding("UTF-8");
    }

    // ──────────────────────── ApiResponse 내용 ────────────────────────

    @Test
    @DisplayName("ApiResponse.error()로 생성된 객체가 직렬화에 사용된다")
    void commence_ApiResponse_error사용() throws IOException {
        given(jsonMapper.writeValueAsString(any())).willReturn("{}");
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);

        entryPoint.commence(request, response, authException);

        verify(jsonMapper).writeValueAsString(captor.capture());
        Object captured = captor.getValue();
        assertThat(captured).isInstanceOf(ApiResponse.class);

        @SuppressWarnings("unchecked")
        ApiResponse<Void> apiResponse = (ApiResponse<Void>) captured;
        assertThat(apiResponse.success()).isFalse();
        assertThat(apiResponse.data()).isNull();
    }

    @Test
    @DisplayName("직렬화된 객체의 error.code가 UNAUTHORIZED이다")
    void commence_에러코드UNAUTHORIZED() throws IOException {
        given(jsonMapper.writeValueAsString(any())).willReturn("{}");
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);

        entryPoint.commence(request, response, authException);

        verify(jsonMapper).writeValueAsString(captor.capture());
        @SuppressWarnings("unchecked")
        ApiResponse<Void> apiResponse = (ApiResponse<Void>) captor.getValue();
        ErrorDetail error = apiResponse.error();
        assertThat(error).isNotNull();
        assertThat(error.code()).isEqualTo("UNAUTHORIZED");
    }

    @Test
    @DisplayName("직렬화된 객체의 error.message가 '인증이 필요합니다.'이다")
    void commence_에러메시지설정() throws IOException {
        given(jsonMapper.writeValueAsString(any())).willReturn("{}");
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);

        entryPoint.commence(request, response, authException);

        verify(jsonMapper).writeValueAsString(captor.capture());
        @SuppressWarnings("unchecked")
        ApiResponse<Void> apiResponse = (ApiResponse<Void>) captor.getValue();
        assertThat(apiResponse.error().message()).isEqualTo("인증이 필요합니다.");
    }

    @Test
    @DisplayName("직렬화된 객체의 error.detail이 null이다")
    void commence_에러detail이null() throws IOException {
        given(jsonMapper.writeValueAsString(any())).willReturn("{}");
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);

        entryPoint.commence(request, response, authException);

        verify(jsonMapper).writeValueAsString(captor.capture());
        @SuppressWarnings("unchecked")
        ApiResponse<Void> apiResponse = (ApiResponse<Void>) captor.getValue();
        assertThat(apiResponse.error().detail()).isNull();
    }

    @Test
    @DisplayName("직렬화된 JSON 문자열이 응답 writer에 쓰인다")
    void commence_JSON이writer에기록됨() throws IOException {
        String expectedJson = "{\"success\":false}";
        given(jsonMapper.writeValueAsString(any())).willReturn(expectedJson);

        entryPoint.commence(request, response, authException);

        printWriter.flush();
        assertThat(responseWriter.toString()).isEqualTo(expectedJson);
    }

    @Test
    @DisplayName("직렬화된 객체의 timestamp가 null이 아니다")
    void commence_타임스탬프설정() throws IOException {
        given(jsonMapper.writeValueAsString(any())).willReturn("{}");
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);

        entryPoint.commence(request, response, authException);

        verify(jsonMapper).writeValueAsString(captor.capture());
        @SuppressWarnings("unchecked")
        ApiResponse<Void> apiResponse = (ApiResponse<Void>) captor.getValue();
        assertThat(apiResponse.timestamp()).isNotNull();
    }
}
