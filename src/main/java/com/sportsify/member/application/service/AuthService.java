package com.sportsify.member.application.service;

import com.sportsify.common.exception.BusinessException;
import com.sportsify.common.exception.ErrorCode;
import com.sportsify.infrastructure.security.JwtProvider;
import com.sportsify.member.application.dto.TokenPairResult;
import com.sportsify.member.domain.repository.MemberRepository;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String REFRESH_KEY_PREFIX = "auth:refresh:";
    private static final String BLACKLIST_KEY_PREFIX = "auth:blacklist:";

    private final JwtProvider jwtProvider;
    private final StringRedisTemplate redisTemplate;
    private final MemberRepository memberRepository;

    public TokenPairResult refresh(String refreshToken) {
        Long memberId = parseMemberIdFromRefreshToken(refreshToken);
        validateStoredRefreshToken(memberId, refreshToken);

        String role = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND))
                .getRole()
                .name();

        String newAccessToken = jwtProvider.createAccessToken(memberId, role);
        String newRefreshToken = jwtProvider.createRefreshToken(memberId);

        redisTemplate.opsForValue().set(
                REFRESH_KEY_PREFIX + memberId,
                newRefreshToken,
                jwtProvider.getRefreshTokenExpiryMs(),
                TimeUnit.MILLISECONDS
        );

        return new TokenPairResult(newAccessToken, newRefreshToken);
    }

    public void logout(String accessToken, String refreshToken) {
        Long memberId = parseMemberIdFromRefreshToken(refreshToken);

        redisTemplate.delete(REFRESH_KEY_PREFIX + memberId);

        if (accessToken != null && jwtProvider.isValid(accessToken)) {
            long remainingMs = jwtProvider.parse(accessToken).getExpiration().getTime() - System.currentTimeMillis();
            if (remainingMs > 0) {
                redisTemplate.opsForValue().set(
                        BLACKLIST_KEY_PREFIX + accessToken,
                        "1",
                        remainingMs,
                        TimeUnit.MILLISECONDS
                );
            }
        }
    }

    private Long parseMemberIdFromRefreshToken(String refreshToken) {
        try {
            String type = jwtProvider.parse(refreshToken).get("type", String.class);
            if (!"refresh".equals(type)) {
                throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
            }
            return jwtProvider.getMemberId(refreshToken);
        } catch (JwtException | IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
    }

    private void validateStoredRefreshToken(Long memberId, String refreshToken) {
        String stored = redisTemplate.opsForValue().get(REFRESH_KEY_PREFIX + memberId);
        if (!refreshToken.equals(stored)) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
    }
}
