package com.realtimechat.conversations.repository;

import com.realtimechat.conversations.entity.ConversationParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Participant-scoped queries: membership, the conversation list and read cursors.
 *
 * <p>All list access is keyset-paginated on {@code (last_activity_at, conversation_id)} — the exact
 * order of {@code conversations_last_activity_idx} — so page <em>n</em> costs the same as page 1 and
 * no {@code OFFSET} is ever executed.
 */
public interface ConversationParticipantRepository extends JpaRepository<ConversationParticipant, ConversationParticipant.Key> {

    Optional<ConversationParticipant> findByIdConversationIdAndIdUserId(UUID conversationId, UUID userId);

    boolean existsByIdConversationIdAndIdUserId(UUID conversationId, UUID userId);

    List<ConversationParticipant> findAllByIdConversationId(UUID conversationId);

    /** Batch peer lookup for a page of conversations (one query instead of one per conversation). */
    List<ConversationParticipant> findAllByIdConversationIdIn(java.util.Collection<UUID> conversationIds);

    List<ConversationParticipant> findAllByIdUserId(UUID userId);

    @Query("select cp.id.userId from ConversationParticipant cp where cp.id.conversationId = :conversationId")
    Set<UUID> findUserIdsByConversationId(@Param("conversationId") UUID conversationId);

    /**
     * First page of a user's conversation list.
     *
     * <p>Written as an explicit join predicate rather than an association traversal because the
     * ordering column ({@code last_activity_at}) lives on {@code conversations} while the filter
     * ({@code user_id}, {@code deleted_at}) lives on the participant row.
     */
    @Query("""
            select cp from ConversationParticipant cp, Conversation c
            where c.id = cp.id.conversationId
              and cp.id.userId = :userId
              and cp.deletedAt is null
              and (:archived is null or cp.archived = :archived)
            order by c.lastActivityAt desc, c.id desc
            """)
    List<ConversationParticipant> findPageByUser(@Param("userId") UUID userId,
                                                @Param("archived") Boolean archived,
                                                org.springframework.data.domain.Pageable pageable);

    /** Keyset continuation: strictly older activity than the cursor, or same instant with a lower id. */
    @Query("""
            select cp from ConversationParticipant cp, Conversation c
            where c.id = cp.id.conversationId
              and cp.id.userId = :userId
              and cp.deletedAt is null
              and (:archived is null or cp.archived = :archived)
              and (c.lastActivityAt < :cursorActivity
                   or (c.lastActivityAt = :cursorActivity and c.id < :cursorId))
            order by c.lastActivityAt desc, c.id desc
            """)
    List<ConversationParticipant> findPageByUserAfter(@Param("userId") UUID userId,
                                                     @Param("archived") Boolean archived,
                                                     @Param("cursorActivity") Instant cursorActivity,
                                                     @Param("cursorId") UUID cursorId,
                                                     org.springframework.data.domain.Pageable pageable);

    /**
     * Revives a conversation for a participant.
     *
     * <p>Used when a new message arrives for a conversation the user had deleted: the other
     * participant keeps their copy, and the message must be visible to both. Done as a bulk UPDATE so
     * the send path does not need to load the participant row.
     */
    @Modifying
    @Query("""
            update ConversationParticipant cp set cp.deletedAt = null
            where cp.id.conversationId = :conversationId and cp.deletedAt is not null
            """)
    int reviveForConversation(@Param("conversationId") UUID conversationId);

    /** Removes the participant rows when a conversation itself is deleted (retention/erasure). */
    @Modifying
    @Query("delete from ConversationParticipant cp where cp.id.conversationId = :conversationId")
    int deleteByConversationId(@Param("conversationId") UUID conversationId);

    /** Conversations the user participates in, used for presence fan-out audiences. */
    @Query("""
            select distinct other.id.userId from ConversationParticipant other
            where other.id.conversationId in (
                select mine.id.conversationId from ConversationParticipant mine
                where mine.id.userId = :userId
            ) and other.id.userId <> :userId
            """)
    Set<UUID> findCounterpartIds(@Param("userId") UUID userId);
}
