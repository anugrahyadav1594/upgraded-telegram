package com.realtimechat.conversations.service;

import com.realtimechat.common.error.NotFoundException;
import com.realtimechat.common.pagination.Cursor;
import com.realtimechat.common.pagination.CursorCodec;
import com.realtimechat.common.pagination.PageResponse;
import com.realtimechat.conversations.dto.ConversationResponse;
import com.realtimechat.conversations.entity.Conversation;
import com.realtimechat.conversations.entity.ConversationParticipant;
import com.realtimechat.conversations.repository.ConversationParticipantRepository;
import com.realtimechat.conversations.repository.ConversationRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Conversation reads: the list and single-conversation views.
 *
 * <p>Pagination is keyset-based on {@code (last_activity_at, id)}. Both halves matter:
 * <ul>
 *   <li>the timestamp orders by recency (what the user expects), and</li>
 *   <li>the id breaks ties so the cursor is <em>total</em> — without it, two conversations with the
 *       same activity timestamp could repeat or vanish across pages.</li>
 * </ul>
 * {@code OFFSET} is never used: page 50 costs the same as page 1.
 */
@Service
public class ConversationQueryService {

    private final ConversationRepository conversationRepository;
    private final ConversationParticipantRepository participantRepository;
    private final ConversationAssembler assembler;
    private final CursorCodec cursorCodec;

    public ConversationQueryService(ConversationRepository conversationRepository,
                                    ConversationParticipantRepository participantRepository,
                                    ConversationAssembler assembler,
                                    CursorCodec cursorCodec) {
        this.conversationRepository = conversationRepository;
        this.participantRepository = participantRepository;
        this.assembler = assembler;
        this.cursorCodec = cursorCodec;
    }

    /**
     * One page of the caller's conversations, most recent activity first.
     *
     * @param archived {@code null} = both active and archived, {@code true}/{@code false} = filter
     */
    @Transactional(readOnly = true)
    public PageResponse<ConversationResponse> list(UUID userId, String rawCursor, int limit, Boolean archived) {
        Cursor cursor = cursorCodec.decode(rawCursor);
        // Fetch one extra row so `hasMore` is exact without a second COUNT query.
        PageRequest pageRequest = PageRequest.of(0, limit + 1);

        List<ConversationParticipant> rows;
        if (cursor == null) {
            rows = participantRepository.findPageByUser(userId, archived, pageRequest);
        } else {
            java.time.Instant activity = cursor.instantKey();
            if (activity == null) {
                throw new com.realtimechat.common.error.InvalidRequestException("Invalid cursor");
            }
            rows = participantRepository.findPageByUserAfter(userId, archived, activity, cursor.id(), pageRequest);
        }

        List<ConversationResponse> assembled = assembler.assemble(rows, userId);
        return PageResponse.of(assembled, limit,
                response -> Cursor.ofInstant(response.lastActivityAt(), response.id()),
                cursorCodec);
    }

    /** One conversation; a non-participant gets 404 so existence is never disclosed. */
    @Transactional(readOnly = true)
    public ConversationResponse get(UUID conversationId, UUID userId) {
        ConversationParticipant participant = participantRepository
                .findByIdConversationIdAndIdUserId(conversationId, userId)
                .orElseThrow(() -> new NotFoundException("Conversation not found"));
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new NotFoundException("Conversation not found"));
        return assembler.assembleOne(conversation, participant, userId);
    }
}
