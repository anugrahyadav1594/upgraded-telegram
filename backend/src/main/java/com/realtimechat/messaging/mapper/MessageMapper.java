package com.realtimechat.messaging.mapper;

import com.realtimechat.common.util.PreviewFormatter;
import com.realtimechat.messaging.dto.ChatMessageResponse;
import com.realtimechat.messaging.dto.ReactionSummaryResponse;
import com.realtimechat.messaging.dto.ReceiptResponse;
import com.realtimechat.messaging.dto.ReplyPreviewResponse;
import com.realtimechat.messaging.entity.Message;
import com.realtimechat.messaging.entity.MessageReaction;
import com.realtimechat.messaging.entity.MessageReceipt;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Builds the wire representation of messages.
 *
 * <p>Batch-first by design: {@link #toResponses} takes pre-loaded reaction and receipt maps, so a page
 * of 50 messages is rendered with zero additional queries. The single-message overload exists for
 * command paths (send, edit, reaction) where one message is being assembled anyway.
 *
 * <p>Two rendering rules live here so they cannot be forgotten in one code path:
 * <ul>
 *   <li>a tombstoned message returns {@code content = null} — the erased text is <em>not</em> echoed
 *       back to whoever asks, including the author;</li>
 *   <li>receipt status is rendered from the caller's perspective, which is what makes a single
 *       representation usable by both sides of a conversation.</li>
 * </ul>
 */
@Component
public class MessageMapper {

    private static final int MAX_REACTION_TYPES = 8;

    private final PreviewFormatter previewFormatter;

    public MessageMapper(PreviewFormatter previewFormatter) {
        this.previewFormatter = previewFormatter;
    }

    /** Renders a page of messages; parents are looked up from the already-loaded map. */
    public List<ChatMessageResponse> toResponses(List<Message> messages,
                                                 Map<UUID, Message> parentsById,
                                                 Map<UUID, List<MessageReaction>> reactionsByMessage,
                                                 Map<UUID, List<MessageReceipt>> receiptsByMessage,
                                                 UUID viewerId) {
        List<ChatMessageResponse> responses = new ArrayList<>(messages.size());
        for (Message message : messages) {
            responses.add(render(message,
                    parentsById.get(message.getReplyToMessageId()),
                    reactionsByMessage.getOrDefault(message.getId(), List.of()),
                    receiptsByMessage.getOrDefault(message.getId(), List.of()),
                    viewerId));
        }
        return responses;
    }

    public ChatMessageResponse toResponse(Message message, Message parent,
                                          List<MessageReaction> reactions,
                                          List<MessageReceipt> receipts,
                                          UUID viewerId) {
        return render(message, parent, reactions, receipts, viewerId);
    }

    private ChatMessageResponse render(Message message, Message parent, List<MessageReaction> reactions,
                                       List<MessageReceipt> receipts, UUID viewerId) {
        boolean deleted = message.isDeleted();
        return new ChatMessageResponse(
                message.getId(),
                message.getConversationId(),
                message.getSenderId(),
                deleted ? null : message.getContent(),
                message.getMessageType().name(),
                message.getClientMessageId(),
                message.getSequence(),
                parent == null ? null : toReplyPreview(parent),
                aggregateReactions(reactions, viewerId),
                aggregateReceipt(message, receipts, viewerId),
                message.getEditedAt(),
                message.getDeletedAt(),
                message.getCreatedAt(),
                message.getUpdatedAt());
    }

    /** Reply parent: preview only, and a tombstone stays a tombstone. */
    private ReplyPreviewResponse toReplyPreview(Message parent) {
        if (parent.isDeleted()) {
            return new ReplyPreviewResponse(parent.getId(), parent.getSenderId(), null, true);
        }
        return new ReplyPreviewResponse(parent.getId(), parent.getSenderId(),
                previewFormatter.preview(parent.getContent(), 120), false);
    }

    private List<ReactionSummaryResponse> aggregateReactions(List<MessageReaction> reactions, UUID viewerId) {
        if (reactions.isEmpty()) {
            return List.of();
        }
        Map<String, List<MessageReaction>> byEmoji = reactions.stream()
                .collect(Collectors.groupingBy(MessageReaction::getEmoji, LinkedHashMap::new, Collectors.toList()));
        List<ReactionSummaryResponse> summaries = new ArrayList<>(byEmoji.size());
        byEmoji.forEach((emoji, group) -> summaries.add(new ReactionSummaryResponse(
                emoji,
                group.size(),
                group.stream().anyMatch(reaction -> reaction.getUserId().equals(viewerId)))));
        // Deterministic order (most used first, then emoji) so two clients render the same thing.
        summaries.sort(Comparator.comparingInt(ReactionSummaryResponse::count).reversed()
                .thenComparing(ReactionSummaryResponse::emoji));
        return summaries.size() > MAX_REACTION_TYPES ? summaries.subList(0, MAX_REACTION_TYPES) : summaries;
    }

    /**
     * Receipt from the viewer's perspective.
     *
     * <p>The sender sees the aggregate of what recipients did (sent → one tick, delivered → two,
     * read → blue). A recipient sees their own receipt. A message the viewer sent that has no
     * receipts yet is {@code SENT} — never {@code READ}, because the server has no evidence of it.
     */
    private ReceiptResponse aggregateReceipt(Message message, List<MessageReceipt> receipts, UUID viewerId) {
        if (message.getSenderId().equals(viewerId)) {
            if (receipts.isEmpty()) {
                return ReceiptResponse.sent();
            }
            return new ReceiptResponse(
                    worstStatus(receipts).name(),
                    earliest(receipts.stream().map(MessageReceipt::getDeliveredAt).toList()),
                    earliest(receipts.stream().map(MessageReceipt::getReadAt).toList()));
        }
        return receipts.stream()
                .filter(receipt -> receipt.getUserId().equals(viewerId))
                .findFirst()
                .map(receipt -> new ReceiptResponse(receipt.getStatus().name(), receipt.getDeliveredAt(),
                        receipt.getReadAt()))
                .orElseGet(ReceiptResponse::sent);
    }

    /** The least-advanced recipient status, i.e. "has everyone reached at least this point". */
    private MessageReceipt.ReceiptStatus worstStatus(Collection<MessageReceipt> receipts) {
        MessageReceipt.ReceiptStatus worst = MessageReceipt.ReceiptStatus.READ;
        for (MessageReceipt receipt : receipts) {
            if (receipt.getStatus().ordinal() < worst.ordinal()) {
                worst = receipt.getStatus();
            }
        }
        return worst;
    }

    private java.time.Instant earliest(List<java.time.Instant> instants) {
        return instants.stream().filter(java.util.Objects::nonNull).min(Comparator.naturalOrder()).orElse(null);
    }
}
