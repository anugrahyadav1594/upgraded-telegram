package com.realtimechat.messaging.service;

import com.realtimechat.messaging.entity.Message;
import com.realtimechat.messaging.repository.MessageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Read-only lookups about messages, for modules that need to resolve a target id without touching the
 * messaging schema.
 *
 * <p>The moderation module needs "which conversation does this message belong to?" to decide whether
 * the reporter is allowed to report it. Exposing that question here — rather than letting another
 * module read the {@code messages} table — keeps the schema owned by one module and makes the
 * dependency direction explicit (moderation → messaging, never the reverse).
 */
@Service
public class MessageLookupService {

    private final MessageRepository messageRepository;

    public MessageLookupService(MessageRepository messageRepository) {
        this.messageRepository = messageRepository;
    }

    @Transactional(readOnly = true)
    public Optional<UUID> conversationOf(UUID messageId) {
        return messageRepository.findById(messageId).map(Message::getConversationId);
    }

    @Transactional(readOnly = true)
    public Optional<UUID> senderOf(UUID messageId) {
        return messageRepository.findById(messageId).map(Message::getSenderId);
    }

    @Transactional(readOnly = true)
    public boolean exists(UUID messageId) {
        return messageRepository.existsById(messageId);
    }
}
