package com.realtimechat.messaging.service;

import com.realtimechat.conversations.service.ReadReceiptPort;
import com.realtimechat.messaging.entity.Message;
import com.realtimechat.messaging.entity.MessageReceipt;
import com.realtimechat.messaging.repository.MessageReceiptRepository;
import com.realtimechat.messaging.repository.MessageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Implements the conversations module's {@link ReadReceiptPort} on top of the receipt table.
 *
 * <p>Every method converts the client-supplied message id into a <b>server-owned sequence
 * watermark</b> before touching rows. That is the key protection: a client cannot mark read state for
 * messages the server never recorded a receipt for, so read receipts cannot be forged for messages
 * that were never delivered.
 */
@Service
public class ReadReceiptAdapter implements ReadReceiptPort {

    private final MessageRepository messageRepository;
    private final MessageReceiptRepository receiptRepository;

    public ReadReceiptAdapter(MessageRepository messageRepository, MessageReceiptRepository receiptRepository) {
        this.messageRepository = messageRepository;
        this.receiptRepository = receiptRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean containsMessage(UUID conversationId, UUID messageId) {
        return messageRepository.findByIdWithinConversation(messageId, conversationId).isPresent();
    }

    @Override
    @Transactional
    public int markRead(UUID conversationId, UUID readerId, UUID lastMessageId, Instant readAt) {
        Long watermark = watermark(conversationId, readerId, lastMessageId);
        if (watermark == null) {
            return 0;
        }
        return receiptRepository.markReadUpTo(conversationId, readerId, watermark,
                MessageReceipt.ReceiptStatus.READ, readAt);
    }

    @Override
    @Transactional
    public int markDelivered(UUID conversationId, UUID recipientId, UUID lastMessageId, Instant deliveredAt) {
        Long watermark = watermark(conversationId, recipientId, lastMessageId);
        if (watermark == null) {
            return 0;
        }
        return receiptRepository.markDeliveredUpTo(conversationId, recipientId, watermark,
                MessageReceipt.ReceiptStatus.SENT, MessageReceipt.ReceiptStatus.DELIVERED, deliveredAt);
    }

    /**
     * Server-owned watermark for a client-supplied message id.
     *
     * @return the highest sequence ≤ the named message for which this user actually has a receipt,
     *         or {@code null} when the message is unknown in this conversation or was never delivered
     */
    private Long watermark(UUID conversationId, UUID userId, UUID lastMessageId) {
        Message message = messageRepository.findByIdWithinConversation(lastMessageId, conversationId).orElse(null);
        if (message == null || message.getSequence() == null) {
            return null;
        }
        return receiptRepository.maxReceiptSequenceUpTo(conversationId, userId, message.getSequence());
    }
}
