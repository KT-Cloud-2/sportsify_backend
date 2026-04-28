package com.sportsify.member.application.service;

import com.sportsify.common.exception.BusinessException;
import com.sportsify.common.exception.ErrorCode;
import com.sportsify.member.application.dto.FavoriteTeamResult;
import com.sportsify.member.application.dto.MemberResult;
import com.sportsify.member.domain.model.Member;
import com.sportsify.member.domain.model.MemberFavoriteTeam;
import com.sportsify.member.domain.repository.MemberFavoriteTeamRepository;
import com.sportsify.member.domain.repository.MemberRepository;
import com.sportsify.team.domain.model.Team;
import com.sportsify.team.infrastructure.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {

    private final MemberRepository memberRepository;
    private final MemberFavoriteTeamRepository favoriteTeamRepository;
    private final TeamRepository teamRepository;

    public MemberResult getMe(Long memberId) {
        Member member = findActiveMember(memberId);
        return MemberResult.from(member);
    }

    @Transactional
    public MemberResult updateNickname(Long memberId, String nickname) {
        if (memberRepository.existsByNickname(nickname)) {
            throw new BusinessException(ErrorCode.NICKNAME_DUPLICATE);
        }
        Member member = findActiveMember(memberId);
        member.updateNickname(nickname);
        return MemberResult.from(member);
    }

    @Transactional
    public void withdraw(Long memberId) {
        Member member = findActiveMember(memberId);
        member.withdraw();
    }

    @Transactional
    public FavoriteTeamResult addFavoriteTeam(Long memberId, Long teamId, Integer priority) {
        if (favoriteTeamRepository.existsByMemberIdAndTeamId(memberId, teamId)) {
            throw new BusinessException(ErrorCode.FAVORITE_TEAM_ALREADY_EXISTS);
        }
        Member member = findActiveMember(memberId);
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TEAM_NOT_FOUND));

        int assignedPriority = (priority != null) ? priority
                : favoriteTeamRepository.findMaxPriorityByMemberId(memberId) + 1;

        MemberFavoriteTeam favoriteTeam = MemberFavoriteTeam.create(member, team, assignedPriority);
        favoriteTeamRepository.save(favoriteTeam);
        return FavoriteTeamResult.from(favoriteTeam);
    }

    public List<FavoriteTeamResult> getFavoriteTeams(Long memberId) {
        findActiveMember(memberId);
        return favoriteTeamRepository.findByMemberIdOrderByPriorityAsc(memberId)
                .stream()
                .map(FavoriteTeamResult::from)
                .toList();
    }

    @Transactional
    public FavoriteTeamResult updateFavoriteTeamPriority(Long memberId, Long teamId, int priority) {
        long totalCount = favoriteTeamRepository.countByMemberId(memberId);
        if (priority < 1 || priority > totalCount) {
            throw new BusinessException(ErrorCode.INVALID_PRIORITY);
        }
        MemberFavoriteTeam favoriteTeam = favoriteTeamRepository.findByMemberIdAndTeamId(memberId, teamId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FAVORITE_TEAM_NOT_FOUND));
        favoriteTeam.updatePriority(priority);
        return FavoriteTeamResult.from(favoriteTeam);
    }

    @Transactional
    public void removeFavoriteTeam(Long memberId, Long teamId) {
        MemberFavoriteTeam favoriteTeam = favoriteTeamRepository.findByMemberIdAndTeamId(memberId, teamId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FAVORITE_TEAM_NOT_FOUND));
        favoriteTeamRepository.delete(favoriteTeam);
    }

    private Member findActiveMember(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        if (member.isWithdrawn()) {
            throw new BusinessException(ErrorCode.MEMBER_NOT_FOUND);
        }
        return member;
    }
}
