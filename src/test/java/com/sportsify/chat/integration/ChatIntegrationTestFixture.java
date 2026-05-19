package com.sportsify.chat.integration;

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

/**
 * 채팅 통합 테스트용 픽스처 컴포넌트.
 * <p>
 * 서비스 계층을 통하지 않고 JPA Entity를 직접 저장해
 * 원하는 DB 상태(예: ARCHIVED, BANNED, EMPTY)를 자유롭게 세팅한다.
 * 각 메서드는 독립적인 @Transactional을 가지므로, 외부 트랜잭션이 없는 경우에도 커밋된다.
 * <p>
 * FK 처리:
 * - chat_rooms.game_id → games.id: 테스트에서 실제 game 레코드가 불필요하므로 null 사용
 * - chat_room_members.member_id → members.id: createMember 호출 시 members 레코드를 함께 삽입
 * - chat_messages.sender_id → members.id: createMessage 호출 시 members 레코드를 함께 삽입
 */
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
        return createRoom(name, type, status, createdBy, null);
    }

    @Transactional
    public ChatRoomJpaEntity createRoom(String name, String type, String status, Long createdBy, Long gameId) {
        createMemberRecord(createdBy);
        return roomJpaRepo.save(new ChatRoomJpaEntity(
                null, name, type, null, gameId,
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
    public MessageJpaEntity createMessage(Long roomId, Long senderId) {
        createMemberRecord(senderId);
        return messageJpaRepo.save(new MessageJpaEntity(
                null, roomId, senderId, "테스트 메시지", "TEXT", "ACTIVE", Instant.now()));
    }

    /**
     * 테스트용 members 레코드를 지정 ID로 삽입한다.
     * 이미 존재하면 아무 작업도 하지 않는다
     */
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
    public void deleteAll() {
        // FK 의존 순서: chat_room_members(→chat_messages, →chat_rooms, →members)
        //              → chat_messages(→chat_rooms) → chat_rooms(→members) → members
        memberJpaRepo.deleteAll();
        messageJpaRepo.deleteAll();
        roomJpaRepo.deleteAll();
        // members 테이블의 테스트용 레코드 정리 (id >= 1000은 테스트 전용 범위)
        em.createNativeQuery("DELETE FROM members WHERE id >= 1000").executeUpdate();
    }
}
