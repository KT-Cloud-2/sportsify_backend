package com.sportsify.chat.config;

import com.sportsify.chat.infrastructure.persistence.chatRoom.ChatRoomJpaEntity;
import com.sportsify.chat.infrastructure.persistence.chatRoom.ChatRoomJpaRepository;
import com.sportsify.chat.infrastructure.persistence.chatRoomMember.ChatRoomMemberJpaEntity;
import com.sportsify.chat.infrastructure.persistence.chatRoomMember.ChatRoomMemberJpaRepository;
import com.sportsify.chat.infrastructure.persistence.message.MessageJpaEntity;
import com.sportsify.chat.infrastructure.persistence.message.MessageJpaRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;

@Component
public class ChatIntegrationTestFixture {

    @Autowired
    private ChatRoomJpaRepository roomJpaRepo;

    @Autowired
    private ChatRoomMemberJpaRepository memberJpaRepo;

    @Autowired
    private MessageJpaRepository messageJpaRepo;

    @PersistenceContext
    private EntityManager em;

    @Transactional
    public ChatRoomJpaEntity createRoom(String name, String type, String status, Long createdBy) {
        createMemberRecord(createdBy);
        return roomJpaRepo.save(new ChatRoomJpaEntity(
                null, name, type, null, null,
                LocalDateTime.now(), LocalDateTime.now(),
                status, createdBy));
    }

    @Transactional
    public ChatRoomMemberJpaEntity createMember(Long roomId, Long memberId, String status) {
        createMemberRecord(memberId);
        return memberJpaRepo.save(new ChatRoomMemberJpaEntity(
                null, roomId, memberId, status, true,
                LocalDateTime.now(), LocalDateTime.now(), null));
    }

    @Transactional
    public ChatRoomMemberJpaEntity updateNotification(ChatRoomMemberJpaEntity member, boolean notification) {
        member.setNotificationEnabled(notification);
        return memberJpaRepo.save(member);
    }

    @Transactional
    public MessageJpaEntity createMessage(Long roomId, Long senderId) {
        createMemberRecord(senderId);
        return messageJpaRepo.save(new MessageJpaEntity(
                null, roomId, senderId, "테스트 메시지", "TEXT", "ACTIVE", Instant.now()));
    }

    @Transactional
    public void createMemberRecord(Long memberId) {
        em.createNativeQuery(
                        "INSERT INTO members (id, email, nickname, provider, provider_id, status, role, created_at) " +
                                "VALUES (:id, :email, 'TestUser', 'KAKAO', :providerId, 'ACTIVE', 'USER', NOW()) " +
                                "ON CONFLICT DO NOTHING"
                )
                .setParameter("id", memberId)
                .setParameter("email", "test" + memberId + "@test.com")
                .setParameter("providerId", "test-provider-" + memberId)
                .executeUpdate();
    }

    @Transactional
    public Long createGameRecord() {
        em.createNativeQuery(
                "INSERT INTO stadiums (id, name, address, total_seats) " +
                        "VALUES (9001, '테스트 경기장', '서울', 50000) " +
                        "ON CONFLICT DO NOTHING"
        ).executeUpdate();

        return ((Number) em.createNativeQuery(
                "INSERT INTO games (stadium_id, sport_type, start_at, status, created_at) " +
                        "VALUES (9001, 'BASEBALL', NOW(), 'SCHEDULED', NOW()) " +
                        "RETURNING id"
        ).getSingleResult()).longValue();
    }

    @Transactional
    public void deleteAll() {
        em.createNativeQuery("TRUNCATE TABLE notification_history, notifications, notification_events, chat_room_members, chat_messages, chat_rooms").executeUpdate();
        em.createNativeQuery("DELETE FROM games").executeUpdate();
        em.createNativeQuery("DELETE FROM stadiums WHERE id = 9001").executeUpdate();
        em.createNativeQuery("DELETE FROM members WHERE id >= 1000").executeUpdate();
    }
}
