package com.realtimechat.conversations.service;

import java.time.Instant;
import java.util.UUID;

/**
 * Write-side port for message read state, implemented by the messaging module.
 *
 * <p>Read state is stored in two places on purpose: the participant row keeps the coarse cursor used
 * to render lists, and {@code message_receipts} keeps the per-message truth that the sender's ticks
 * and the unread badge are derived from. Both must move together — a cursor that advanced without the
 * receipts would leave the sender on a single tick forever — so this port is called inside the
 * <b>same transaction</b> that updates the cursor, and the implementation participates in it.
 */
public interface ReadReceiptPort {

    /**
     * True when the message exists and belongs to the conversation.
     *
     * <p>Exposed through this port because the read endpoint must validate a client-supplied message
     * id, and that validation needs messaging's data — which the conversations module must not read
     * directly without creating a dependency cycle.
     */
    boolean containsMessage(UUID conversationId, UUID messageId);

    /**
     * Marks every unread receipt at or before {@code lastMessageId} as read for this reader.
     *
     * @return the number of receipts whose state changed (0 when everything was already read), so the
     *         caller can skip broadcasting an event that would tell nobody anything new
     */
    int markRead(UUID conversationId, UUID readerId, UUID lastMessageId, Instant readAt);

    /**
     * Marks every receipt at or before {@code lastMessageId} as delivered for this reader.
     *
     * <p>Delivery is the middle tick: it means "reached the device", not "the human saw it".
     */
    int markDelivered(UUID conversationId, UUID recipientId, UUID lastMessageId, Instant deliveredAt);
}
