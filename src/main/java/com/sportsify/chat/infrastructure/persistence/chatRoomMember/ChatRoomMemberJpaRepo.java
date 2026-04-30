package com.sportsify.chat.infrastructure.persistence.chatRoomMember;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * ChatRoomMember Spring Data JPA Repository.
 */
public interface ChatRoomMemberJpaRepo extends JpaRepository<ChatRoomMemberJpaEntity, Long> {

    @Query("SELECT m FROM ChatRoomMemberJpaEntity m " +
            "WHERE m.roomId = :roomId " +
            "AND m.memberId = :memberId " +
            "AND m.status IN :statuses")
    Optional<ChatRoomMemberJpaEntity> findByRoomIdAndMemberIdInternal(@Param("roomId") Long roomId, @Param("memberId") Long memberId, @Param("statuses") List<String> statuses);

    default Optional<ChatRoomMemberJpaEntity> findByRoomIdAndMemberId(Long roomId, Long memberId) {
        return findByRoomIdAndMemberId(roomId, memberId, null);
    }

    default Optional<ChatRoomMemberJpaEntity> findByRoomIdAndMemberId(Long roomId, Long memberId, List<String> statuses) {
        if (memberId == null) return Optional.empty();
        List<String> resolved = (statuses == null || statuses.isEmpty()) ? List.of("JOINED", "INVITED") : statuses;
        return findByRoomIdAndMemberIdInternal(roomId, memberId, resolved);
    }

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


    boolean existsByRoomIdAndMemberIdAndStatus(Long roomId, Long memberId, String status);

    @Query("SELECT m.roomId, COUNT(m) FROM ChatRoomMemberJpaEntity m " +
            "WHERE m.roomId IN :roomIds " +
            "AND m.status IN ('JOINED', 'INVITED') " +
            "GROUP BY m.roomId")
    List<Object[]> countActiveByRoomIds(@Param("roomIds") List<Long> roomIds);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE ChatRoomMemberJpaEntity m SET m.status = 'DELETED' , m.updatedAt = :now " +
            "WHERE m.roomId = :roomId AND m.status IN ('JOINED', 'INVITED')")
    void leaveAllActiveByRoomId(@Param("roomId") Long roomId, @Param("now") LocalDateTime now);

}
