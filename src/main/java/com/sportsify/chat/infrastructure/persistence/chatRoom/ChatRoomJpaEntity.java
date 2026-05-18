package com.sportsify.chat.infrastructure.persistence.chatRoom;


import jakarta.persistence.*;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * chat_rooms 테이블 매핑 영속 엔티티.
 */
@Getter
@Entity
@Table(name = "chat_rooms",
        indexes = {
                @Index(name = "idx_chat_rooms_game_id", columnList = "game_id")
        })
public class ChatRoomJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "type", nullable = false, length = 20)
    private String type;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "game_id")
    private Long gameId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    protected ChatRoomJpaEntity() {
        // for JPA
    }

    public ChatRoomJpaEntity(Long id,
                             String name,
                             String type,
                             String imageUrl,
                             Long gameId,
                             LocalDateTime createdAt,
                             LocalDateTime updatedAt,
                             String status,
                             Long createdBy) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.imageUrl = imageUrl;
        this.gameId = gameId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.status = status;
        this.createdBy = createdBy;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setType(String type) {
        this.type = type;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public void setGameId(Long gameId) {
        this.gameId = gameId;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }
}