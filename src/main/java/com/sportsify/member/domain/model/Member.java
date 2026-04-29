package com.sportsify.member.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "members")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String email;

    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OAuthProvider provider;

    @Column(name = "provider_id", nullable = false)
    private String providerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MemberStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MemberRole role;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    public static Member create(String email, String nickname, OAuthProvider provider, String providerId) {
        Member member = new Member();
        member.email = email;
        member.nickname = nickname;
        member.provider = provider;
        member.providerId = providerId;
        member.status = MemberStatus.ACTIVE;
        member.role = MemberRole.USER;
        member.createdAt = LocalDateTime.now();
        return member;
    }

    public void updateLastLoginAt() {
        this.lastLoginAt = LocalDateTime.now();
    }

    public void updateNickname(String nickname) {
        this.nickname = nickname;
        this.updatedAt = LocalDateTime.now();
    }

    public void withdraw() {
        this.status = MemberStatus.WITHDRAWN;
        this.updatedAt = LocalDateTime.now();
    }

    public boolean isWithdrawn() {
        return this.status == MemberStatus.WITHDRAWN;
    }
}
