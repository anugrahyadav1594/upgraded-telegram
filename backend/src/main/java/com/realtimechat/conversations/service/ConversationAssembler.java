package com.realtimechat.conversations.service;

import com.realtimechat.conversations.dto.ConversationPeerResponse;
import com.realtimechat.conversations.dto.ConversationResponse;
import com.realtimechat.conversations.dto.MessagePreviewResponse;
import com.realtimechat.conversations.entity.Conversation;
import com.realtimechat.conversations.entity.ConversationParticipant;
import com.realtimechat.conversations.repository.ConversationParticipantRepository;
import com.realtimechat.conversations.repository.ConversationRepository;
import com.realtimechat.presence.dto.PresenceSnapshot;
import com.realtimechat.presence.service.PresenceService;
import com.realtimechat.users.service.BlockQueryService;
import com.realtimechat.users.service.ProfilePort;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Builds {@link ConversationResponse} objects from stored rows.
 *
 * <p>Two properties make this the right place for the assembly rather than a mapper:
 *
 * <ol>
 *   <li><b>Constant query count.</b> A page of 25 conversations costs a fixed number of reads —
 *       participants, conversations, peer profiles and last-message previews are all fetched in
 *       batches — never a lookup per row. That is the difference between a list that stays fast as
 *       history grows and one that degrades silently.</li>
 *   <li><b>Privacy is applied here, once.</b> Presence and block state are obtained from their owners
 *       (presence service, block query service) rather than by reading tables directly, so no
 *       response shape can accidentally bypass a privacy setting.</li>
 * </ol>
 */
@Component
public class ConversationAssembler {

    /** Bound on presence lookups per page, so an unusually large page cannot fan out unboundedly. */
    private static final int MAX_PRESENCE_LOOKUPS = 200;

    private final ConversationRepository conversationRepository;
    private final ConversationParticipantRepository participantRepository;
    private final ProfilePort profilePort;
    private final PresenceService presenceService;
    private final BlockQueryService blockQueryService;
    private final MessagePreviewPort messagePreviewPort;

    public ConversationAssembler(ConversationRepository conversationRepository,
                                 ConversationParticipantRepository participantRepository,
                                 ProfilePort profilePort,
                                 PresenceService presenceService,
                                 BlockQueryService blockQueryService,
                                 MessagePreviewPort messagePreviewPort) {
        this.conversationRepository = conversationRepository;
        this.participantRepository = participantRepository;
        this.profilePort = profilePort;
        this.presenceService = presenceService;
        this.blockQueryService = blockQueryService;
        this.messagePreviewPort = messagePreviewPort;
    }

    /**
     * Assembles responses for a page of the caller's participant rows.
     *
     * @param participants the page rows, already ordered by last activity (descending)
     * @param requesterId  the authenticated caller; every filter is applied relative to them
     */
    public List<ConversationResponse> assemble(List<ConversationParticipant> participants, UUID requesterId) {
        if (participants.isEmpty()) {
            return List.of();
        }
        List<UUID> conversationIds = participants.stream()
                .map(participant -> participant.getId().getConversationId())
                .toList();

        Map<UUID, Conversation> conversations = new HashMap<>(conversationIds.size());
        conversationRepository.findAllById(conversationIds)
                .forEach(conversation -> conversations.put(conversation.getId(), conversation));

        // Peers: every participant row of the page's conversations, fetched in one query, then the
        // "other" side is selected per conversation in memory.
        Map<UUID, UUID> peerByConversation = new HashMap<>(conversationIds.size());
        for (ConversationParticipant row : participantRepository.findAllByIdConversationIdIn(conversationIds)) {
            if (!row.getUserId().equals(requesterId)) {
                peerByConversation.put(row.getId().getConversationId(), row.getUserId());
            }
        }
        Set<UUID> peerIds = new HashSet<>(peerByConversation.values());

        Map<UUID, ProfilePort.UserSummary> profiles = profilePort.summariesOf(peerIds);
        Map<UUID, Integer> unreadCounts = messagePreviewPort.unreadCounts(requesterId, conversationIds);

        List<UUID> lastMessageIds = conversations.values().stream()
                .map(Conversation::getLastMessageId)
                .filter(Objects::nonNull)
                .toList();
        Map<UUID, MessagePreviewPort.MessagePreview> previews = messagePreviewPort.previewsOf(lastMessageIds);

        Set<UUID> blockedPeers = blockedAmong(requesterId, peerIds);
        Map<UUID, PresenceSnapshot> presence = resolvePresence(peerIds, requesterId, blockedPeers);

        List<ConversationResponse> responses = new ArrayList<>(participants.size());
        for (ConversationParticipant participant : participants) {
            UUID conversationId = participant.getId().getConversationId();
            Conversation conversation = conversations.get(conversationId);
            if (conversation == null) {
                continue; // the row vanished between the page query and assembly: skip, never fail
            }
            responses.add(build(conversation, participant, peerByConversation.get(conversationId),
                    profiles, presence, previews, unreadCounts.getOrDefault(conversationId, 0)));
        }
        return responses;
    }

