package com.realtimechat.notifications.service;

import com.realtimechat.common.error.InvalidRequestException;
import com.realtimechat.common.error.NotFoundException;
import com.realtimechat.common.pagination.Cursor;
import com.realtimechat.common.pagination.CursorCodec;
import com.realtimechat.common.pagination.PageResponse;
import com.realtimechat.common.realtime.RealtimeBroadcaster;
import com.realtimechat.common.realtime.RealtimeEvent;
import com.realtimechat.common.realtime.RealtimeEventType;
import com.realtimechat.common.util.TimeProvider;
import com.realtimechat.conversations.repository.ConversationParticipantRepository;
import com.realtimechat.notifications.dto.NotificationResponse;
import com.realtimechat.notifications.dto.NotificationSettingsResponse;
import com.realtimechat.notifications.entity.Notification;
import com.realtimechat.notifications.entity.NotificationSettings;
import com.realtimechat.notifications.repository.NotificationRepository;
import com.realtimechat.notifications.repository.NotificationSettingsRepository;
import com.realtimechat.users.service.NotificationCountPort;
import com.realtimechat.users.service.ProfilePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * In-app notifications: creation, listing and read state.
 *
 * <p>Notifications are derived data and are treated that way:
 * <ul>
 *   <li><b>Creation never fails a user action.</b> It happens after the originating transaction
 *       commits (see {@code NotificationListener}) and is wrapped so a failure is logged, not thrown —
 *       a message that was successfully stored must not be reported as failed because a badge could
 *       not be written.</li>
 *   <li><b>Muting is respected at creation time.</b> A muted conversation produces no notification at
 *       all, rather than a notification the client has to hide: not writing it is cheaper and cannot
 *       be bypassed by a client.</li>
 *   <li><b>Ownership is part of every query.</b> Marking read and deleting filter on the user id, so a
 *       guessed notification id cannot touch somebody else's rows.</li>
 * </ul>
 */
@Service
public class NotificationService implements NotificationCountPort {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final int MAX_PAGE = 100;

    private final NotificationRepository notificationRepository;
    private final NotificationSettingsRepository settingsRepository;
    private final ConversationParticipantRepository participantRepository;
    private final ProfilePort profilePort;
    private final RealtimeBroadcaster broadcaster;
    private final TimeProvider timeProvider;
    private final CursorCodec cursorCodec;

    public NotificationService(NotificationRepository notificationRepository,
                               NotificationSettingsRepository settingsRepository,
                               ConversationParticipantRepository participantRepository,
                               ProfilePort profilePort,
                               RealtimeBroadcaster broadcaster,
                               TimeProvider timeProvider,
                               CursorCodec cursorCodec) {
        this.notificationRepository = notificationRepository;
        this.settingsRepository = settingsRepository;
        this.participantRepository = participantRepository;
        this.profilePort = profilePort;
        this.broadcaster = broadcaster;
        this.timeProvider = timeProvider;
        this.cursorCodec = cursorCodec;
    }

    // ------------------------------------------------------------------ creation

    /**
     * Creates a new-message notification for one recipient.
     *
     * <p>Checks the per-conversation mute flag first (a muted conversation is silent by definition),
     * then the user's global preference, then writes the row and pushes a realtime frame.
     */
    public void createMessageNotification(UUID recipientId, UUID conversationId, UUID messageId,
                                          UUID actorId, String preview) {
        try {
            if (isConversationMuted(recipientId, conversationId)) {
                return;
            }
            NotificationSettings settings = settings(recipientId);
            if (!settings.isNewMessage()) {
                return;
            }
            Notification notification = notificationRepository.save(
                    Notification.newMessage(recipientId, conversationId, messageId, actorId, preview));
            push(recipientId, notification);
        } catch (RuntimeException ex) {
            // Never propagate: notification failure must not fail a send that already committed.
            log.warn("could not create message notification recipient={} conversation={}",
                    recipientId, conversationId, ex);
        }
    }

    /** Moderation outcome notice: the reporter learns that their report was handled. */
    @Transactional
    public void notifyModerationUpdate(UUID userId, String summary) {
        NotificationSettings settings = settings(userId);
        if (!settings.isModerationUpdates()) {
            return;
        }
        Notification notification = notificationRepository.save(Notification.moderation(userId, summary));
        push(userId, notification);
    }

    private void push(UUID userId, Notification notification) {
        NotificationResponse dto = toResponse(notification, Map.of());
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("notification", dto);
        payload.put("unreadCount", countUnread(userId));
        broadcaster.publish(RealtimeEvent.toUser(RealtimeEventType.NOTIFICATION_NEW, payload, userId));
    }

