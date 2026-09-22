package com.realtimechat.messaging.repository;

import com.realtimechat.messaging.entity.MessageReaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Reaction persistence.
 *
 * <p>Toggling is a delete-or-insert on a composite key, so two concurrent toggles converge on a
 * consistent state instead of racing on a boolean column. Aggregation happens in one batched query
 * per page of messages, never per message.
 */
public interface MessageReactionRepository extends JpaRepository<MessageReaction, MessageReaction.Key> {

    List<MessageReaction> findByMessageIdIn(Collection<UUID> messageIds);

    @Modifying
    @Query("""
            delete from MessageReaction r
            where r.messageId = :messageId and r.userId = :userId and r.emoji = :emoji
            """)
    int remove(@Param("messageId") UUID messageId, @Param("userId") UUID userId, @Param("emoji") String emoji);

    @Modifying
    @Query("""
            delete from MessageReaction r
            where r.messageId = :messageId and r.userId = :userId
            """)
    int removeAll(@Param("messageId") UUID messageId, @Param("userId") UUID userId);
}
