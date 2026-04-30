package com.sportsify.member.infrastructure.repository;

import com.sportsify.member.domain.model.MemberFavoriteTeam;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MemberFavoriteTeamJpaRepository extends JpaRepository<MemberFavoriteTeam, Long> {

    List<MemberFavoriteTeam> findByMemberIdOrderByPriorityAsc(Long memberId);

    Optional<MemberFavoriteTeam> findByMemberIdAndTeamId(Long memberId, Long teamId);

    boolean existsByMemberIdAndTeamId(Long memberId, Long teamId);

    @Query("SELECT COALESCE(MAX(f.priority), 0) FROM MemberFavoriteTeam f WHERE f.member.id = :memberId")
    int findMaxPriorityByMemberId(@Param("memberId") Long memberId);

    long countByMemberId(Long memberId);
}
