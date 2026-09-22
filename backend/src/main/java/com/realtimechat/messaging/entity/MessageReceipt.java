package com.realtimechat.messaging.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Per-recipient delivery state of a message: {@code SENT → DELIVERED → READ}.
 *
 * <p>The server, not the client, is authoritative: the row exists from the moment the message is
 * committed, and each transition is a guarded UPDATE (see
 * {@code MessageReceiptRepository.markDelivered/markRead}) whose {@code WHERE} clause refuses to move
 * a receipt backwards. A replayed or spoofed acknowledgement therefore cannot un-read a message.
 *
 * <p>{@code @IdClass} is used here rather than {@code @EmbeddedId} because the key is two plain
 * UUID columns with no association to map — see {@link com.realtimechat.conversations.entity
 * .ConversationParticipant} for the case where an embedded id is needed instead.
 */
@Entity
@Table(name = "message_receipts")
@IdClass(MessageReceipt.Key.class)
public class MessageReceipt {

    @Id
    @Column(name = "message_id", nullable = false, updatable = false)
    private UUID messageId;

    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private ReceiptStatus status = ReceiptStatus.SENT;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "read_at")
    private Instant readAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected MessageReceipt() {
        // for JPA
    }

    public static MessageReceipt sent(UUID messageId, UUID recipientId) {
        MessageReceipt receipt = new MessageReceipt();
        receipt.messageId = messageId;
        receipt.userId = recipientId;
        receipt.status = ReceiptStatus.SENT;
        return receipt;
    }

    public UUID getMessageId() {
        return messageId;
    }

    public UUID getUserId() {
        return userId;
    }

    public ReceiptStatus getStatus() {
        return status;
    }

    public Instant getDeliveredAt() {
        return deliveredAt;
    }

    public Instant getReadAt() {
        return readAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /** Receipt states, ordered by progress: a client shows the highest state reached. */
    public enum ReceiptStatus {
        SENT,
        DELIVERED,
        READ
    }

    /** Composite key {@code (messageId, userId)}. */
    public static class Key implements Serializable {

        private UUID messageId;
        private UUID userId;

        public Key() {
        }

        public Key(UUID messageId, UUID userId) {
            this.messageId = messageId;
            this.userId = userId;
        }

        public UUID getMessageId() {
            return messageId;
        }

        public UUID getUserId() {
            return userId;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Key key)) {
                return false;
            }
            return Objects.equals(messageId, key.messageId) && Objects.equals(userId, key.userId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(messageId, userId);
        }
    }
}
