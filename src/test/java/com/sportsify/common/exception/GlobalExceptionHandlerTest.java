package com.sportsify.common.exception;

import com.sportsify.common.response.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    // ──────────────────────── handleBusinessException ────────────────────────

    @Test
    @DisplayName("BusinessException — 에러 코드 HTTP 상태로 ApiResponse<Void> 반환")
    void handleBusinessException_ApiResponse반환() {
        BusinessException ex = new BusinessException(ErrorCode.MEMBER_NOT_FOUND);

        ResponseEntity<ApiResponse<Void>> response = handler.handleBusinessException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isFalse();
        assertThat(response.getBody().data()).isNull();
        assertThat(response.getBody().error()).isNotNull();
    }

    @Test
    @DisplayName("BusinessException — error.code가 ErrorCode의 code와 일치한다")
    void handleBusinessException_에러코드일치() {
        BusinessException ex = new BusinessException(ErrorCode.MEMBER_NOT_FOUND);

        ResponseEntity<ApiResponse<Void>> response = handler.handleBusinessException(ex);

        assertThat(response.getBody().error().code()).isEqualTo("MEMBER_NOT_FOUND");
        assertThat(response.getBody().error().message()).isEqualTo("존재하지 않는 회원입니다.");
    }

    @Test
    @DisplayName("BusinessException — detail이 없으면 error.detail이 null")
    void handleBusinessException_detail없음() {
        BusinessException ex = new BusinessException(ErrorCode.MEMBER_NOT_FOUND);

        ResponseEntity<ApiResponse<Void>> response = handler.handleBusinessException(ex);

        assertThat(response.getBody().error().detail()).isNull();
    }

    @Test
    @DisplayName("BusinessException — detail이 있으면 error.detail에 포함된다")
    void handleBusinessException_detail포함() {
        BusinessException ex = new BusinessException(ErrorCode.INVALID_INPUT, "nickname: 유효하지 않은 값");

        ResponseEntity<ApiResponse<Void>> response = handler.handleBusinessException(ex);

        assertThat(response.getBody().error().detail()).isEqualTo("nickname: 유효하지 않은 값");
    }

    @Test
    @DisplayName("BusinessException(UNAUTHORIZED) — 401 상태 코드 반환")
    void handleBusinessException_401반환() {
        BusinessException ex = new BusinessException(ErrorCode.UNAUTHORIZED);

        ResponseEntity<ApiResponse<Void>> response = handler.handleBusinessException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("BusinessException(CONFLICT) — 409 상태 코드 반환")
    void handleBusinessException_409반환() {
        BusinessException ex = new BusinessException(ErrorCode.NICKNAME_DUPLICATE);

        ResponseEntity<ApiResponse<Void>> response = handler.handleBusinessException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().error().code()).isEqualTo("NICKNAME_DUPLICATE");
    }

    @Test
    @DisplayName("BusinessException — timestamp가 null이 아니다")
    void handleBusinessException_타임스탬프설정() {
        BusinessException ex = new BusinessException(ErrorCode.MEMBER_NOT_FOUND);

        ResponseEntity<ApiResponse<Void>> response = handler.handleBusinessException(ex);

        assertThat(response.getBody().timestamp()).isNotNull();
    }

    // ──────────────────────── handleValidationException ────────────────────────

    @Test
    @DisplayName("MethodArgumentNotValidException — 400 상태 코드 반환")
    void handleValidationException_400반환() {
        MethodArgumentNotValidException ex = mockValidationException("nickname", "2자 이상 입력해주세요.");

        ResponseEntity<ApiResponse<Void>> response = handler.handleValidationException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("MethodArgumentNotValidException — error.code가 INVALID_INPUT")
    void handleValidationException_코드INVALID_INPUT() {
        MethodArgumentNotValidException ex = mockValidationException("nickname", "2자 이상 입력해주세요.");

        ResponseEntity<ApiResponse<Void>> response = handler.handleValidationException(ex);

        assertThat(response.getBody().success()).isFalse();
        assertThat(response.getBody().error().code()).isEqualTo("INVALID_INPUT");
        assertThat(response.getBody().error().message()).isEqualTo("입력값이 올바르지 않습니다.");
    }

    @Test
    @DisplayName("MethodArgumentNotValidException — detail에 필드명과 메시지가 포함된다")
    void handleValidationException_detail필드포함() {
        MethodArgumentNotValidException ex = mockValidationException("nickname", "2자 이상 입력해주세요.");

        ResponseEntity<ApiResponse<Void>> response = handler.handleValidationException(ex);

        assertThat(response.getBody().error().detail()).isEqualTo("nickname: 2자 이상 입력해주세요.");
    }

    @Test
    @DisplayName("MethodArgumentNotValidException — 필드 에러가 없으면 detail이 null")
    void handleValidationException_필드에러없음_detailNull() {
        BindingResult bindingResult = mock(BindingResult.class);
        given(bindingResult.getFieldErrors()).willReturn(List.of());

        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        given(ex.getBindingResult()).willReturn(bindingResult);

        ResponseEntity<ApiResponse<Void>> response = handler.handleValidationException(ex);

        assertThat(response.getBody().error().detail()).isNull();
    }

    @Test
    @DisplayName("MethodArgumentNotValidException — 여러 필드 에러 중 첫 번째만 포함된다")
    void handleValidationException_첫번째필드만포함() {
        BindingResult bindingResult = mock(BindingResult.class);
        FieldError firstError = new FieldError("obj", "email", "유효한 이메일 형식이어야 합니다.");
        FieldError secondError = new FieldError("obj", "nickname", "2자 이상 입력해주세요.");
        given(bindingResult.getFieldErrors()).willReturn(List.of(firstError, secondError));

        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        given(ex.getBindingResult()).willReturn(bindingResult);

        ResponseEntity<ApiResponse<Void>> response = handler.handleValidationException(ex);

        assertThat(response.getBody().error().detail()).isEqualTo("email: 유효한 이메일 형식이어야 합니다.");
    }

    // ──────────────────────── handleException ────────────────────────

    @Test
    @DisplayName("Exception — 500 상태 코드 반환")
    void handleException_500반환() {
        Exception ex = new RuntimeException("예상치 못한 오류");

        ResponseEntity<ApiResponse<Void>> response = handler.handleException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    @DisplayName("Exception — error.code가 INTERNAL_ERROR")
    void handleException_코드INTERNAL_ERROR() {
        Exception ex = new RuntimeException("예상치 못한 오류");

        ResponseEntity<ApiResponse<Void>> response = handler.handleException(ex);

        assertThat(response.getBody().success()).isFalse();
        assertThat(response.getBody().error().code()).isEqualTo("INTERNAL_ERROR");
        assertThat(response.getBody().error().message()).isEqualTo("서버 내부 오류가 발생했습니다.");
    }

    @Test
    @DisplayName("Exception — error.detail이 null")
    void handleException_detail이null() {
        Exception ex = new IllegalStateException("상태 오류");

        ResponseEntity<ApiResponse<Void>> response = handler.handleException(ex);

        assertThat(response.getBody().error().detail()).isNull();
    }

    @Test
    @DisplayName("Exception — timestamp가 null이 아니다")
    void handleException_타임스탬프설정() {
        Exception ex = new RuntimeException();

        ResponseEntity<ApiResponse<Void>> response = handler.handleException(ex);

        assertThat(response.getBody().timestamp()).isNotNull();
    }

    @Test
    @DisplayName("Exception — data가 null이다")
    void handleException_data가null() {
        Exception ex = new NullPointerException("NPE 발생");

        ResponseEntity<ApiResponse<Void>> response = handler.handleException(ex);

        assertThat(response.getBody().data()).isNull();
    }

    // ──────────────────────── 헬퍼 ────────────────────────

    private MethodArgumentNotValidException mockValidationException(String field, String message) {
        BindingResult bindingResult = mock(BindingResult.class);
        FieldError fieldError = new FieldError("obj", field, message);
        given(bindingResult.getFieldErrors()).willReturn(List.of(fieldError));

        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        given(ex.getBindingResult()).willReturn(bindingResult);
        return ex;
    }
}