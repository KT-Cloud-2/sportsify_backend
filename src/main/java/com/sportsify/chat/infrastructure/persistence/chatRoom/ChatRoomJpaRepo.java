package com.sportsify.chat.infrastructure.persistence.chatRoom;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChatRoomJpaRepo extends JpaRepository<ChatRoomJpaEntity, Long> {

    @Query("SELECT r FROM ChatRoomJpaEntity r " +
            "WHERE r.gameId = :gameId " +
            "AND r.status = 'ACTIVE' " +
            "AND (:cursor IS NULL OR r.id < :cursor) " +
            "ORDER BY r.id DESC")
    List<ChatRoomJpaEntity> findActiveByGameId(
            @Param("gameId") Long gameId,
            @Param("cursor") Long cursor,
            Pageable pageable);


    @Query(value = "SELECT cr.id FROM chat_rooms cr " +
            "JOIN chat_room_members m1 ON m1.member_id = :memberId1 AND m1.room_id = cr.id AND m1.status IN ('JOINED', 'INVITED') " +
            "JOIN chat_room_members m2 ON m2.member_id = :memberId2 AND m2.room_id = cr.id AND m2.status IN ('JOINED', 'INVITED') " +
            "WHERE cr.type = 'DIRECT' AND cr.status != 'DELETED' " +
            "LIMIT 1",
            nativeQuery = true)
    Optional<Long> findExistingDirect(@Param("memberId1") Long memberId1, @Param("memberId2") Long memberId2);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM ChatRoomJpaEntity r WHERE r.id = :id")
    Optional<ChatRoomJpaEntity> findByIdForUpdate(@Param("id") Long id);

    @Query("SELECT r FROM ChatRoomJpaEntity r " +
            "WHERE r.id IN :roomIds " +
            "AND r.type = :type " +
            "AND r.status = 'ACTIVE' " +
            "AND (:cursor IS NULL OR r.id < :cursor) " +
            "ORDER BY r.id DESC")
    List<ChatRoomJpaEntity> findActiveByRoomIds(
            @Param("roomIds") List<Long> roomIds,
            @Param("type") String type,
            @Param("cursor") Long cursor,
            Pageable pageable
    );
}
