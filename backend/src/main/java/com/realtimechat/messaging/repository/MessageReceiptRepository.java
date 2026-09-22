package com.realtimechat.messaging.repository;

import com.realtimechat.messaging.entity.MessageReceipt;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Server-authoritative delivery state.
 *
 * <p>Two rules are enforced by the shape of these queries rather than by service logic:
 * <ul>
 *   <li><b>Monotonic.</b> Every transition is a conditional UPDATE filtered on the current status
 *       ({@code status = 'SENT'} for delivery, {@code read_at is null} for reading), so a replayed or
 *       duplicated acknowledgement cannot move a receipt backwards.</li>
 *   <li><b>Scoped to a conversation and a watermark.</b> Read state advances up to a
 *       {@code sequence} value, not up to "whatever the client sent", so a client cannot mark
 *       messages it was never delivered.</li>
 * </ul>
 */
public interface MessageReceiptRepository extends JpaRepository<MessageReceipt, MessageReceipt.Key> {

    List<MessageReceipt> findByMessageIdIn(Collection<UUID> messageIds);

    /** The recipient's own receipt for one message (drives the tick in a message response). */
    @Query("""
            select r from MessageReceipt r
            where r.messageId = :messageId and r.userId = :userId
            """)
    java.util.Optional<MessageReceipt> findForUser(@Param("messageId") UUID messageId,
                                                   @Param("userId") UUID userId);

    /**
     * Advances receipts to DELIVERED up to an inclusive sequence watermark.
     *
     * @return number of rows actually changed (0 when everything was already delivered)
     */
    @Modifying(clearAutomatically = false, flushAutomatically = true)
    @Query("""
            update MessageReceipt r set r.status = :status, r.deliveredAt = :now
            where r.userId = :userId
              and r.status = :fromStatus
              and r.messageId in (
                  select m.id from Message m
                  where m.conversationId = :conversationId and m.sequence <= :maxSequence
              )
            """)
    int markDeliveredUpTo(@Param("conversationId") UUID conversationId,
                          @Param("userId") UUID userId,
                          @Param("maxSequence") Long maxSequence,
                          @Param("fromStatus") MessageReceipt.ReceiptStatus fromStatus,
                          @Param("status") MessageReceipt.ReceiptStatus status,
                          @Param("now") Instant now);

    /** Advances receipts to READ up to an inclusive sequence watermark. */
    @Modifying(clearAutomatically = false, flushAutomatically = true)
    @Query("""
            update MessageReceipt r set r.status = :status, r.readAt = :now,
                                        r.deliveredAt = coalesce(r.deliveredAt, :now)
            where r.userId = :userId
              and r.readAt is null
              and r.messageId in (
                  select m.id from Message m
                  where m.conversationId = :conversationId and m.sequence <= :maxSequence
              )
            """)
    int markReadUpTo(@Param("conversationId") UUID conversationId,
                     @Param("userId") UUID userId,
                     @Param("maxSequence") Long maxSequence,
                     @Param("status") MessageReceipt.ReceiptStatus status,
                     @Param("now") Instant now);

    /** Unread counts per conversation, in one grouped query (never one query per row). */
    @Query("""
            select m.conversationId, count(r) from MessageReceipt r, Message m
            where r.messageId = m.id
              and r.userId = :userId
              and r.readAt is null
              and m.deletedAt is null
              and m.conversationId in :conversationIds
            group by m.conversationId
            """)
    List<Object[]> countUnreadByConversation(@Param("userId") UUID userId,
                                             @Param("conversationIds") Collection<UUID> conversationIds);

    @Query("""
            select count(r) from MessageReceipt r, Message m
            where r.messageId = m.id
              and r.userId = :userId
              and r.readAt is null
              and m.deletedAt is null
            """)
    long countUnreadTotal(@Param("userId") UUID userId);

    /** Newest acknowledgements, used by the delivery endpoint to advance a watermark. */
    @Query("""
            select r from MessageReceipt r, Message m
            where r.messageId = m.id and r.userId = :userId and m.conversationId = :conversationId
            order by m.sequence desc
            """)
    List<MessageReceipt> findNewestForUser(@Param("conversationId") UUID conversationId,
                                           @Param("userId") UUID userId,
                                           Pageable pageable);

    /**
     * Highest sequence the recipient has actually received a receipt for.
     *
     * <p>Read/delivery endpoints accept a client-supplied message id, and this is what turns it into
     * a server-owned watermark: the client can only ever acknowledge messages that were really
     * delivered to it.
     */
    @Query("""
            select max(m.sequence) from MessageReceipt r, Message m
            where r.messageId = m.id and r.userId = :userId and m.conversationId = :conversationId
              and m.sequence <= :maxSequence
            """)
    Long maxReceiptSequenceUpTo(@Param("conversationId") UUID conversationId,
                                @Param("userId") UUID userId,
                                @Param("maxSequence") Long maxSequence);
}
