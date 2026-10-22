package com.realtimechat.conversations.repository;

import com.realtimechat.conversations.entity.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Conversation persistence.
 *
 * <p>Deliberately tiny: list queries live in {@link ConversationParticipantRepository}, because a
 * user's list is a participant-scoped query and putting it here would require a join from the wrong
 * side of the association.
 */
public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    /**
     * Finds the DIRECT conversation for a pair.
     *
     * <p>The key is canonical ({@code least:greatest}), so this is a single unique-index lookup and
     * the order in which the two users are named does not matter.
     */
    Optional<Conversation> findByDirectKey(String directKey);
}
