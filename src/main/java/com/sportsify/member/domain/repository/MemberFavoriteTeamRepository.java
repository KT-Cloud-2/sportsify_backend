package com.sportsify.member.domain.repository;

import com.sportsify.member.domain.model.MemberFavoriteTeam;

import java.util.List;
import java.util.Optional;

public interface MemberFavoriteTeamRepository {

    List<MemberFavoriteTeam> findByMemberIdOrderByPriorityAsc(Long memberId);

    Optional<MemberFavoriteTeam> findByMemberIdAndTeamId(Long memberId, Long teamId);

    boolean existsByMemberIdAndTeamId(Long memberId, Long teamId);

    int findMaxPriorityByMemberId(Long memberId);

    long countByMemberId(Long memberId);

    MemberFavoriteTeam save(MemberFavoriteTeam favoriteTeam);

    void delete(MemberFavoriteTeam favoriteTeam);
}
