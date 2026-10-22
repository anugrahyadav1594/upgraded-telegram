package com.realtimechat.conversations.service;

import com.realtimechat.common.error.BlockedException;
import com.realtimechat.common.error.ConflictException;
import com.realtimechat.common.error.InvalidRequestException;
import com.realtimechat.common.error.NotFoundException;
import com.realtimechat.common.util.TimeProvider;
import com.realtimechat.conversations.dto.ConversationResponse;
import com.realtimechat.conversations.dto.CreateConversationResult;
import com.realtimechat.conversations.dto.UpdateConversationRequest;
import com.realtimechat.conversations.entity.Conversation;
import com.realtimechat.conversations.entity.ConversationParticipant;
import com.realtimechat.conversations.event.ConversationCreatedEvent;
import com.realtimechat.conversations.event.ConversationUpdatedEvent;
import com.realtimechat.conversations.event.MessageReadEvent;
import com.realtimechat.conversations.repository.ConversationParticipantRepository;
import com.realtimechat.conversations.repository.ConversationRepository;
import com.realtimechat.users.service.BlockQueryService;
import com.realtimechat.users.service.PrivacyService;
import com.realtimechat.users.service.UserAccountService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Conversation writes: create, set flags, remove, mark read.
 *
 * <p>Authorisation for every operation is a membership check against the database
 * ({@code conversation_participants}), never a client-supplied participant list. A caller who is not
 * a participant gets a {@code 404} rather than a {@code 403}, so the API does not confirm that a
 * conversation they are excluded from exists.
 */
@Service
public class ConversationService implements ConversationAccessPort {

    private static final Logger log = LoggerFactory.getLogger(ConversationService.class);
    private static final Logger securityLog = LoggerFactory.getLogger("SECURITY");

    private final ConversationRepository conversationRepository;
    private final ConversationParticipantRepository participantRepository;
    private final ConversationAssembler assembler;
    private final UserAccountService userAccountService;
    private final BlockQueryService blockQueryService;
    private final PrivacyService privacyService;
    private final ReadReceiptPort readReceiptPort;
    private final ApplicationEventPublisher eventPublisher;
    private final TimeProvider timeProvider;

    public ConversationService(ConversationRepository conversationRepository,
                               ConversationParticipantRepository participantRepository,
                               ConversationAssembler assembler,
                               UserAccountService userAccountService,
                               BlockQueryService blockQueryService,
                               PrivacyService privacyService,
                               ReadReceiptPort readReceiptPort,
                               ApplicationEventPublisher eventPublisher,
                               TimeProvider timeProvider) {
        this.conversationRepository = conversationRepository;
        this.participantRepository = participantRepository;
        this.assembler = assembler;
        this.userAccountService = userAccountService;
        this.blockQueryService = blockQueryService;
        this.privacyService = privacyService;
        this.readReceiptPort = readReceiptPort;
        this.eventPublisher = eventPublisher;
        this.timeProvider = timeProvider;
    }

    // ------------------------------------------------------------------ create

    /**
     * Starts a conversation with a user, or reopens the existing one.
     *
     * <p>Idempotent for DIRECT conversations — including one the caller had removed, which is
     * un-deleted rather than duplicated. That is what makes "start chat" safe to retry and impossible
     * to turn into two threads for the same two people.
     */
    @Transactional
    public CreateConversationResult create(UUID requesterId, UUID peerUserId) {
        if (requesterId.equals(peerUserId)) {
            throw new ConflictException("You cannot start a conversation with yourself");
        }
        // Unknown, suspended and deleted accounts are indistinguishable here on purpose.
        userAccountService.requireActiveUser(peerUserId);

        if (blockQueryService.isBlockedEitherWay(requesterId, peerUserId)) {
            throw new BlockedException("You cannot message this user");
        }
        boolean alreadyInContact = participantRepository.findCounterpartIds(requesterId).contains(peerUserId);
        privacyService.requireMayStartConversation(peerUserId, requesterId, alreadyInContact);

        String directKey = Conversation.directKeyFor(requesterId, peerUserId);
        Optional<Conversation> existing = conversationRepository.findByDirectKey(directKey);
        if (existing.isPresent()) {
            Conversation conversation = existing.get();
            ConversationParticipant mine = requireParticipant(conversation.getId(), requesterId);
            boolean revived = mine.getDeletedAt() != null;
            if (revived) {
                mine.restore();
                participantRepository.save(mine);
            }
            ConversationParticipant theirs = requireParticipant(conversation.getId(), peerUserId);
            if (theirs.getDeletedAt() != null) {
                theirs.restore();
                participantRepository.save(theirs);
            }
            // It already existed (live or soft-deleted), so the contract returns 200, not 201.
            return new CreateConversationResult(assembler.assembleOne(conversation, mine, requesterId), false);
        }

        Conversation conversation = Conversation.createDirect(requesterId, peerUserId);
        try {
            conversationRepository.saveAndFlush(conversation);
        } catch (DataIntegrityViolationException ex) {
            // Two clients pressed "message" at the same instant: the unique index on direct_key
            // decided the winner. The loser re-reads that row, so the outcome is one conversation.
            log.debug("concurrent conversation creation resolved by unique index");
            Conversation winner = conversationRepository.findByDirectKey(directKey)
                    .orElseThrow(() -> new NotFoundException("Conversation not found"));
            ConversationParticipant mine = requireParticipant(winner.getId(), requesterId);
            return new CreateConversationResult(assembler.assembleOne(winner, mine, requesterId), false);
        }

        ConversationParticipant mine = requireParticipant(conversation.getId(), requesterId);
        ConversationResponse response = assembler.assembleOne(conversation, mine, requesterId);

        // The other participant learns about it in real time; the creator already has the response.
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", response.id());
        payload.put("type", response.type());
        payload.put("peer", response.peer());
        payload.put("unreadCount", 0);
        payload.put("lastActivityAt", response.lastActivityAt());
        payload.put("createdAt", response.createdAt());
        eventPublisher.publishEvent(new ConversationCreatedEvent(requesterId, peerUserId, payload));

        securityLog.info("conversation created initiator={} peer={}", requesterId, peerUserId);
        return new CreateConversationResult(response, true);
    }

