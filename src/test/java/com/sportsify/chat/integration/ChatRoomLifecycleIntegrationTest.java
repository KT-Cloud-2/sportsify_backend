package com.sportsify.chat.integration;

import com.sportsify.chat.application.chatRoom.service.ChatRoomService;
import com.sportsify.chat.application.chatRoomMember.service.ChatRoomMemberService;
import com.sportsify.chat.infrastructure.persistence.chatRoom.ChatRoomJpaEntity;
import com.sportsify.chat.infrastructure.persistence.chatRoom.ChatRoomJpaRepository;
import com.sportsify.chat.infrastructure.persistence.chatRoomMember.ChatRoomMemberJpaRepository;
import com.sportsify.support.RepositoryTestSupport;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * [통합] 채팅방 생명주기 트랜잭션 통합 테스트
 * <p>
 * 테스트 가치: 트랜잭션 원자성
 * - 방 삭제, 멤버 퇴장, 방 상태 전환이 하나의 트랜잭션 내에서 원자적으로 처리되는지 검증
 * <p>
 * 단위 테스트와의 차이:
 * - 단위 테스트는 Mock 기반으로 메서드 호출 여부만 검증
 * - 통합 테스트는 실제 DB에서 상태 변경이 원자적으로 반영되는지 검증
 */
@DisplayName("[통합] 채팅방 생명주기 트랜잭션 통합 테스트")
class ChatRoomLifecycleIntegrationTest extends RepositoryTestSupport {

    private static final Long CREATOR_ID = 1001L;
    private static final Long MEMBER_ID = 1002L;
    @Autowired
    private ChatRoomService chatRoomService;
    @Autowired
    private ChatRoomMemberService chatRoomMemberService;
    @Autowired
    private ChatRoomJpaRepository chatRoomJpaRepo;
    @Autowired
    private ChatRoomMemberJpaRepository chatRoomMemberJpaRepo;
    @Autowired
    private ChatIntegrationTestFixture fixture;
    @PersistenceContext
    private EntityManager entityManager;

    /**
     * 왜 통합 테스트가 필요한가:
     * - delete()는 방 상태 변경(save)과 leaveAllMembersByRoom(벌크 UPDATE)을 동일 트랜잭션에서 실행
     * - leaveAllMembersByRoom은 JPQL @Modifying 벌크 업데이트로, 실제 DB에서만 원자성 검증 가능
     * <p>
     * 실패 가능 포인트:
     * - leaveAllMembersByRoom이 별도 트랜잭션으로 분리되면
     * 방 삭제는 성공해도 멤버는 JOINED 상태로 남아 데이터 불일치 발생
     */
    @Test
    @DisplayName("방 삭제 시 모든 멤버가 DELETED 상태로 함께 처리된다")
    void 방_삭제_멤버_일괄_퇴장_원자성() {
        // Given
        ChatRoomJpaEntity room = fixture.createRoom("테스트방", "GAME", "ACTIVE", CREATOR_ID);
        fixture.createMember(room.getId(), CREATOR_ID, "JOINED");
        fixture.createMember(room.getId(), MEMBER_ID, "JOINED");
        entityManager.flush();
        entityManager.clear();

        // When
        chatRoomService.delete(room.getId(), CREATOR_ID);
        entityManager.flush();
        entityManager.clear();

        // Then
        ChatRoomJpaEntity deleted = chatRoomJpaRepo.findById(room.getId()).orElseThrow();
        List<?> activeMembers = chatRoomMemberJpaRepo.findActiveByRoomId(room.getId());

        assertThat(deleted.getStatus()).isEqualTo("DELETED");
        assertThat(activeMembers).isEmpty();
    }

    /**
     * 왜 통합 테스트가 필요한가:
     * - leave()는 멤버 퇴장 저장 후 countActiveByRoom()을 실행해 방 EMPTY 전환 여부를 결정
     * - 실제 DB 카운트 결과에 의존하므로 실제 환경 검증이 필수
     * <p>
     * 실패 가능 포인트:
     * - leave()의 saveAndFlush() 이전에 countActiveByRoom()이 실행되면
     * 카운트가 1로 남아 방 EMPTY 전환이 누락될 수 있음
     */
    @Test
    @DisplayName("마지막 멤버가 퇴장하면 방 상태가 EMPTY로 전환된다")
    void 마지막_멤버_퇴장_시_방_EMPTY_전환() {
        // Given
        ChatRoomJpaEntity room = fixture.createRoom("테스트방", "GAME", "ACTIVE", CREATOR_ID);
        fixture.createMember(room.getId(), CREATOR_ID, "JOINED");
        entityManager.flush();
        entityManager.clear();

        // When
        chatRoomMemberService.leave(room.getId(), CREATOR_ID);
        entityManager.flush();
        entityManager.clear();

        // Then
        ChatRoomJpaEntity updated = chatRoomJpaRepo.findById(room.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo("EMPTY");
    }

    /**
     * 왜 통합 테스트가 필요한가:
     * - join()에서 room.reactivate() 호출과 chatRoomRepo.save()가 동일 트랜잭션에서 처리됨을 검증
     * - EMPTY→ACTIVE 전환이 실제 DB에 반영되어야 이후 메시지 전송이 가능
     * <p>
     * 실패 가능 포인트:
     * - EMPTY 방 상태 확인 후 reactivate()와 save()가 다른 트랜잭션으로 분리되면
     * 방은 EMPTY 상태로 남고 멤버만 JOINED 상태가 되는 불일치 발생
     */
    @Test
    @DisplayName("EMPTY 방에 멤버가 입장하면 방이 ACTIVE로 복구된다")
    void EMPTY_방_입장_시_ACTIVE_복구() {
        // Given
        ChatRoomJpaEntity room = fixture.createRoom("빈방", "GAME", "EMPTY", CREATOR_ID);
        entityManager.flush();
        entityManager.clear();

        // When
        fixture.createMemberRecord(MEMBER_ID);
        chatRoomMemberService.join(room.getId(), MEMBER_ID);
        entityManager.flush();
        entityManager.clear();

        // Then
        ChatRoomJpaEntity updated = chatRoomJpaRepo.findById(room.getId()).orElseThrow();
        boolean isJoined = chatRoomMemberJpaRepo
                .existsByRoomIdAndMemberIdAndStatus(room.getId(), MEMBER_ID, "JOINED");

        assertThat(updated.getStatus()).isEqualTo("ACTIVE");
        assertThat(isJoined).isTrue();
    }
}
