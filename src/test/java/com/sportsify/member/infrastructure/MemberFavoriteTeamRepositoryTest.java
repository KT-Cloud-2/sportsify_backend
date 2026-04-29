package com.sportsify.member.infrastructure;

import com.sportsify.member.domain.model.Member;
import com.sportsify.member.domain.model.MemberFavoriteTeam;
import com.sportsify.member.domain.model.OAuthProvider;
import com.sportsify.member.infrastructure.repository.MemberFavoriteTeamJpaRepository;
import com.sportsify.member.infrastructure.repository.MemberJpaRepository;
import com.sportsify.support.RepositoryTestSupport;
import com.sportsify.team.domain.model.SportType;
import com.sportsify.team.domain.model.Team;
import com.sportsify.team.infrastructure.repository.TeamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class MemberFavoriteTeamRepositoryTest extends RepositoryTestSupport {

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private MemberFavoriteTeamJpaRepository favoriteTeamRepository;

    private Member member;
    private Team teamA;
    private Team teamB;

    @BeforeEach
    void setUp() {
        member = memberRepository.save(
                Member.create("test@test.com", "테스터", OAuthProvider.GOOGLE, "g-001")
        );
        teamA = teamRepository.save(createTeam("KIA 타이거즈", "KIA", SportType.BASEBALL));
        teamB = teamRepository.save(createTeam("두산 베어스", "두산", SportType.BASEBALL));
    }

    @Test
    @DisplayName("선호 팀 목록을 priority 오름차순으로 조회한다")
    void findByMemberIdOrderByPriorityAsc_순서_검증() {
        favoriteTeamRepository.save(MemberFavoriteTeam.create(member, teamA, 2));
        favoriteTeamRepository.save(MemberFavoriteTeam.create(member, teamB, 1));

        List<MemberFavoriteTeam> result = favoriteTeamRepository.findByMemberIdOrderByPriorityAsc(member.getId());

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getTeam().getName()).isEqualTo("두산 베어스");
        assertThat(result.get(1).getTeam().getName()).isEqualTo("KIA 타이거즈");
    }

    @Test
    @DisplayName("memberId와 teamId로 선호 팀을 조회한다")
    void findByMemberIdAndTeamId_존재하는_경우() {
        favoriteTeamRepository.save(MemberFavoriteTeam.create(member, teamA, 1));

        Optional<MemberFavoriteTeam> result = favoriteTeamRepository.findByMemberIdAndTeamId(member.getId(), teamA.getId());

        assertThat(result).isPresent();
    }

    @Test
    @DisplayName("등록하지 않은 팀은 존재 여부 조회 시 false를 반환한다")
    void existsByMemberIdAndTeamId_미등록_팀() {
        assertThat(favoriteTeamRepository.existsByMemberIdAndTeamId(member.getId(), teamA.getId())).isFalse();
    }

    @Test
    @DisplayName("선호 팀을 등록하면 existsByMemberIdAndTeamId가 true를 반환한다")
    void existsByMemberIdAndTeamId_등록된_팀() {
        favoriteTeamRepository.save(MemberFavoriteTeam.create(member, teamA, 1));

        assertThat(favoriteTeamRepository.existsByMemberIdAndTeamId(member.getId(), teamA.getId())).isTrue();
    }

    @Test
    @DisplayName("회원의 선호 팀 최대 priority를 반환한다")
    void findMaxPriorityByMemberId_최대값_반환() {
        favoriteTeamRepository.save(MemberFavoriteTeam.create(member, teamA, 1));
        favoriteTeamRepository.save(MemberFavoriteTeam.create(member, teamB, 3));

        int max = favoriteTeamRepository.findMaxPriorityByMemberId(member.getId());

        assertThat(max).isEqualTo(3);
    }

    @Test
    @DisplayName("선호 팀이 없으면 findMaxPriorityByMemberId는 0을 반환한다")
    void findMaxPriorityByMemberId_없으면_0() {
        int max = favoriteTeamRepository.findMaxPriorityByMemberId(member.getId());

        assertThat(max).isZero();
    }

    @Test
    @DisplayName("회원의 선호 팀 수를 정확히 반환한다")
    void countByMemberId_개수_반환() {
        favoriteTeamRepository.save(MemberFavoriteTeam.create(member, teamA, 1));
        favoriteTeamRepository.save(MemberFavoriteTeam.create(member, teamB, 2));

        assertThat(favoriteTeamRepository.countByMemberId(member.getId())).isEqualTo(2);
    }

    private Team createTeam(String name, String shortName, SportType sportType) {
        return Team.createForTest(name, shortName, sportType);
    }
}
