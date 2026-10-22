package com.realtimechat.messaging.service;

import com.realtimechat.common.util.PreviewFormatter;
import com.realtimechat.conversations.service.MessagePreviewPort;
import com.realtimechat.messaging.entity.Message;
import com.realtimechat.messaging.repository.MessageReceiptRepository;
import com.realtimechat.messaging.repository.MessageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Implements the conversations module's {@link MessagePreviewPort}.
 *
 * <p>Two things are deliberately not done here:
 * <ul>
 *   <li><b>No content for deleted messages.</b> A tombstone previews as deleted, never as its former
 *       text — the erased content must not survive in a list payload.</li>
 *   <li><b>No unread counter column.</b> Unread counts are counted from receipts at read time, so a
 *       badge can never drift from the delivery state it is supposed to describe.</li>
 * </ul>
 */
@Service
public class MessagePreviewAdapter implements MessagePreviewPort {

    private final MessageRepository messageRepository;
    private final MessageReceiptRepository receiptRepository;
    private final PreviewFormatter previewFormatter;

    public MessagePreviewAdapter(MessageRepository messageRepository,
                                 MessageReceiptRepository receiptRepository,
                                 PreviewFormatter previewFormatter) {
        this.messageRepository = messageRepository;
        this.receiptRepository = receiptRepository;
        this.previewFormatter = previewFormatter;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, MessagePreview> previewsOf(Collection<UUID> messageIds) {
        if (messageIds == null || messageIds.isEmpty()) {
            return Map.of();
        }
        List<Message> messages = messageRepository.findAllById(messageIds);
        Map<UUID, MessagePreview> previews = new HashMap<>(messages.size());
        for (Message message : messages) {
            previews.put(message.getId(), new MessagePreview(
                    message.getId(),
                    message.getSenderId(),
                    message.isDeleted() ? null : previewFormatter.preview(message.getContent()),
                    message.getMessageType().name(),
                    message.getCreatedAt(),
                    message.isDeleted(),
                    message.isEdited()));
        }
        return previews;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, Integer> unreadCounts(UUID userId, Collection<UUID> conversationIds) {
        if (conversationIds == null || conversationIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Integer> counts = new HashMap<>(conversationIds.size());
        for (Object[] row : receiptRepository.countUnreadByConversation(userId, conversationIds)) {
            counts.put((UUID) row[0], ((Number) row[1]).intValue());
        }
        return counts;
    }

    @Override
    @Transactional(readOnly = true)
    public int totalUnread(UUID userId) {
        return (int) Math.min(Integer.MAX_VALUE, receiptRepository.countUnreadTotal(userId));
    }
}
