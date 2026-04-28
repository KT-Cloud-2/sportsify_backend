package com.sportsify.chat.infrastructure.persistence.message;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Message Spring Data JPA Repository
 */
public interface MessageJpaRepo extends JpaRepository<MessageJpaEntity, Long> {

    /**
     * 최초 페이지: 가장 최근 메시지부터 조회.
     */
    @Query("SELECT m FROM MessageJpaEntity m " +
            "WHERE m.roomId = :roomId " +
            "ORDER BY m.id DESC")
    List<MessageJpaEntity> findLatest(@Param("roomId") Long roomId, Pageable pageable);

    /**
     * 커서 페이징: 주어진 messageId 보다 과거(작은 id) 메시지를 최신순으로 조회.
     */
    @Query("SELECT m FROM MessageJpaEntity m " +
            "WHERE m.roomId = :roomId " +
            "AND m.id < :beforeMessageId " +
            "ORDER BY m.id DESC")
    List<MessageJpaEntity> findBefore(@Param("roomId") Long roomId,
                                      @Param("beforeMessageId") Long beforeMessageId,
                                      Pageable pageable);

    /**
     * 주어진 messageId 보다 큰(이후) 메시지 개수. 안읽은 메시지 카운트용.
     */
    @Query("SELECT COUNT(m) FROM MessageJpaEntity m " +
            "WHERE m.roomId = :roomId " +
            "AND m.id > :afterMessageId " +
            "AND m.status = 'ACTIVE'")
    long countAfter(@Param("roomId") Long roomId,
                    @Param("afterMessageId") Long afterMessageId);

    /**
     * 한 번도 읽지 않은 경우 사용: 채팅방의 모든 활성 메시지 개수.
     */
    @Query("SELECT COUNT(m) FROM MessageJpaEntity m " +
            "WHERE m.roomId = :roomId " +
            "AND m.status = 'ACTIVE'")
    long countAll(@Param("roomId") Long roomId);
}