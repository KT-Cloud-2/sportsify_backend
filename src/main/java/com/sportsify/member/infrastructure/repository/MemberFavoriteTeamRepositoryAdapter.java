package com.sportsify.member.infrastructure.repository;

import com.sportsify.member.domain.model.MemberFavoriteTeam;
import com.sportsify.member.domain.repository.MemberFavoriteTeamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class MemberFavoriteTeamRepositoryAdapter implements MemberFavoriteTeamRepository {

    private final MemberFavoriteTeamJpaRepository jpaRepository;

    @Override
    public List<MemberFavoriteTeam> findByMemberIdOrderByPriorityAsc(Long memberId) {
        return jpaRepository.findByMemberIdOrderByPriorityAsc(memberId);
    }

    @Override
    public Optional<MemberFavoriteTeam> findByMemberIdAndTeamId(Long memberId, Long teamId) {
        return jpaRepository.findByMemberIdAndTeamId(memberId, teamId);
    }

    @Override
    public boolean existsByMemberIdAndTeamId(Long memberId, Long teamId) {
        return jpaRepository.existsByMemberIdAndTeamId(memberId, teamId);
    }

    @Override
    public int findMaxPriorityByMemberId(Long memberId) {
        return jpaRepository.findMaxPriorityByMemberId(memberId);
    }

    @Override
    public long countByMemberId(Long memberId) {
        return jpaRepository.countByMemberId(memberId);
    }

    @Override
    public MemberFavoriteTeam save(MemberFavoriteTeam favoriteTeam) {
        return jpaRepository.save(favoriteTeam);
    }

    @Override
    public void delete(MemberFavoriteTeam favoriteTeam) {
        jpaRepository.delete(favoriteTeam);
    }
}
