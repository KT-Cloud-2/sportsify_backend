package com.sportsify.chat.infrastructure.persistence.chatRoom;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChatRoomJpaRepo extends JpaRepository<ChatRoomJpaEntity, Long> {


    Optional<ChatRoomJpaEntity> findByGameId(Long gameId);


    @Query("SELECT m FROM ChatRoomMemberJpaEntity m " +
            "WHERE m.memberId = :memberId " +
            "AND m.status IN ('JOINED', 'INVITED')")
    List<ChatRoomJpaEntity> findExistingDirect(@Param("memberId") Long memberId, @Param("memberId") Long memberId);

}
