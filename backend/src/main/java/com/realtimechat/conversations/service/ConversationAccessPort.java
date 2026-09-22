package com.realtimechat.conversations.service;

import java.util.Set;
import java.util.UUID;

/**
 * Membership view of conversations, exposed to modules that need to authorise an action without
 * knowing anything about how conversations are stored.
 *
 * <p>Used by the realtime layer (may this socket subscribe to this conversation?) and by messaging
 * (may this user send into this conversation?). Both are authorisation questions, so they must be
 * answered from persisted state — never from a client-supplied participant list.
 */
public interface ConversationAccessPort {

    /** True when the user is an active participant. Unknown conversations return false. */
    boolean isParticipant(UUID conversationId, UUID userId);

    /**
     * The other participant of a one-to-one conversation.
     *
     * @return empty when the user is not a participant, or the conversation has no single counterpart
     */
    java.util.Optional<UUID> counterpartOf(UUID conversationId, UUID userId);

    Set<UUID> participantIds(UUID conversationId);
}
