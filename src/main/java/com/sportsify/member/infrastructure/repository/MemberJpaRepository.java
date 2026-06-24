package com.sportsify.member.infrastructure.repository;

import com.sportsify.member.domain.model.Member;
import com.sportsify.member.domain.model.OAuthProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MemberJpaRepository extends JpaRepository<Member, Long> {

    Optional<Member> findByProviderAndProviderId(OAuthProvider provider, String providerId);

    Optional<Member> findByEmail(String email);

    boolean existsByNickname(String nickname);
}
