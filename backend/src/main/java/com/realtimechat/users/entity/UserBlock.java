package com.realtimechat.users.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A directed block relationship.
 *
 * <p>Stored as its own row (not a flag on a conversation) because blocking must be enforced on
 * every interaction path — sending, starting a conversation, profile visibility, presence and
 * typing — regardless of whether a conversation exists yet. Enforcement is centralized in
 * {@code BlockQueryService}, which always checks <b>both</b> directions: the blocked user must not
 * be able to tell whether they were blocked.
 */
@Entity
@Table(name = "user_blocks")
@IdClass(UserBlock.Key.class)
public class UserBlock {

    @Id
    @Column(name = "blocker_id", nullable = false, updatable = false)
    private UUID blockerId;

    @Id
    @Column(name = "blocked_id", nullable = false, updatable = false)
    private UUID blockedId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected UserBlock() {
        // for JPA
    }

    public UserBlock(UUID blockerId, UUID blockedId) {
        if (blockerId.equals(blockedId)) {
            throw new IllegalArgumentException("A user cannot block themselves");
        }
        this.blockerId = blockerId;
        this.blockedId = blockedId;
    }

    public UUID getBlockerId() {
        return blockerId;
    }

    public UUID getBlockedId() {
        return blockedId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /** Composite primary key. */
    public static class Key implements Serializable {

        private UUID blockerId;
        private UUID blockedId;

        public Key() {
        }

        public Key(UUID blockerId, UUID blockedId) {
            this.blockerId = blockerId;
            this.blockedId = blockedId;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Key key)) {
                return false;
            }
            return Objects.equals(blockerId, key.blockerId) && Objects.equals(blockedId, key.blockedId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(blockerId, blockedId);
        }
    }
}
