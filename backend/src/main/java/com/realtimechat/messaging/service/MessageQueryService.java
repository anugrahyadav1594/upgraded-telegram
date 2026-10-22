package com.realtimechat.messaging.service;

import com.realtimechat.common.error.InvalidRequestException;
import com.realtimechat.common.error.NotFoundException;
import com.realtimechat.common.pagination.Cursor;
import com.realtimechat.common.pagination.CursorCodec;
import com.realtimechat.common.pagination.PageResponse;
import com.realtimechat.conversations.service.ConversationAccessPort;
import com.realtimechat.messaging.dto.ChatMessageResponse;
import com.realtimechat.messaging.entity.Message;
import com.realtimechat.messaging.entity.MessageReaction;
import com.realtimechat.messaging.entity.MessageReceipt;
import com.realtimechat.messaging.mapper.MessageMapper;
import com.realtimechat.messaging.repository.MessageReactionRepository;
import com.realtimechat.messaging.repository.MessageReceiptRepository;
import com.realtimechat.messaging.repository.MessageRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Message reads: history pages and reconnect resynchronisation.
 *
 * <p>Both access paths are keyset-based, and the difference between them matters:
 * <ul>
 *   <li><b>History</b> walks backwards from "now" using {@code (created_at, id) < cursor}, ordered
 *       descending. Deep history costs the same as recent history because there is no {@code OFFSET}
 *       — and unlike offsets, new incoming messages cannot shift the window and cause a row to be
 *       skipped or repeated.</li>
 *   <li><b>Resync</b> walks forwards from a sequence watermark ({@code sequence > ?}). It is the
 *       answer to "I was disconnected, what did I miss?" and is deliberately independent of client
 *       clocks: sequences are assigned by the database.</li>
 * </ul>
 *
 * <p>Every method loads reactions and receipts for the whole page in two batched queries and hands
 * them to the mapper, so rendering 50 messages never issues 150 queries.
 */
@Service
public class MessageQueryService {

    private static final int MAX_LIMIT = 100;

    private final MessageRepository messageRepository;
    private final MessageReceiptRepository receiptRepository;
    private final MessageReactionRepository reactionRepository;
    private final ConversationAccessPort conversationAccessPort;
    private final MessageMapper messageMapper;
    private final CursorCodec cursorCodec;

    public MessageQueryService(MessageRepository messageRepository,
                               MessageReceiptRepository receiptRepository,
                               MessageReactionRepository reactionRepository,
                               ConversationAccessPort conversationAccessPort,
                               MessageMapper messageMapper,
                               CursorCodec cursorCodec) {
        this.messageRepository = messageRepository;
        this.receiptRepository = receiptRepository;
        this.reactionRepository = reactionRepository;
        this.conversationAccessPort = conversationAccessPort;
        this.messageMapper = messageMapper;
        this.cursorCodec = cursorCodec;
    }

    /**
     * One page of history, newest first.
     *
     * @param direction {@code older} (default) or {@code newer}
     * @param rawCursor opaque cursor from a previous page ({@code (createdAt, id)})
     */
    @Transactional(readOnly = true)
    public PageResponse<ChatMessageResponse> history(UUID userId, UUID conversationId, String rawCursor,
                                                     int limit, String direction) {
        requireParticipant(conversationId, userId);
        int pageSize = Math.min(Math.max(limit, 1), MAX_LIMIT);
        boolean newer = "newer".equalsIgnoreCase(direction);
        Cursor cursor = cursorCodec.decode(rawCursor);
        // One extra row: `hasMore` is then exact without a COUNT over a potentially huge table.
        PageRequest pageRequest = PageRequest.of(0, pageSize + 1);

        List<Message> rows;
        if (cursor == null) {
            rows = messageRepository.findLatestPage(conversationId, pageRequest);
        } else {
            var createdAt = cursor.instantKey();
            if (createdAt == null) {
                throw new InvalidRequestException("Invalid cursor");
            }
            rows = newer
                    ? messageRepository.findPageAfter(conversationId, createdAt, cursor.id(), pageRequest)
                    : messageRepository.findPageBefore(conversationId, createdAt, cursor.id(), pageRequest);
        }

        List<ChatMessageResponse> rendered = render(rows, userId);
        // The cursor points at the last returned item in the direction of travel, so the next request
        // continues exactly where this one stopped — whether walking back in time or forward after a
        // reconnect.
        return PageResponse.of(rendered, pageSize,
                response -> Cursor.ofInstant(response.createdAt(), response.id()), cursorCodec);
    }

    /**
     * Everything after a sequence watermark, oldest first.
     *
     * <p>Used by the client's reconnect routine. Bounded by {@code limit}; a client that missed more
     * than one page simply asks again with the new watermark (its own loop), which keeps the server
     * from ever building an unbounded response.
     */
    @Transactional(readOnly = true)
    public List<ChatMessageResponse> resync(UUID userId, UUID conversationId, Long afterSequence, int limit) {
        requireParticipant(conversationId, userId);
        int pageSize = Math.min(Math.max(limit, 1), MAX_LIMIT);
        long watermark = afterSequence == null ? 0L : Math.max(0L, afterSequence);
        List<Message> rows = messageRepository.findByConversationIdAndSequenceGreaterThanOrderBySequenceAsc(
                conversationId, watermark, PageRequest.of(0, pageSize));
        return render(rows, userId);
    }

    /** Highest sequence the caller can see in this conversation: the client's next watermark. */
    @Transactional(readOnly = true)
    public Long watermark(UUID userId, UUID conversationId) {
        requireParticipant(conversationId, userId);
        Long max = messageRepository.findMaxSequence(conversationId);
        return max == null ? 0L : max;
    }

    // ------------------------------------------------------------------ helpers

    /**
     * Renders a page with three queries total: messages (already loaded), reactions, receipts.
     *
     * <p>Reply parents are fetched in the same batch by collecting the referenced ids and loading them
     * together — including parents that are not part of the page (a reply to an older message).
     */
    private List<ChatMessageResponse> render(List<Message> messages, UUID viewerId) {
        if (messages.isEmpty()) {
            return List.of();
        }
        List<UUID> messageIds = messages.stream().map(Message::getId).toList();

        List<UUID> parentIds = messages.stream()
                .map(Message::getReplyToMessageId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        Map<UUID, Message> parents = new HashMap<>(parentIds.size());
        if (!parentIds.isEmpty()) {
            messageRepository.findAllById(parentIds).forEach(parent -> parents.put(parent.getId(), parent));
        }

        Map<UUID, List<MessageReaction>> reactions = new HashMap<>();
        for (MessageReaction reaction : reactionRepository.findByMessageIdIn(messageIds)) {
            reactions.computeIfAbsent(reaction.getMessageId(), key -> new ArrayList<>()).add(reaction);
        }
        Map<UUID, List<MessageReceipt>> receipts = new HashMap<>();
        for (MessageReceipt receipt : receiptRepository.findByMessageIdIn(messageIds)) {
            receipts.computeIfAbsent(receipt.getMessageId(), key -> new ArrayList<>()).add(receipt);
        }
        return messageMapper.toResponses(messages, parents, reactions, receipts, viewerId);
    }

    private void requireParticipant(UUID conversationId, UUID userId) {
        if (!conversationAccessPort.isParticipant(conversationId, userId)) {
            throw new NotFoundException("Conversation not found");
        }
    }
}
