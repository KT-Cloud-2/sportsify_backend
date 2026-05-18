package com.sportsify.member.application;

import com.sportsify.common.exception.BusinessException;
import com.sportsify.common.exception.ErrorCode;
import com.sportsify.infrastructure.security.JwtProvider;
import com.sportsify.member.application.dto.TokenPairResult;
import com.sportsify.member.application.service.AuthService;
import com.sportsify.member.domain.model.Member;
import com.sportsify.member.domain.model.MemberRole;
import com.sportsify.member.domain.model.OAuthProvider;
import com.sportsify.member.domain.repository.MemberRepository;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @InjectMocks
    private AuthService authService;

    @Mock
    private JwtProvider jwtProvider;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private ValueOperations<String, String> valueOps;

    // ──────────────────────── refresh ────────────────────────

    @Test
    @DisplayName("유효한 Refresh Token으로 새 토큰 쌍을 발급한다")
    void refresh_성공() {
        Claims claims = refreshClaims("valid-refresh", 1L);
        Member member = Member.create("test@test.com", "테스터", OAuthProvider.GOOGLE, "g-1");
        given(redisTemplate.opsForValue()).willReturn(valueOps);
        given(jwtProvider.parse("valid-refresh")).willReturn(claims);
        given(valueOps.get("auth:refresh:1")).willReturn("valid-refresh");
        given(memberRepository.findById(1L)).willReturn(java.util.Optional.of(member));
        given(jwtProvider.createAccessToken(1L, MemberRole.USER.name())).willReturn("new-access");
        given(jwtProvider.createRefreshToken(1L)).willReturn("new-refresh");
        given(jwtProvider.getRefreshTokenExpiryMs()).willReturn(1_209_600_000L);

        TokenPairResult result = authService.refresh("valid-refresh");

        assertThat(result.accessToken()).isEqualTo("new-access");
        assertThat(result.refreshToken()).isEqualTo("new-refresh");
    }

    @Test
    @DisplayName("Redis에 저장된 토큰과 다르면 INVALID_REFRESH_TOKEN 예외가 발생한다")
    void refresh_토큰불일치_예외() {
        Claims claims = refreshClaims("stale-refresh", 1L);
        given(redisTemplate.opsForValue()).willReturn(valueOps);
        given(jwtProvider.parse("stale-refresh")).willReturn(claims);
        given(valueOps.get("auth:refresh:1")).willReturn("different-refresh");

        assertThatThrownBy(() -> authService.refresh("stale-refresh"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);
    }

    @Test
    @DisplayName("type이 refresh가 아닌 토큰은 INVALID_REFRESH_TOKEN 예외가 발생한다")
    void refresh_잘못된타입_예외() {
        Claims claims = claimsWithType("access");
        given(jwtProvider.parse("wrong-type")).willReturn(claims);

        assertThatThrownBy(() -> authService.refresh("wrong-type"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);
    }

    // ──────────────────────── logout ────────────────────────

    @Test
    @DisplayName("로그아웃 시 Redis refresh key를 삭제하고 access token을 블랙리스트에 등록한다")
    void logout_성공() {
        // given(A).willReturn(helperThatContainsGiven()) 패턴은 willReturn 인자 평가 중
        // 내부 given()이 실행되어 UnfinishedStubbingException 발생 → 먼저 인스턴스를 만들고 스텁을 완성한다
        Claims accessClaims = stubAccessClaims(900_000L);
        Claims refreshClaims = claimsWithType("refresh");

        given(jwtProvider.parse("valid-refresh")).willReturn(refreshClaims);
        given(jwtProvider.getMemberId("valid-refresh")).willReturn(1L);
        given(jwtProvider.isValid("valid-access")).willReturn(true);
        given(jwtProvider.parse("valid-access")).willReturn(accessClaims);
        given(redisTemplate.opsForValue()).willReturn(valueOps);

        authService.logout("valid-access", "valid-refresh");

        verify(redisTemplate).delete("auth:refresh:1");
        verify(valueOps).set(eq("auth:blacklist:valid-access"), eq("1"), anyLong(), any());
    }

    // ──────────────────────── 픽스처 헬퍼 ────────────────────────

    private Claims refreshClaims(String token, Long memberId) {
        Claims claims = claimsWithType("refresh");
        given(jwtProvider.getMemberId(token)).willReturn(memberId);
        return claims;
    }

    private Claims claimsWithType(String type) {
        Claims claims = org.mockito.Mockito.mock(Claims.class);
        given(claims.get("type", String.class)).willReturn(type);
        return claims;
    }

    // given() 밖에서 먼저 호출해 mock 인스턴스와 스텁을 완성한 뒤 반환
    private Claims stubAccessClaims(long msFromNow) {
        Date expiration = new Date(System.currentTimeMillis() + msFromNow);
        Claims claims = org.mockito.Mockito.mock(Claims.class);
        given(claims.getExpiration()).willReturn(expiration);
        return claims;
    }
}
