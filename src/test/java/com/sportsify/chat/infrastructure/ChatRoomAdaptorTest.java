package com.sportsify.chat.infrastructure;

import com.sportsify.chat.domain.model.chatRoom.*;
import com.sportsify.chat.infrastructure.persistence.chatRoom.ChatRoomAdaptor;
import com.sportsify.chat.infrastructure.persistence.chatRoom.ChatRoomJpaRepository;
import com.sportsify.member.domain.model.Member;
import com.sportsify.member.domain.model.OAuthProvider;
import com.sportsify.member.infrastructure.repository.MemberJpaRepository;
import com.sportsify.support.RepositoryTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.InvalidDataAccessApiUsageException;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("[단위] ChatRoomAdaptor save() update 경로 회귀 테스트")
class ChatRoomAdaptorTest extends RepositoryTestSupport {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 5, 6, 12, 0);

    @Autowired
    private ChatRoomAdaptor adaptor;

    @Autowired
    private ChatRoomJpaRepository jpaRepository;

    @Autowired
    private MemberJpaRepository memberJpaRepository;

    private MemberId creatorId;

    @BeforeEach
    void setUp() {
        Member member = memberJpaRepository.save(
                Member.create("cr@test.com", "방장", OAuthProvider.GOOGLE, "g-cr"));
        creatorId = MemberId.of(member.getId());
    }

    @Test
    @DisplayName("id가 없는 ChatRoom을 save하면 INSERT되고 id가 부여된다")
    void save_신규_insert() {
        ChatRoom chatRoom = ChatRoom.create(
                ChatRoomName.of("원래 이름"), ChatRoomType.DIRECT,
                null, null, creatorId, NOW);

        adaptor.save(chatRoom);

        assertThat(chatRoom.getId()).isNotNull();
        assertThat(jpaRepository.findById(chatRoom.getId().value())).isPresent();
    }

    @Test
    @DisplayName("id가 있는 ChatRoom을 save하면 변경된 이름이 DB에 반영된다")
    void save_기존_update_이름_변경() {
        ChatRoom chatRoom = ChatRoom.create(
                ChatRoomName.of("원래 이름"), ChatRoomType.DIRECT,
                null, null, creatorId, NOW);
        adaptor.save(chatRoom);

        chatRoom.rename(ChatRoomName.of("변경된 이름"), NOW.plusMinutes(1), creatorId);
        adaptor.save(chatRoom);

        String savedName = jpaRepository.findById(chatRoom.getId().value())
                .orElseThrow().getName();
        assertThat(savedName).isEqualTo("변경된 이름");
    }

    @Test
    @DisplayName("DB에 존재하지 않는 id로 update를 시도하면 예외를 던진다")
    void save_존재하지_않는_id로_update_예외() {
        ChatRoom chatRoom = ChatRoom.restore(
                ChatRoomId.of(99999L), ChatRoomName.of("유령 방"), ChatRoomType.DIRECT,
                null, null, NOW, NOW, ChatRoomStatus.ACTIVE, creatorId);

        assertThatThrownBy(() -> adaptor.save(chatRoom))
                .isInstanceOf(InvalidDataAccessApiUsageException.class)
                .hasMessageContaining("ChatRoom not found for update");
    }

    @Test
    @DisplayName("id가 있는 ChatRoom을 save하면 변경된 상태가 DB에 반영된다")
    void save_기존_update_상태_변경() {
        ChatRoom chatRoom = ChatRoom.create(
                ChatRoomName.of("테스트 방"), ChatRoomType.DIRECT,
                null, null, creatorId, NOW);
        adaptor.save(chatRoom);

        chatRoom.archive(NOW.plusMinutes(1));
        adaptor.save(chatRoom);

        String savedStatus = jpaRepository.findById(chatRoom.getId().value())
                .orElseThrow().getStatus();
        assertThat(savedStatus).isEqualTo("ARCHIVED");
    }
}
