package com.realtimechat.messaging.service;

import com.realtimechat.common.error.BlockedException;
import com.realtimechat.common.error.ForbiddenException;
import com.realtimechat.common.error.InvalidRequestException;
import com.realtimechat.common.error.NotFoundException;
import com.realtimechat.common.error.RateLimitExceededException;
import com.realtimechat.common.error.UnsupportedMessageTypeException;
import com.realtimechat.common.ratelimit.RateLimitDecision;
import com.realtimechat.common.ratelimit.RateLimiter;
import com.realtimechat.common.util.EmojiCatalog;
import com.realtimechat.common.util.PreviewFormatter;
import com.realtimechat.common.util.TextNormalizer;
import com.realtimechat.common.util.TimeProvider;
import com.realtimechat.config.AppProperties;
import com.realtimechat.conversations.entity.Conversation;
import com.realtimechat.conversations.entity.ConversationParticipant;
import com.realtimechat.conversations.event.ConversationUpdatedEvent;
import com.realtimechat.conversations.repository.ConversationParticipantRepository;
import com.realtimechat.conversations.repository.ConversationRepository;
import com.realtimechat.conversations.service.ConversationAccessPort;
import com.realtimechat.messaging.dto.ChatMessageResponse;
import com.realtimechat.messaging.entity.Message;
import com.realtimechat.messaging.entity.MessageReaction;
import com.realtimechat.messaging.entity.MessageReceipt;
import com.realtimechat.messaging.entity.MessageType;
import com.realtimechat.messaging.event.MessageAckEvent;
import com.realtimechat.messaging.event.MessageCommittedEvent;
import com.realtimechat.messaging.event.MessageDeletedEvent;
import com.realtimechat.messaging.event.MessageDeliveredEvent;
import com.realtimechat.messaging.event.MessageNewEvent;
import com.realtimechat.messaging.event.MessageReactionUpdatedEvent;
import com.realtimechat.messaging.event.MessageUpdatedEvent;
import com.realtimechat.messaging.mapper.MessageMapper;
import com.realtimechat.messaging.repository.MessageReactionRepository;
import com.realtimechat.messaging.repository.MessageReceiptRepository;
import com.realtimechat.messaging.repository.MessageRepository;
import com.realtimechat.users.service.BlockService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Message writes: send, edit, delete, react, acknowledge.
 *
 * <p>The order of operations in {@link #send} is the whole design, so it is spelled out:
 * <ol>
 *   <li><b>Authorise</b> — membership comes from the database, not from the payload.</li>
 *   <li><b>Resolve idempotency</b> — a known {@code clientMessageId} returns the stored message, so a
 *       retry can never duplicate.</li>
 *   <li><b>Rate limit</b> — per user, per minute and per day, before any write happens.</li>
 *   <li><b>Validate</b> — content bounds and reply target (the parent must be in the same
 *       conversation).</li>
 *   <li><b>Persist</b> — message, receipts and the conversation's last-message pointer in one
 *       transaction.</li>
 *   <li><b>Announce</b> — events are published as domain events that the broadcaster delivers
 *       <em>after commit</em>. Nothing is ever announced that is not durable.</li>
 * </ol>
 */
@Service
public class MessageService {

    private static final Logger log = LoggerFactory.getLogger(MessageService.class);
    private static final Logger securityLog = LoggerFactory.getLogger("SECURITY");

    private static final String BUCKET_SEND_MINUTE = "message-send";
    private static final String BUCKET_SEND_DAY = "message-send-daily";

    private final MessageRepository messageRepository;
    private final MessageReceiptRepository receiptRepository;
    private final MessageReactionRepository reactionRepository;
    private final ConversationRepository conversationRepository;
    private final ConversationParticipantRepository participantRepository;
    private final ConversationAccessPort conversationAccessPort;
    private final BlockService blockService;
    private final MessageMapper messageMapper;
    private final PreviewFormatter previewFormatter;
    private final EmojiCatalog emojiCatalog;
    private final RateLimiter rateLimiter;
    private final AppProperties properties;
    private final TimeProvider timeProvider;
    private final ApplicationEventPublisher eventPublisher;

    public MessageService(MessageRepository messageRepository,
                          MessageReceiptRepository receiptRepository,
                          MessageReactionRepository reactionRepository,
                          ConversationRepository conversationRepository,
                          ConversationParticipantRepository participantRepository,
                          ConversationAccessPort conversationAccessPort,
                          BlockService blockService,
                          MessageMapper messageMapper,
                          PreviewFormatter previewFormatter,
                          EmojiCatalog emojiCatalog,
                          RateLimiter rateLimiter,
                          AppProperties properties,
                          TimeProvider timeProvider,
                          ApplicationEventPublisher eventPublisher) {
        this.messageRepository = messageRepository;
        this.receiptRepository = receiptRepository;
        this.reactionRepository = reactionRepository;
        this.conversationRepository = conversationRepository;
        this.participantRepository = participantRepository;
        this.conversationAccessPort = conversationAccessPort;
        this.blockService = blockService;
        this.messageMapper = messageMapper;
        this.previewFormatter = previewFormatter;
        this.emojiCatalog = emojiCatalog;
        this.rateLimiter = rateLimiter;
        this.properties = properties;
        this.timeProvider = timeProvider;
        this.eventPublisher = eventPublisher;
    }

    // ------------------------------------------------------------------ send

    /**
     * Sends a message.
     *
     * @param clientMessageId caller-generated idempotency key
     * @return the stored message plus whether it was a replay of an earlier send
     */
    @Transactional
    public SendResult send(UUID senderId, UUID conversationId, String content, UUID clientMessageId,
                           String messageType, UUID replyToMessageId) {
        requireActiveParticipant(conversationId, senderId);
        MessageType type = parseType(messageType);

        // 1. Idempotency before anything else: a duplicated retry must not consume rate limit, must not
        //    touch the conversation pointer, and must not emit a second announcement.
        Optional<Message> replayed = messageRepository
                .findByConversationIdAndSenderIdAndClientMessageId(conversationId, senderId, clientMessageId);
        if (replayed.isPresent()) {
            log.debug("idempotent send replay conversationId={}", conversationId);
            return new SendResult(render(replayed.get(), senderId), true);
        }

        UUID counterpartId = conversationAccessPort.counterpartOf(conversationId, senderId).orElse(null);
        if (counterpartId != null) {
            // A block ends the conversation in both directions, immediately and server-side.
            blockService.requireNotBlocked(senderId, counterpartId);
        }
        enforceSendRateLimits(senderId);

        String normalized = TextNormalizer.normalizeText(content);
        validateContent(normalized);

        Message parent = null;
        if (replyToMessageId != null) {
            parent = messageRepository.findByIdWithinConversation(replyToMessageId, conversationId)
                    .orElseThrow(() -> new NotFoundException("The message you are replying to was not found"));
        }

        Message message = Message.createText(conversationId, senderId, normalized, clientMessageId, replyToMessageId);
        try {
            messageRepository.saveAndFlush(message);
        } catch (DataIntegrityViolationException ex) {
            // Two retries raced: the unique index on (conversation, sender, client_message_id) kept
            // exactly one message. Return the winner instead of an error.
            Message winner = messageRepository
                    .findByConversationIdAndSenderIdAndClientMessageId(conversationId, senderId, clientMessageId)
                    .orElseThrow(() -> ex);
            return new SendResult(render(winner, senderId), true);
        }

        List<MessageReceipt> receipts = createReceipts(message, senderId);
        updateConversationPointer(conversationId, message);

        // A message revives a conversation the recipient had removed: their copy comes back so the
        // message is not silently invisible to them.
        participantRepository.reviveForConversation(conversationId);

        ChatMessageResponse senderView = messageMapper.toResponse(message, parent, List.of(), receipts, senderId);
        eventPublisher.publishEvent(new MessageAckEvent(senderId, clientMessageId, senderView));

        for (MessageReceipt receipt : receipts) {
            ChatMessageResponse recipientView = messageMapper.toResponse(message, parent, List.of(),
                    List.of(receipt), receipt.getUserId());
            eventPublisher.publishEvent(new MessageNewEvent(Set.of(receipt.getUserId()), recipientView));
        }
        announceConversationChange(conversationId, message, senderId, counterpartId);

        // Notification trigger: a plain application event consumed after commit, so a lost
        // notification can never fail the send or resurrect a rolled-back message.
        Set<UUID> notificationRecipients = new java.util.LinkedHashSet<>();
        receipts.forEach(receipt -> notificationRecipients.add(receipt.getUserId()));
        if (!notificationRecipients.isEmpty()) {
            eventPublisher.publishEvent(new MessageCommittedEvent(conversationId, message.getId(), senderId,
                    notificationRecipients, previewFormatter.preview(message.getContent())));
        }
        securityLog.debug("message sent conversationId={} sender={} length={}",
                conversationId, senderId, normalized.length());
        return new SendResult(senderView, false);
    }

    private List<MessageReceipt> createReceipts(Message message, UUID senderId) {
        List<MessageReceipt> receipts = new ArrayList<>();
        for (UUID participantId : conversationAccessPort.participantIds(message.getConversationId())) {
            if (participantId.equals(senderId)) {
                continue; // the sender does not need a receipt for their own message
            }
            receipts.add(MessageReceipt.sent(message.getId(), participantId));
        }
        if (!receipts.isEmpty()) {
            receiptRepository.saveAll(receipts);
        }
        return receipts;
    }

    /**
     * Moves the conversation's denormalised pointer.
     *
     * <p>Done inside the same transaction as the insert, so a list view can never point at a message
     * that does not exist, and its ordering timestamp always matches real activity.
     */
    private void updateConversationPointer(UUID conversationId, Message message) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new NotFoundException("Conversation not found"));
        conversation.recordMessage(message.getId(), message.getCreatedAt() == null
                ? timeProvider.now()
                : message.getCreatedAt());
        conversationRepository.save(conversation);
    }

    private void announceConversationChange(UUID conversationId, Message message, UUID senderId, UUID counterpartId) {
        Map<String, Object> changes = new LinkedHashMap<>();
        changes.put("lastMessage", Map.of(
                "id", message.getId(),
                "senderId", message.getSenderId(),
                "preview", previewFormatter.preview(message.getContent()),
                "messageType", message.getMessageType().name(),
                "createdAt", message.getCreatedAt(),
                "deleted", false));
        changes.put("lastActivityAt", message.getCreatedAt());
        Set<UUID> recipients = counterpartId == null
                ? Set.of(senderId)
                : Set.of(senderId, counterpartId);
        eventPublisher.publishEvent(new ConversationUpdatedEvent(conversationId, recipients, changes));
    }

    // ------------------------------------------------------------------ edit

    /**
     * Edits a message. Only the author may, only within the edit window, and only a live TEXT message.
     *
     * <p>The window exists because chat is a shared record: past a couple of days, silently rewriting
     * what the other person already read is indistinguishable from tampering. Deleting stays available
     * forever — removing your own words is a different act from changing them under someone.
     */
    @Transactional
    public ChatMessageResponse edit(UUID userId, UUID messageId, String content) {
        Message message = requireMessage(messageId);
        requireActiveParticipant(message.getConversationId(), userId);
        if (!message.getSenderId().equals(userId)) {
            throw new ForbiddenException("You can only edit your own messages");
        }
        if (!message.isEditableAt(timeProvider.now(), properties.getMessaging().getEditWindow())) {
            throw new InvalidRequestException("This message can no longer be edited");
        }
        String normalized = TextNormalizer.normalizeText(content);
        validateContent(normalized);
        message.edit(normalized, timeProvider.now());
        messageRepository.save(message);

        ChatMessageResponse response = render(message, userId);
        Set<UUID> recipients = conversationAccessPort.participantIds(message.getConversationId());
        eventPublisher.publishEvent(new MessageUpdatedEvent(recipients, response));
        return response;
    }

    // ------------------------------------------------------------------ delete

    /** Soft-deletes a message (author only). Replies keep working; the parent becomes a tombstone. */
    @Transactional
    public void delete(UUID userId, UUID messageId) {
        Message message = requireMessage(messageId);
        requireActiveParticipant(message.getConversationId(), userId);
        if (!message.isDeletableBy(userId)) {
            throw new ForbiddenException("You can only delete your own messages");
        }
        Instant now = timeProvider.now();
        message.softDelete(now);
        messageRepository.save(message);

        Set<UUID> recipients = conversationAccessPort.participantIds(message.getConversationId());
        eventPublisher.publishEvent(new MessageDeletedEvent(recipients, messageId,
                message.getConversationId(), now));

        // If this was the conversation's newest message, the list preview must not keep showing it.
        conversationRepository.findById(message.getConversationId()).ifPresent(conversation -> {
            if (messageId.equals(conversation.getLastMessageId())) {
                conversation.clearLastMessage();
                conversationRepository.save(conversation);
                eventPublisher.publishEvent(new ConversationUpdatedEvent(message.getConversationId(),
                        recipients, Map.of("lastMessage", Map.of(
                                "id", messageId,
                                "preview", (Object) null,
                                "deleted", true))));
            }
        });
        log.info("message deleted messageId={} by={}", messageId, userId);
    }

    // ------------------------------------------------------------------ reactions

    /** Toggles a reaction and returns the message with aggregated reactions. */
    @Transactional
    public ChatMessageResponse toggleReaction(UUID userId, UUID messageId, String emoji) {
        Message message = requireMessage(messageId);
        requireActiveParticipant(message.getConversationId(), userId);
        UUID counterpartId = conversationAccessPort.counterpartOf(message.getConversationId(), userId).orElse(null);
        if (counterpartId != null) {
            blockService.requireNotBlocked(userId, counterpartId);
        }
        if (!emojiCatalog.isAllowed(emoji)) {
            throw new InvalidRequestException("That reaction is not supported");
        }

        int removed = reactionRepository.remove(messageId, userId, emoji);
        if (removed == 0) {
            reactionRepository.save(new MessageReaction(messageId, userId, emoji));
        }
        // Read the aggregate back through the same code path the history endpoint uses, so a toggled
        // reaction and a reloaded page can never disagree.
        ChatMessageResponse response = render(message, userId);
        Set<UUID> recipients = conversationAccessPort.participantIds(message.getConversationId());
        eventPublisher.publishEvent(new MessageReactionUpdatedEvent(recipients, messageId,
                message.getConversationId(), response.reactions()));
        return response;
    }

    // ------------------------------------------------------------------ acknowledgements

    /**
     * Marks messages delivered up to a watermark.
     *
     * <p>Only the recipient can acknowledge, and only messages for which a receipt exists — which is
     * exactly what the server itself created when the message was sent, so a client cannot claim to
     * have delivered messages it never received.
     */
    @Transactional
    public void markDelivered(UUID recipientId, UUID conversationId, UUID lastMessageId) {
        requireActiveParticipant(conversationId, recipientId);
        Long watermark = resolveWatermark(conversationId, recipientId, lastMessageId);
        if (watermark == null) {
            throw new NotFoundException("Message not found");
        }
        Instant now = timeProvider.now();
        int updated = receiptRepository.markDeliveredUpTo(conversationId, recipientId, watermark,
                MessageReceipt.ReceiptStatus.SENT, MessageReceipt.ReceiptStatus.DELIVERED, now);
        if (updated == 0) {
            return; // already acknowledged: no state changed, no event needed
        }
        UUID senderId = conversationAccessPort.counterpartOf(conversationId, recipientId).orElse(null);
        if (senderId != null) {
            eventPublisher.publishEvent(new MessageDeliveredEvent(senderId, recipientId, conversationId,
                    lastMessageId, now));
        }
    }

    /**
     * Marks a single message read (the per-message endpoint).
     *
     * <p>Conversation-level read lives in the conversations module because it moves the participant's
     * cursor as well; this one only moves the receipt, which is what a "mark this unread-looking
     * message as read" action needs.
     */
    @Transactional
    public void markRead(UUID readerId, UUID messageId) {
        Message message = requireMessage(messageId);
        requireActiveParticipant(message.getConversationId(), readerId);
        if (message.getSenderId().equals(readerId)) {
            // Reading your own message is not an event; accepting it silently would let a client
            // fabricate read receipts for the other side.
            return;
        }
        Instant now = timeProvider.now();
        int updated = receiptRepository.markReadUpTo(message.getConversationId(), readerId, message.getSequence(),
                MessageReceipt.ReceiptStatus.READ, now);
        if (updated == 0) {
            return;
        }
        UUID senderId = message.getSenderId();
        eventPublisher.publishEvent(new com.realtimechat.conversations.event.MessageReadEvent(
                message.getConversationId(), readerId, senderId, messageId, now));
    }

    // ------------------------------------------------------------------ helpers

    /** Turns a client-supplied message id into a server-owned sequence watermark. */
    private Long resolveWatermark(UUID conversationId, UUID userId, UUID lastMessageId) {
        Message message = messageRepository.findByIdWithinConversation(lastMessageId, conversationId)
                .orElseThrow(() -> new NotFoundException("Message not found"));
        Long sequence = message.getSequence();
        if (sequence == null) {
            return null;
        }
        // The receipt must exist for this user, otherwise the watermark would cover messages that were
        // never delivered to them (for example messages sent before they joined).
        return receiptRepository.maxReceiptSequenceUpTo(conversationId, userId, sequence);
    }

    private void enforceSendRateLimits(UUID senderId) {
        AppProperties.RateLimit limits = properties.getRateLimit();
        if (!limits.isEnabled()) {
            return;
        }
        String key = senderId.toString();
        RateLimitDecision perMinute = rateLimiter.consume(BUCKET_SEND_MINUTE, key,
                limits.getSendPerMinute(), Duration.ofMinutes(1));
        if (!perMinute.allowed()) {
            securityLog.warn("message send rate limit tripped sender={}", senderId);
            throw new RateLimitExceededException(
                    "You are sending messages too quickly. Try again in "
                            + Math.max(1, perMinute.retryAfter().toSeconds()) + " seconds.");
        }
        RateLimitDecision perDay = rateLimiter.consume(BUCKET_SEND_DAY, key,
                limits.getSendPerDay(), Duration.ofDays(1));
        if (!perDay.allowed()) {
            securityLog.warn("daily message quota exhausted sender={}", senderId);
            throw new RateLimitExceededException("You have reached today's message limit.");
        }
    }

    private void validateContent(String normalized) {
        if (normalized == null || normalized.isBlank()) {
            throw new InvalidRequestException("A message cannot be empty");
        }
        int max = properties.getMessaging().getMaxContentLength();
        if (normalized.length() > max) {
            throw new InvalidRequestException("A message can be at most " + max + " characters");
        }
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            if (Character.isISOControl(c) && c != '\n' && c != '\t') {
                throw new InvalidRequestException("A message cannot contain control characters");
            }
        }
    }

    private MessageType parseType(String messageType) {
        if (messageType == null || messageType.isBlank()) {
            return MessageType.TEXT;
        }
        MessageType type;
        try {
            type = MessageType.valueOf(messageType.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new UnsupportedMessageTypeException("Unknown message type");
        }
        if (type == MessageType.SYSTEM) {
            // SYSTEM messages are server-generated only; a client asking for one is not authorised to.
            throw new UnsupportedMessageTypeException("System messages cannot be sent by clients");
        }
        if (type != MessageType.TEXT) {
            // Attachments are modelled in the schema but not enabled in this release; accepting them
            // would promise a file that could never be stored or served.
            throw new UnsupportedMessageTypeException("Attachments are not supported yet");
        }
        return type;
    }

    /** Loads a message and renders it for one viewer (single-message command paths). */
    ChatMessageResponse render(Message message, UUID viewerId) {
        Message parent = message.getReplyToMessageId() == null
                ? null
                : messageRepository.findById(message.getReplyToMessageId()).orElse(null);
        List<MessageReaction> reactions = reactionRepository.findByMessageIdIn(List.of(message.getId()));
        List<MessageReceipt> receipts = receiptRepository.findByMessageIdIn(List.of(message.getId()));
        return messageMapper.toResponse(message, parent, reactions, receipts, viewerId);
    }

    private Message requireMessage(UUID messageId) {
        return messageRepository.findById(messageId)
                .orElseThrow(() -> new NotFoundException("Message not found"));
    }

    /**
     * Ensures the caller is a participant.
     *
     * <p>A participant row that was soft-deleted still counts — the user removed the conversation from
     * their list, but the conversation is revived by the next message. Refusing here would make a
     * previously removed thread permanently unsendable.
     */
    private ConversationParticipant requireActiveParticipant(UUID conversationId, UUID userId) {
        if (!conversationAccessPort.isParticipant(conversationId, userId)) {
            // 404 rather than 403: non-participants must not learn that the conversation exists.
            throw new NotFoundException("Conversation not found");
        }
        return participantRepository.findByIdConversationIdAndIdUserId(conversationId, userId)
                .orElseThrow(() -> new NotFoundException("Conversation not found"));
    }

    /** Result of a send: the message and whether it came from an earlier attempt. */
    public record SendResult(ChatMessageResponse message, boolean replayed) {
    }
}
