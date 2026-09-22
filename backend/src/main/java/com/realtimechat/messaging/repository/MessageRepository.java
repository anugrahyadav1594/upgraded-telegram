package com.realtimechat.messaging.repository;

import com.realtimechat.messaging.entity.Message;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Message history access.
 *
 * <p>Every query here is written to match an existing index exactly:
 * <ul>
 *   <li>history pages use {@code messages_conversation_created_idx} with a {@code (created_at, id)}
 *       keyset condition — never {@code OFFSET}, which degrades linearly with history depth and can
 *       skip or repeat rows when new messages arrive mid-pagination;</li>
 *   <li>resync uses {@code messages_conversation_sequence_idx} with {@code sequence > ?}.</li>
 * </ul>
 * Each method has a cursor and a cursor-less variant rather than an {@code is null} check in JPQL:
 * passing an untyped null into a comparison is a common source of runtime parameter-type errors, and
 * two explicit queries are also what the planner can optimise without guesswork.
 */
public interface MessageRepository extends JpaRepository<Message, UUID> {

    /** Idempotent send lookup: the retry of a send must find the stored message. */
    Optional<Message> findByConversationIdAndSenderIdAndClientMessageId(UUID conversationId, UUID senderId,
                                                                        UUID clientMessageId);

    List<Message> findByIdInAndConversationId(Collection<UUID> ids, UUID conversationId);

    long countByConversationId(UUID conversationId);

    /** Newest page of a conversation (no cursor). */
    @Query("""
            select m from Message m
            where m.conversationId = :conversationId
            order by m.createdAt desc, m.id desc
            """)
    List<Message> findLatestPage(@Param("conversationId") UUID conversationId, Pageable pageable);

    /** Older page: strictly before the cursor, ties broken by id so the order is total. */
    @Query("""
            select m from Message m
            where m.conversationId = :conversationId
              and (m.createdAt < :cursorCreatedAt
                   or (m.createdAt = :cursorCreatedAt and m.id < :cursorId))
            order by m.createdAt desc, m.id desc
            """)
    List<Message> findPageBefore(@Param("conversationId") UUID conversationId,
                                @Param("cursorCreatedAt") Instant cursorCreatedAt,
                                @Param("cursorId") UUID cursorId,
                                Pageable pageable);

    /** Newer page used by reconnect resync (ascending, so the client can append in order). */
    @Query("""
            select m from Message m
            where m.conversationId = :conversationId
              and (m.createdAt > :cursorCreatedAt
                   or (m.createdAt = :cursorCreatedAt and m.id > :cursorId))
            order by m.createdAt asc, m.id asc
            """)
    List<Message> findPageAfter(@Param("conversationId") UUID conversationId,
                               @Param("cursorCreatedAt") Instant cursorCreatedAt,
                               @Param("cursorId") UUID cursorId,
                               Pageable pageable);

    /**
     * Everything newer than a sequence watermark, in order.
     *
     * <p>This is the resync query: {@code sequence} is globally monotonic and gap-tolerant, so a
     * client that was offline for a while can request exactly what it missed without depending on
     * clock accuracy.
     */
    List<Message> findByConversationIdAndSequenceGreaterThanOrderBySequenceAsc(UUID conversationId, Long sequence,
                                                                              Pageable pageable);

    /** Most recent other message that is still replyable (used to reject dangling reply targets). */
    @Query("""
            select m from Message m
            where m.id = :id and m.conversationId = :conversationId
            """)
    Optional<Message> findByIdWithinConversation(@Param("id") UUID id,
                                                @Param("conversationId") UUID conversationId);

    /** Highest sequence currently stored in a conversation (watermark for a fresh client). */
    @Query("select max(m.sequence) from Message m where m.conversationId = :conversationId")
    Long findMaxSequence(@Param("conversationId") UUID conversationId);

    /** Retention/erasure: anonymises the sender of every message written by a deleted account. */
    @org.springframework.data.jpa.repository.Modifying
    @Query("""
            update Message m set m.senderId = :tombstoneId
            where m.senderId = :userId
            """)
    int anonymiseSender(@Param("userId") UUID userId, @Param("tombstoneId") UUID tombstoneId);
}
