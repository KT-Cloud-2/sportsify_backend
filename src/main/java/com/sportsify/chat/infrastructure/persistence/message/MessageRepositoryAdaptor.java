package com.sportsify.chat.infrastructure.persistence.message;

import com.sportsify.chat.domain.model.chatRoom.ChatRoomId;
import com.sportsify.chat.domain.model.chatRoom.MemberId;
import com.sportsify.chat.domain.model.message.Message;
import com.sportsify.chat.domain.model.message.MessageId;
import com.sportsify.chat.domain.model.message.MessageStatus;
import com.sportsify.chat.domain.repository.MessageRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.stream.Collectors;

/**
 * MessageRepository(도메인 포트) 의 JPA 어댑터 구현체.
 */
@Slf4j
@Repository
public class MessageRepositoryAdaptor implements MessageRepository {

    private final MessageJpaRepository jpaRepository;
    private final MessageMapper mapper;
    private final JdbcTemplate jdbcTemplate;

    public MessageRepositoryAdaptor(MessageJpaRepository jpaRepository,
                                    MessageMapper mapper,
                                    JdbcTemplate jdbcTemplate) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Message save(Message message) {
        if (message.getId() == null) {
            MessageJpaEntity saved = jpaRepository.save(mapper.toNewJpaEntity(message));
            message.assignId(MessageId.of(saved.getId()));
            return message;
        }
        Long id = message.getId().value();
        MessageJpaEntity entity = jpaRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException("Message not found for update: id=" + id));
        mapper.applyToJpa(entity, message);
        jpaRepository.save(entity);
        return message;
    }

    @Override
    public Optional<Message> findById(MessageId id) {
        return jpaRepository.findById(id.value()).map(mapper::toDomain);
    }

    @Override
    public Optional<Message> findByIdForUpdate(MessageId id) {
        return jpaRepository.findByIdForUpdate(id.value()).map(mapper::toDomain);
    }

    @Override
    public List<Message> findByRoomBefore(ChatRoomId roomId, Long beforeMessageId, int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        PageRequest pageable = PageRequest.of(0, limit);
        List<MessageJpaEntity> rows;
        if (beforeMessageId == null) {
            rows = jpaRepository.findLatest(roomId.value(), pageable);
        } else {
            rows = jpaRepository.findBefore(roomId.value(), beforeMessageId, pageable);
        }
        return rows.stream().map(mapper::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<Message> findByRoomAndMemberBefore(ChatRoomId roomId, MemberId memberId, Long beforeMessageId, int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        PageRequest pageable = PageRequest.of(0, limit);
        List<MessageJpaEntity> rows;
        log.info(roomId.value() + " " + memberId.value() + " " + beforeMessageId);
        if (beforeMessageId == null) {
            rows = jpaRepository.findLatestBySenderAndRoom(roomId.value(), memberId.value(), pageable);
        } else {
            rows = jpaRepository.findBeforeBySenderAndRoom(roomId.value(), memberId.value(), beforeMessageId, pageable);
        }
        return rows.stream().map(mapper::toDomain).collect(Collectors.toList());
    }

    @Override
    public long countAfter(ChatRoomId roomId, Long afterMessageId) {
        if (afterMessageId == null) {
            return jpaRepository.countAll(roomId.value());
        }
        return jpaRepository.countAfter(roomId.value(), afterMessageId);
    }

    @Override
    public List<Message> findMyLatestByRooms(List<ChatRoomId> roomIds, MemberId memberId) {
        return jpaRepository.findMyLatestByRooms(roomIds.stream().map(ChatRoomId::value).toList(), memberId.value())
                .stream().map(mapper::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<Message> findLatestByRooms(List<ChatRoomId> roomIds) {
        return jpaRepository.findLatestByRooms(roomIds.stream().map(ChatRoomId::value).toList())
                .stream().map(mapper::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<Message> findByRoomAfter(ChatRoomId roomId, Long afterMessageId, int limit) {
        if (limit <= 0) throw new IllegalArgumentException("limit must be positive");
        return jpaRepository.findAfter(roomId.value(), afterMessageId, PageRequest.of(0, limit))
                .stream().map(mapper::toDomain).collect(Collectors.toList());
    }

    @Override
    public Map<ChatRoomId, Long> countUnreadByRooms(Map<ChatRoomId, Long> lastReadMap) {
        if (lastReadMap.isEmpty()) return Map.of();

        StringJoiner unionParts = new StringJoiner(" UNION ALL ");
        List<Object> params = new ArrayList<>();

        lastReadMap.forEach((roomId, lastReadId) -> {
            unionParts.add("SELECT ? AS room_id, ? AS last_read_id");
            params.add(roomId.value());
            params.add(lastReadId != null ? lastReadId : 0L);
        });

        String sql = """
                SELECT c.room_id, COUNT(*) AS cnt
                FROM chat_messages c
                INNER JOIN (%s) t
                    ON c.room_id = t.room_id
                   AND c.id > t.last_read_id
                WHERE c.status = ?
                GROUP BY c.room_id
                """.formatted(unionParts);

        params.add(MessageStatus.ACTIVE.name());

        return jdbcTemplate.query(sql, params.toArray(), rs -> {
            Map<ChatRoomId, Long> result = new HashMap<>();
            while (rs.next()) {
                result.put(ChatRoomId.of(rs.getLong("room_id")), rs.getLong("cnt"));
            }
            return result;
        });
    }

}
