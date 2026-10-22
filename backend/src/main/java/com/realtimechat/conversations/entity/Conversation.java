package com.realtimechat.conversations.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A conversation (today always one-to-one).
 *
 * <p>Modelling notes:
 * <ul>
 *   <li><b>Identity is the participant pair, not a generated key.</b> {@code direct_key} is the
 *       canonical {@code least(uuid):greatest(uuid)} string with a unique index behind it, so two
 *       simultaneous "start a chat" requests converge on one row: the second insert violates the
 *       index and the service re-reads. Idempotency is therefore a database guarantee, not a
 *       best-effort check.</li>
 *   <li><b>Only a message pointer is denormalised.</b> {@code last_message_id} and
 *       {@code last_activity_at} let the conversation list order and render without a correlated
 *       subquery per row. The preview text is not duplicated here: one primary-key lookup for the
 *       page's last messages is cheaper than keeping a second copy of user content in sync.</li>
 *   <li><b>Per-user state lives elsewhere.</b> Read cursor, mute, archive and "delete for me" are on
 *       {@link ConversationParticipant}, because the two participants disagree about all of them.</li>
 * </ul>
 */
@Entity
@Table(name = "conversations")
public class Conversation {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 16)
    private ConversationType type = ConversationType.DIRECT;

    @Column(name = "direct_key", length = 73)
    private String directKey;

    @Column(name = "title", length = 128)
    private String title;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "last_message_id")
    private UUID lastMessageId;

    @Column(name = "last_activity_at", nullable = false)
    private Instant lastActivityAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "conversation", fetch = FetchType.LAZY, cascade = CascadeType.ALL,
            orphanRemoval = true)
    private List<ConversationParticipant> participants = new ArrayList<>();

    protected Conversation() {
        // for JPA
    }

    /**
     * Creates a DIRECT conversation between two distinct users.
     *
     * <p>{@code createdBy} records who initiated it (useful for abuse review) while the canonical key
     * keeps the row identical from either direction.
     */
    public static Conversation createDirect(UUID requesterId, UUID otherUserId) {
        if (requesterId.equals(otherUserId)) {
            throw new IllegalArgumentException("A conversation requires two distinct users");
        }
        Conversation conversation = new Conversation();
        conversation.id = UUID.randomUUID();
        conversation.type = ConversationType.DIRECT;
        conversation.directKey = directKeyFor(requesterId, otherUserId);
        conversation.createdBy = requesterId;
        conversation.lastActivityAt = Instant.now();
        conversation.addParticipants(requesterId, otherUserId);
        return conversation;
    }

    /**
     * Canonical, order-independent key for a pair of users.
     *
     * <p>Sorting the UUIDs is what makes the key stable: {@code (A,B)} and {@code (B,A)} must produce
     * the same value or the unique index would happily accept duplicates.
     */
    public static String directKeyFor(UUID first, UUID second) {
        return first.compareTo(second) < 0
                ? first + ":" + second
                : second + ":" + first;
    }

    public void addParticipants(UUID... userIds) {
        for (UUID userId : userIds) {
            participants.add(new ConversationParticipant(this, userId));
        }
    }

    /**
     * Records the newest message as the conversation's last activity.
     *
     * <p>Called inside the same transaction that inserts the message, so the conversation list can
     * never point at a message that was not committed.
     */
    public void recordMessage(UUID messageId, Instant sentAt) {
        this.lastMessageId = messageId;
        this.lastActivityAt = sentAt;
    }

    /** Clears the pointer when the referenced message is deleted, keeping the conversation visible. */
    public void clearLastMessage() {
        this.lastMessageId = null;
    }

    public boolean involves(UUID userId) {
        for (ConversationParticipant participant : participants) {
            if (participant.getUserId().equals(userId)) {
                return true;
            }
        }
        return false;
    }

    public UUID getId() {
        return id;
    }

    public ConversationType getType() {
        return type;
    }

    public String getDirectKey() {
        return directKey;
    }

    public String getTitle() {
        return title;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public UUID getLastMessageId() {
        return lastMessageId;
    }

    public Instant getLastActivityAt() {
        return lastActivityAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public List<ConversationParticipant> getParticipants() {
        return participants;
    }
}