    // ------------------------------------------------------------------ update / delete

    @Transactional
    public ConversationResponse update(UUID conversationId, UUID userId, UpdateConversationRequest request) {
        if (request.isEmpty()) {
            throw new InvalidRequestException("Nothing to update");
        }
        Conversation conversation = requireConversation(conversationId);
        ConversationParticipant participant = requireParticipant(conversationId, userId);

        Map<String, Object> changes = new LinkedHashMap<>();
        if (request.muted() != null && participant.mute(request.muted())) {
            changes.put("muted", request.muted());
        }
        if (request.archived() != null && participant.archive(request.archived())) {
            changes.put("archived", request.archived());
        }
        participantRepository.save(participant);

        ConversationResponse response = assembler.assembleOne(conversation, participant, userId);
        if (!changes.isEmpty()) {
            // Sent to the caller's own sessions so their other tabs stay consistent; the peer is not
            // told, because muting and archiving are private to this user.
            eventPublisher.publishEvent(new ConversationUpdatedEvent(conversationId, Set.of(userId), changes));
        }
        return response;
    }

    @Transactional
    public void delete(UUID conversationId, UUID userId) {
        requireConversation(conversationId);
        ConversationParticipant participant = requireParticipant(conversationId, userId);
        if (participant.softDelete(timeProvider.now())) {
            participantRepository.save(participant);
            eventPublisher.publishEvent(new ConversationUpdatedEvent(conversationId, Set.of(userId),
                    Map.of("deleted", true)));
            log.info("conversation removed for user userId={} conversationId={}", userId, conversationId);
        }
    }

    // ------------------------------------------------------------------ read state

    /**
     * Marks everything up to {@code lastMessageId} as read.
     *
     * <p>Ordering matters: the message id is validated against the conversation first (a client could
     * otherwise name a message from a conversation it is not in), then both stores are updated in the
     * same transaction, then — only if it actually changed something and the reader allows receipts —
     * the counterpart is told.
     */
    @Transactional
    public void markRead(UUID conversationId, UUID userId, UUID lastMessageId) {
        requireConversation(conversationId);
        ConversationParticipant participant = requireParticipant(conversationId, userId);

        // The message id arrives from the client, so it is validated against the conversation before
        // any state changes: naming a message from a conversation you are not in must not move a
        // cursor. A 404 (not 403) keeps "someone else's conversation" unobservable.
        if (!readReceiptPort.containsMessage(conversationId, lastMessageId)) {
            throw new NotFoundException("Message not found");
        }

        Instant now = timeProvider.now();
        boolean cursorMoved = participant.markRead(lastMessageId, now);
        if (cursorMoved) {
            participantRepository.save(participant);
        }
        int affected = readReceiptPort.markRead(conversationId, userId, lastMessageId, now);

        if (affected == 0) {
            return; // already read: no state changed, so nothing to announce
        }
        eventPublisher.publishEvent(new ConversationUpdatedEvent(conversationId, Set.of(userId),
                Map.of("unreadCount", 0)));
        if (!privacyService.readReceiptsEnabled(userId)) {
            return; // the reader's own badge is cleared above; the sender is not told
        }
        counterpartOf(conversationId, userId).ifPresent(counterpart -> eventPublisher.publishEvent(
                new MessageReadEvent(conversationId, userId, counterpart, lastMessageId, now)));
    }

    // ------------------------------------------------------------------ ConversationAccessPort

    @Override
    @Transactional(readOnly = true)
    public boolean isParticipant(UUID conversationId, UUID userId) {
        return participantRepository.existsByIdConversationIdAndIdUserId(conversationId, userId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UUID> counterpartOf(UUID conversationId, UUID userId) {
        if (!isParticipant(conversationId, userId)) {
            return Optional.empty();
        }
        return participantRepository.findAllByIdConversationId(conversationId).stream()
                .map(ConversationParticipant::getUserId)
                .filter(candidate -> !candidate.equals(userId))
                .findFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public Set<UUID> participantIds(UUID conversationId) {
        return participantRepository.findUserIdsByConversationId(conversationId);
    }

    // ------------------------------------------------------------------ helpers

    @Transactional(readOnly = true)
    public Conversation requireConversation(UUID conversationId) {
        return conversationRepository.findById(conversationId)
                .orElseThrow(() -> new NotFoundException("Conversation not found"));
    }

    @Transactional(readOnly = true)
    public ConversationParticipant requireParticipant(UUID conversationId, UUID userId) {
        return participantRepository.findByIdConversationIdAndIdUserId(conversationId, userId)
                // Existence of somebody else's conversation is not disclosed: 404, not 403.
                .orElseThrow(() -> new NotFoundException("Conversation not found"));
    }
}
