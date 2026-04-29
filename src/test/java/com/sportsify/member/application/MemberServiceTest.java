package com.sportsify.member.application;

import com.sportsify.common.exception.BusinessException;
import com.sportsify.common.exception.ErrorCode;
import com.sportsify.member.application.dto.FavoriteTeamResult;
import com.sportsify.member.application.dto.MemberResult;
import com.sportsify.member.application.service.MemberService;
import com.sportsify.member.domain.model.Member;
import com.sportsify.member.domain.model.MemberFavoriteTeam;
import com.sportsify.member.domain.model.MemberStatus;
import com.sportsify.member.domain.model.OAuthProvider;
import com.sportsify.member.domain.repository.MemberFavoriteTeamRepository;
import com.sportsify.member.domain.repository.MemberRepository;
import com.sportsify.team.domain.model.SportType;
import com.sportsify.team.domain.model.Team;
import com.sportsify.team.infrastructure.repository.TeamRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

    @InjectMocks
    private MemberService memberService;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private MemberFavoriteTeamRepository favoriteTeamRepository;

    @Mock
    private TeamRepository teamRepository;

    // ──────────────────────── getMe ────────────────────────

    @Test
    @DisplayName("활성 회원의 내 정보를 조회한다")
    void getMe_활성회원_정보반환() {
        Member member = activeMember(1L);
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));

        MemberResult result = memberService.getMe(1L);

        assertThat(result.email()).isEqualTo("test@example.com");
    }

    @Test
    @DisplayName("탈퇴한 회원 조회 시 MEMBER_NOT_FOUND 예외가 발생한다")
    void getMe_탈퇴회원_예외() {
        Member member = activeMember(1L);
        member.withdraw();
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));

        assertThatThrownBy(() -> memberService.getMe(1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.MEMBER_NOT_FOUND);
    }

    // ──────────────────────── updateNickname ────────────────────────

    @Test
    @DisplayName("닉네임을 성공적으로 변경한다")
    void updateNickname_성공() {
        Member member = activeMember(1L);
        given(memberRepository.existsByNickname("새닉네임")).willReturn(false);
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));

        MemberResult result = memberService.updateNickname(1L, "새닉네임");

        assertThat(result.nickname()).isEqualTo("새닉네임");
    }

    @Test
    @DisplayName("중복 닉네임으로 변경 시 NICKNAME_DUPLICATE 예외가 발생한다")
    void updateNickname_중복닉네임_예외() {
        given(memberRepository.existsByNickname("중복닉")).willReturn(true);

        assertThatThrownBy(() -> memberService.updateNickname(1L, "중복닉"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.NICKNAME_DUPLICATE);
    }

    // ──────────────────────── withdraw ────────────────────────

    @Test
    @DisplayName("회원 탈퇴 처리 시 status가 WITHDRAWN으로 변경된다")
    void withdraw_상태변경() {
        Member member = activeMember(1L);
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));

        memberService.withdraw(1L);

        assertThat(member.getStatus()).isEqualTo(MemberStatus.WITHDRAWN);
    }

    // ──────────────────────── addFavoriteTeam ────────────────────────

    @Test
    @DisplayName("선호 팀을 추가한다 — priority 미지정 시 max+1이 할당된다")
    void addFavoriteTeam_우선순위_자동할당() {
        Member member = activeMember(1L);
        Team team = team(10L);
        given(favoriteTeamRepository.existsByMemberIdAndTeamId(1L, 10L)).willReturn(false);
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));
        given(teamRepository.findById(10L)).willReturn(Optional.of(team));
        given(favoriteTeamRepository.findMaxPriorityByMemberId(1L)).willReturn(2);
        given(favoriteTeamRepository.save(org.mockito.ArgumentMatchers.any()))
                .willAnswer(inv -> inv.getArgument(0));

        FavoriteTeamResult result = memberService.addFavoriteTeam(1L, 10L, null);

        assertThat(result.priority()).isEqualTo(3);
    }

    @Test
    @DisplayName("이미 등록된 팀 추가 시 FAVORITE_TEAM_ALREADY_EXISTS 예외가 발생한다")
    void addFavoriteTeam_중복등록_예외() {
        given(favoriteTeamRepository.existsByMemberIdAndTeamId(1L, 10L)).willReturn(true);

        assertThatThrownBy(() -> memberService.addFavoriteTeam(1L, 10L, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.FAVORITE_TEAM_ALREADY_EXISTS);
    }

    @Test
    @DisplayName("존재하지 않는 팀 추가 시 TEAM_NOT_FOUND 예외가 발생한다")
    void addFavoriteTeam_팀없음_예외() {
        Member member = activeMember(1L);
        given(favoriteTeamRepository.existsByMemberIdAndTeamId(1L, 99L)).willReturn(false);
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));
        given(teamRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> memberService.addFavoriteTeam(1L, 99L, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.TEAM_NOT_FOUND);
    }

    // ──────────────────────── getFavoriteTeams ────────────────────────

    @Test
    @DisplayName("선호 팀 목록을 priority 오름차순으로 반환한다")
    void getFavoriteTeams_목록반환() {
        Member member = activeMember(1L);
        Team teamA = team(10L);
        Team teamB = team(20L);
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));
        given(favoriteTeamRepository.findByMemberIdOrderByPriorityAsc(1L)).willReturn(List.of(
                MemberFavoriteTeam.create(member, teamA, 1),
                MemberFavoriteTeam.create(member, teamB, 2)
        ));

        List<FavoriteTeamResult> result = memberService.getFavoriteTeams(1L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).priority()).isEqualTo(1);
    }

    // ──────────────────────── updateFavoriteTeamPriority ────────────────────────

    @Test
    @DisplayName("우선순위 범위 초과 시 INVALID_PRIORITY 예외가 발생한다")
    void updateFavoriteTeamPriority_범위초과_예외() {
        given(favoriteTeamRepository.countByMemberId(1L)).willReturn(2L);

        assertThatThrownBy(() -> memberService.updateFavoriteTeamPriority(1L, 10L, 5))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_PRIORITY);
    }

    @Test
    @DisplayName("등록되지 않은 팀의 우선순위 변경 시 FAVORITE_TEAM_NOT_FOUND 예외가 발생한다")
    void updateFavoriteTeamPriority_미등록팀_예외() {
        given(favoriteTeamRepository.countByMemberId(1L)).willReturn(3L);
        given(favoriteTeamRepository.findByMemberIdAndTeamId(1L, 10L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> memberService.updateFavoriteTeamPriority(1L, 10L, 2))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.FAVORITE_TEAM_NOT_FOUND);
    }

    // ──────────────────────── removeFavoriteTeam ────────────────────────

    @Test
    @DisplayName("등록된 선호 팀을 삭제한다")
    void removeFavoriteTeam_삭제성공() {
        Member member = activeMember(1L);
        Team team = team(10L);
        MemberFavoriteTeam favoriteTeam = MemberFavoriteTeam.create(member, team, 1);
        given(favoriteTeamRepository.findByMemberIdAndTeamId(1L, 10L)).willReturn(Optional.of(favoriteTeam));

        memberService.removeFavoriteTeam(1L, 10L);

        verify(favoriteTeamRepository).delete(favoriteTeam);
    }

    // ──────────────────────── 픽스처 헬퍼 ────────────────────────

    private Member activeMember(Long id) {
        Member member = Member.create("test@example.com", "테스터", OAuthProvider.GOOGLE, "g-" + id);
        setId(member, id);
        return member;
    }

    private Team team(Long id) {
        Team team = Team.createForTest("팀" + id, "T" + id, SportType.BASEBALL);
        setId(team, id);
        return team;
    }

    private void setId(Object entity, Long id) {
        try {
            var field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
