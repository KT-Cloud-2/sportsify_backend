package com.sportsify.member.infrastructure.repository;

import com.sportsify.member.domain.model.Member;
import com.sportsify.member.domain.model.OAuthProvider;
import com.sportsify.member.domain.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class MemberRepositoryAdapter implements MemberRepository {

    private final MemberJpaRepository jpaRepository;

    @Override
    public Optional<Member> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public Optional<Member> findByProviderAndProviderId(OAuthProvider provider, String providerId) {
        return jpaRepository.findByProviderAndProviderId(provider, providerId);
    }

    @Override
    public boolean existsByNickname(String nickname) {
        return jpaRepository.existsByNickname(nickname);
    }
}