    /** Single-conversation variant used by GET/POST/PATCH. */
    public ConversationResponse assembleOne(Conversation conversation, ConversationParticipant mine,
                                           UUID requesterId) {
        List<ConversationResponse> assembled = assemble(List.of(mine), requesterId);
        if (!assembled.isEmpty()) {
            return assembled.get(0);
        }
        // Fallback for a freshly created conversation whose participant page has not been re-read yet.
        return build(conversation, mine, counterpart(conversation, requesterId),
                Map.of(), Map.of(), Map.of(), 0);
    }

    private ConversationResponse build(Conversation conversation,
                                       ConversationParticipant mine,
                                       UUID peerId,
                                       Map<UUID, ProfilePort.UserSummary> profiles,
                                       Map<UUID, PresenceSnapshot> presence,
                                       Map<UUID, MessagePreviewPort.MessagePreview> previews,
                                       int unreadCount) {
        ConversationPeerResponse peer = null;
        if (peerId != null) {
            ProfilePort.UserSummary summary = profiles.get(peerId);
            PresenceSnapshot snapshot = presence.get(peerId);
            peer = new ConversationPeerResponse(
                    peerId,
                    summary == null ? null : summary.username(),
                    summary == null ? null : summary.displayName(),
                    summary == null ? null : summary.avatarUrl(),
                    snapshot != null && snapshot.isOnline(),
                    snapshot == null ? null : snapshot.lastSeenAt());
        }

        MessagePreviewResponse lastMessage = null;
        UUID lastMessageId = conversation.getLastMessageId();
        if (lastMessageId != null) {
            MessagePreviewPort.MessagePreview preview = previews.get(lastMessageId);
            if (preview != null) {
                lastMessage = new MessagePreviewResponse(preview.messageId(), preview.senderId(),
                        preview.preview(), preview.messageType(), preview.createdAt(), preview.deleted());
            } else {
                // The pointer exists but the message is no longer readable (for example removed by
                // retention): show the timestamp and a tombstone, never a stale preview string.
                lastMessage = new MessagePreviewResponse(lastMessageId, null, null, null,
                        conversation.getLastActivityAt(), true);
            }
        }

        return new ConversationResponse(
                conversation.getId(),
                conversation.getType().name(),
                peer,
                lastMessage,
                unreadCount,
                mine.isMuted(),
                mine.isArchived(),
                conversation.getLastActivityAt(),
                conversation.getCreatedAt());
    }

    /** Batch presence resolution; blocked relationships yield no presence at all. */
    private Map<UUID, PresenceSnapshot> resolvePresence(Set<UUID> peerIds, UUID requesterId,
                                                        Set<UUID> blockedPeers) {
        if (peerIds.isEmpty() || peerIds.size() > MAX_PRESENCE_LOOKUPS) {
            return Map.of();
        }
        Set<UUID> blockedForRequester = new HashSet<>(blockedPeers);
        blockedForRequester.add(requesterId);
        return presenceService.snapshots(new HashSet<>(peerIds), requesterId, blockedForRequester);
    }

    /** Which of these users are invisible to the requester (blocked in either direction). */
    private Set<UUID> blockedAmong(UUID requesterId, Collection<UUID> candidates) {
        if (candidates.isEmpty()) {
            return Set.of();
        }
        Set<UUID> invisible = blockQueryService.usersInvisibleTo(requesterId);
        Set<UUID> result = new HashSet<>();
        for (UUID candidate : candidates) {
            if (invisible.contains(candidate)) {
                result.add(candidate);
            }
        }
        return result;
    }

    private UUID counterpart(Conversation conversation, UUID requesterId) {
        for (ConversationParticipant participant : conversation.getParticipants()) {
            if (!participant.getUserId().equals(requesterId)) {
                return participant.getUserId();
            }
        }
        return null;
    }
}
