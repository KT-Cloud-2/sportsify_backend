package com.sportsify.member.domain.model;

import com.sportsify.team.domain.model.Team;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "member_favorite_teams")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberFavoriteTeam {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @Column(nullable = false)
    private int priority;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public static MemberFavoriteTeam create(Member member, Team team, int priority) {
        MemberFavoriteTeam favoriteTeam = new MemberFavoriteTeam();
        favoriteTeam.member = member;
        favoriteTeam.team = team;
        favoriteTeam.priority = priority;
        favoriteTeam.createdAt = LocalDateTime.now();
        return favoriteTeam;
    }

    public void updatePriority(int priority) {
        this.priority = priority;
    }
}
