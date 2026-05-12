package com.sportsify.chat.infrastructure.persistence.message;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Message Spring Data JPA Repository
 */
public interface MessageJpaRepository extends JpaRepository<MessageJpaEntity, Long> {


    @Query("SELECT m FROM MessageJpaEntity m " +
            "WHERE m.roomId = :roomId " +
            "ORDER BY m.id DESC")
    List<MessageJpaEntity> findLatest(@Param("roomId") Long roomId, Pageable pageable);


    @Query("SELECT m FROM MessageJpaEntity m " +
            "WHERE m.roomId = :roomId " +
            "AND m.id < :beforeMessageId " +
            "ORDER BY m.id DESC")
    List<MessageJpaEntity> findBefore(@Param("roomId") Long roomId,
                                      @Param("beforeMessageId") Long beforeMessageId,
                                      Pageable pageable);


    @Query("SELECT COUNT(m) FROM MessageJpaEntity m " +
            "WHERE m.roomId = :roomId " +
            "AND m.id > :afterMessageId " +
            "AND m.status = 'ACTIVE'")
    long countAfter(@Param("roomId") Long roomId,
                    @Param("afterMessageId") Long afterMessageId);

    @Query("SELECT COUNT(m) FROM MessageJpaEntity m " +
            "WHERE m.roomId = :roomId " +
            "AND m.status = 'ACTIVE'")
    long countAll(@Param("roomId") Long roomId);


    @Query("SELECT m FROM MessageJpaEntity m " +
            "WHERE m.senderId = :senderId " +
            "AND m.roomId = :roomId " +
            "ORDER BY m.id DESC")
    List<MessageJpaEntity> findLatestBySenderAndRoom(@Param("roomId") Long roomId, @Param("senderId") Long senderId, Pageable pageable);

    @Query("SELECT m FROM MessageJpaEntity m " +
            "WHERE m.senderId = :senderId " +
            "AND m.roomId = :roomId " +
            "AND m.id < :beforeMessageId " +
            "ORDER BY m.id DESC")
    List<MessageJpaEntity> findBeforeBySenderAndRoom(@Param("roomId") Long roomId,
                                                     @Param("senderId") Long senderId,
                                                     @Param("beforeMessageId") Long beforeMessageId,
                                                     Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM MessageJpaEntity m " +
            "WHERE m.id = :messageId")
    Optional<MessageJpaEntity> findByIdForUpdate(@Param("messageId") Long messageId);

    @Query(value = """                                                                                                                                                                                                                                                                                                
            SELECT m.* FROM chat_messages m                                                                                                                                                                                                                                                                               
            INNER JOIN (
              SELECT room_id, MAX(id) AS max_id
              FROM chat_messages                                                                                                                                                                                                                                                                                            
              WHERE room_id IN :roomIds AND sender_id = :memberId
              GROUP BY room_id                                                                                                                                                                                                                                                                                        
            ) latest ON m.id = latest.max_id                                                                                                                                                                                                                                                                        
            """,
            nativeQuery = true)
    List<MessageJpaEntity> findMyLatestByRooms(
            @Param("roomIds") List<Long> roomIds,
            @Param("memberId") Long memberId);
}
