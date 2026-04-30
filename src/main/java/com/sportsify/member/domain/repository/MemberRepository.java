package com.sportsify.member.domain.repository;

import com.sportsify.member.domain.model.Member;
import com.sportsify.member.domain.model.OAuthProvider;

import java.util.Optional;

public interface MemberRepository {

    Optional<Member> findById(Long id);

    Optional<Member> findByProviderAndProviderId(OAuthProvider provider, String providerId);

    boolean existsByNickname(String nickname);
}
