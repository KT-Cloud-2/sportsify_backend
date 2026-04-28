package com.sportsify.chat.infrastructure.persistence.chatRoomMember;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * ChatRoomMember Spring Data JPA Repository.
 */
public interface ChatRoomMemberJpaRepo extends JpaRepository<ChatRoomMemberJpaEntity, Long> {

    Optional<ChatRoomMemberJpaEntity> findByRoomIdAndMemberId(Long roomId, Long memberId);

    @Query("SELECT m FROM ChatRoomMemberJpaEntity m " +
            "WHERE m.roomId = :roomId " +
            "AND m.status IN ('JOINED', 'INVITED')")
    List<ChatRoomMemberJpaEntity> findActiveByRoomId(@Param("roomId") Long roomId);

    @Query("SELECT m FROM ChatRoomMemberJpaEntity m " +
            "WHERE m.memberId = :memberId " +
            "AND m.status IN ('JOINED', 'INVITED')")
    List<ChatRoomMemberJpaEntity> findActiveByMemberId(@Param("memberId") Long memberId);

    @Query("SELECT COUNT(m) FROM ChatRoomMemberJpaEntity m " +
            "WHERE m.roomId = :roomId " +
            "AND m.status IN ('JOINED', 'INVITED')")
    long countActiveByRoomId(@Param("roomId") Long roomId);

    boolean existsByRoomIdAndMemberId(Long roomId, Long memberId);
}
