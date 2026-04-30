package com.sportsify.member.infrastructure;

import com.sportsify.member.domain.model.Member;
import com.sportsify.member.domain.model.OAuthProvider;
import com.sportsify.member.infrastructure.repository.MemberJpaRepository;
import com.sportsify.support.RepositoryTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class MemberRepositoryTest extends RepositoryTestSupport {

    @Autowired
    private MemberJpaRepository memberRepository;

    @Test
    @DisplayName("provider와 providerId로 회원을 조회한다")
    void findByProviderAndProviderId_존재하는_회원() {
        Member saved = memberRepository.save(
                Member.create("test@google.com", "구글유저", OAuthProvider.GOOGLE, "google-sub-001")
        );

        Optional<Member> found = memberRepository.findByProviderAndProviderId(OAuthProvider.GOOGLE, "google-sub-001");

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(saved.getId());
    }

    @Test
    @DisplayName("존재하지 않는 provider/providerId 조합이면 빈 Optional을 반환한다")
    void findByProviderAndProviderId_없는_회원() {
        Optional<Member> found = memberRepository.findByProviderAndProviderId(OAuthProvider.KAKAO, "no-such-id");

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("이미 사용 중인 닉네임이면 existsByNickname이 true를 반환한다")
    void existsByNickname_사용중인_닉네임() {
        memberRepository.save(Member.create("a@test.com", "유니크닉네임", OAuthProvider.GOOGLE, "g-001"));

        assertThat(memberRepository.existsByNickname("유니크닉네임")).isTrue();
    }

    @Test
    @DisplayName("사용하지 않는 닉네임이면 existsByNickname이 false를 반환한다")
    void existsByNickname_사용안하는_닉네임() {
        assertThat(memberRepository.existsByNickname("없는닉네임")).isFalse();
    }
}