    private boolean isConversationMuted(UUID userId, UUID conversationId) {
        return participantRepository.findByIdConversationIdAndIdUserId(conversationId, userId)
                .map(participant -> participant.isMuted())
                .orElse(true); // no participant row: not a member, so nothing to notify about
    }

    // ------------------------------------------------------------------ listing

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> list(UUID userId, String rawCursor, int limit, boolean unreadOnly) {
        int pageSize = Math.min(Math.max(limit, 1), MAX_PAGE);
        Cursor cursor = cursorCodec.decode(rawCursor);
        List<Notification> rows = cursor == null
                ? notificationRepository.findPage(userId, unreadOnly, PageRequest.of(0, pageSize + 1))
                : notificationRepository.findPageAfter(userId, unreadOnly, cursor.instantKey(), cursor.id(),
                        PageRequest.of(0, pageSize + 1));

        Map<UUID, ProfilePort.UserSummary> actors = actorsOf(rows);
        List<NotificationResponse> items = rows.stream().map(row -> toResponse(row, actors)).toList();
        return PageResponse.of(items, pageSize,
                response -> Cursor.ofInstant(response.createdAt(), response.id()), cursorCodec);
    }

    /** Actors are resolved in one batch so a page of notifications never issues a query per row. */
    private Map<UUID, ProfilePort.UserSummary> actorsOf(List<Notification> notifications) {
        Collection<UUID> actorIds = new ArrayList<>();
        for (Notification notification : notifications) {
            if (notification.getActorId() != null) {
                actorIds.add(notification.getActorId());
            }
        }
        Map<UUID, ProfilePort.UserSummary> summaries = new HashMap<>(profilePort.summariesOf(actorIds));
        return summaries;
    }

    private NotificationResponse toResponse(Notification notification,
                                            Map<UUID, ProfilePort.UserSummary> actors) {
        ProfilePort.UserSummary actor = notification.getActorId() == null
                ? null
                : actors.get(notification.getActorId());
        return NotificationResponse.from(notification, actor);
    }

    @Override
    @Transactional(readOnly = true)
    public long unreadCount(UUID userId) {
        return countUnread(userId);
    }

    private long countUnread(UUID userId) {
        return notificationRepository.countUnread(userId);
    }

    // ------------------------------------------------------------------ read state

    /**
     * Marks notifications read.
     *
     * <p>An empty (or absent) id list means "mark everything read", which is what the UI's "mark all"
     * action sends. Ids are always intersected with the caller's own rows by the query itself.
     *
     * @return the remaining unread count, so the client can update the badge without a second call
     */
    @Transactional
    public long markRead(UUID userId, List<UUID> ids) {
        Instant now = timeProvider.now();
        if (ids == null || ids.isEmpty()) {
            notificationRepository.markAllRead(userId, now);
        } else {
            notificationRepository.markRead(userId, ids, now);
        }
        return countUnread(userId);
    }

    @Transactional
    public void delete(UUID userId, UUID notificationId) {
        int deleted = notificationRepository.deleteOwned(userId, notificationId);
        if (deleted == 0) {
            // Unknown id and somebody else's id are reported identically: ownership is not disclosed.
            throw new NotFoundException("Notification not found");
        }
    }

    @Transactional
    public int deleteAll(UUID userId) {
        return notificationRepository.deleteAllOwned(userId);
    }

    // ------------------------------------------------------------------ settings

    @Transactional(readOnly = true)
    public NotificationSettings settings(UUID userId) {
        return settingsRepository.findById(userId).orElseGet(() -> NotificationSettings.defaultsFor(userId));
    }

    @Transactional
    public NotificationSettingsResponse updateSettings(UUID userId, boolean newMessage, boolean mentions,
                                                       boolean conversationUpdates, boolean moderationUpdates,
                                                       boolean emailDigest) {
        NotificationSettings settings = settingsRepository.findById(userId)
                .orElseGet(() -> NotificationSettings.defaultsFor(userId));
        settings.update(newMessage, mentions, conversationUpdates, moderationUpdates, emailDigest);
        return NotificationSettingsResponse.from(settingsRepository.save(settings));
    }

    // ------------------------------------------------------------------ retention

    @Transactional
    public int purgeOlderThan(Instant cutoff) {
        int removed = notificationRepository.deleteOlderThan(cutoff);
        if (removed > 0) {
            log.info("pruned {} notifications older than {}", removed, cutoff);
        }
        return removed;
    }

    /** Used by tests to assert on stored rows without exposing the repository. */
    @Transactional(readOnly = true)
    public Optional<Notification> find(UUID id) {
        return notificationRepository.findById(id);
    }

    /** Guard used by the API layer for pagination bounds. */
    public static void requireValidLimit(int limit) {
        if (limit < 1) {
            throw new InvalidRequestException("limit must be positive");
        }
    }
}
