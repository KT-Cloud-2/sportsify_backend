package com.sportsify.member.domain;

import com.sportsify.member.domain.model.Member;
import com.sportsify.member.domain.model.MemberRole;
import com.sportsify.member.domain.model.MemberStatus;
import com.sportsify.member.domain.model.OAuthProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MemberTest {

    @Test
    @DisplayName("소셜 로그인 정보로 회원을 생성하면 ACTIVE/USER 상태로 초기화된다")
    void create_초기상태_검증() {
        Member member = Member.create("test@example.com", "닉네임", OAuthProvider.GOOGLE, "google-sub-123");

        assertThat(member.getEmail()).isEqualTo("test@example.com");
        assertThat(member.getNickname()).isEqualTo("닉네임");
        assertThat(member.getProvider()).isEqualTo(OAuthProvider.GOOGLE);
        assertThat(member.getProviderId()).isEqualTo("google-sub-123");
        assertThat(member.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(member.getRole()).isEqualTo(MemberRole.USER);
        assertThat(member.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("닉네임을 수정하면 새 닉네임과 updatedAt이 갱신된다")
    void updateNickname_갱신_검증() {
        Member member = Member.create("test@example.com", "기존닉네임", OAuthProvider.KAKAO, "kakao-id-456");

        member.updateNickname("새닉네임");

        assertThat(member.getNickname()).isEqualTo("새닉네임");
        assertThat(member.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("회원 탈퇴 처리 시 status가 WITHDRAWN으로 변경된다")
    void withdraw_상태_변경() {
        Member member = Member.create("test@example.com", "닉네임", OAuthProvider.GOOGLE, "google-sub-789");

        member.withdraw();

        assertThat(member.getStatus()).isEqualTo(MemberStatus.WITHDRAWN);
        assertThat(member.isWithdrawn()).isTrue();
        assertThat(member.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("ACTIVE 회원은 isWithdrawn이 false다")
    void isWithdrawn_활성회원_false() {
        Member member = Member.create("test@example.com", "닉네임", OAuthProvider.GOOGLE, "google-sub-000");

        assertThat(member.isWithdrawn()).isFalse();
    }

    @Test
    @DisplayName("마지막 로그인 시각을 갱신한다")
    void updateLastLoginAt_갱신() {
        Member member = Member.create("test@example.com", "닉네임", OAuthProvider.GOOGLE, "google-sub-111");

        assertThat(member.getLastLoginAt()).isNull();

        member.updateLastLoginAt();

        assertThat(member.getLastLoginAt()).isNotNull();
    }
}
