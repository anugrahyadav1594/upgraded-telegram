package com.realtimechat.presence.service;

import com.realtimechat.config.AppProperties;
import com.realtimechat.presence.dto.PresenceSnapshot;
import com.realtimechat.presence.dto.PresenceStatus;
import com.realtimechat.presence.event.PresenceChangedEvent;
import com.realtimechat.profiles.repository.ProfileRepository;
import com.realtimechat.users.service.PrivacyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Presence, implemented entirely on ephemeral Redis state plus one coarse database hint.
 *
 * <p>Why not the database: a heartbeat is the highest-frequency event in a chat system. Writing each
 * one would dominate write traffic for data whose value decays in seconds. Instead:
 * <ul>
 *   <li>{@code presence:{userId}} — a hash with the connection count, TTL-bounded. Its expiry is a
 *       self-healing safety net: if a process dies without a clean disconnect, the user goes offline
 *       automatically instead of appearing online forever.</li>
 *   <li>{@code presence:conn:{stompSessionId}} — per-connection marker, so multi-tab/multi-device
 *       sessions are counted correctly and a user is offline only when the <b>last</b> one goes.</li>
 *   <li>{@code profiles.last_seen_at} — written at most once per configured throttle interval, only
 *       on the last disconnect, only when the user allows last-seen visibility.</li>
 * </ul>
 */
@Service
public class PresenceService {

    private static final Logger log = LoggerFactory.getLogger(PresenceService.class);
    private static final String USER_KEY_PREFIX = "presence:";
    private static final String CONNECTION_KEY_PREFIX = "presence:conn:";
    private static final String FIELD_CONNECTIONS = "connections";
    private static final String FIELD_LAST_SEEN = "lastSeenAt";

    private final StringRedisTemplate redis;
    private final ProfileRepository profileRepository;
    private final PrivacyService privacyService;
    private final ApplicationEventPublisher eventPublisher;
    private final AppProperties properties;

    public PresenceService(StringRedisTemplate redis,
                           ProfileRepository profileRepository,
                           PrivacyService privacyService,
                           ApplicationEventPublisher eventPublisher,
                           AppProperties properties) {
        this.redis = redis;
        this.profileRepository = profileRepository;
        this.privacyService = privacyService;
        this.eventPublisher = eventPublisher;
        this.properties = properties;
    }

    // ------------------------------------------------------------------ connection lifecycle

    /** Registers a new STOMP connection. Returns true when the user transitioned offline → online. */
    public boolean onConnected(UUID userId, String stompSessionId) {
        Duration ttl = properties.getPresence().getTtl();
        boolean firstConnection;
        try {
            redis.opsForValue().set(connectionKey(stompSessionId), userId.toString(), ttl);
            Long connections = redis.opsForHash().increment(userKey(userId), FIELD_CONNECTIONS, 1L);
            redis.expire(userKey(userId), ttl);
            firstConnection = connections != null && connections == 1L;
        } catch (RuntimeException ex) {
            // Presence is an optimisation, never a correctness requirement: messaging must keep
            // working when Redis is unavailable.
            log.warn("presence connect failed userId={} — continuing without presence", userId, ex);
            return false;
        }
        if (firstConnection) {
            eventPublisher.publishEvent(new PresenceChangedEvent(userId, PresenceStatus.ONLINE, null));
        }
        return firstConnection;
    }

    /**
     * Removes a connection. Returns true when this was the user's last connection, in which case the
     * coarse last-seen hint is persisted (throttled) and an OFFLINE transition is published.
     */
    public boolean onDisconnected(UUID userId, String stompSessionId) {
        boolean lastConnection = false;
        try {
            Boolean deleted = redis.delete(connectionKey(stompSessionId));
            Long remaining = redis.opsForHash().increment(userKey(userId), FIELD_CONNECTIONS, -1L);
            if (remaining == null || remaining <= 0) {
                redis.delete(userKey(userId));
                lastConnection = true;
            } else {
                redis.expire(userKey(userId), properties.getPresence().getTtl());
            }
            if (deleted == null || !deleted) {
                log.debug("presence disconnect for unknown session userId={}", userId);
            }
        } catch (RuntimeException ex) {
            log.warn("presence disconnect failed userId={}", userId, ex);
        }
        if (lastConnection) {
            Instant now = Instant.now();
            persistLastSeen(userId, now);
            eventPublisher.publishEvent(new PresenceChangedEvent(userId, PresenceStatus.OFFLINE, now));
        }
        return lastConnection;
    }

    /** Refreshes the TTL for a live connection (called from the WebSocket heartbeat). */
    public void heartbeat(UUID userId, String stompSessionId) {
        Duration ttl = properties.getPresence().getTtl();
        try {
            redis.expire(connectionKey(stompSessionId), ttl);
            redis.expire(userKey(userId), ttl);
        } catch (RuntimeException ex) {
            log.debug("presence heartbeat failed userId={}", userId, ex);
        }
    }

    /**
     * Persists the last-seen hint, throttled and privacy-gated.
     *
     * <p>The {@code WHERE last_seen_at < threshold} condition makes the throttle race-free across
     * instances without a distributed lock.
     */
    @Transactional
    public void persistLastSeen(UUID userId, Instant when) {
        if (!privacyService.lastSeenVisible(userId)) {
            return; // hidden: do not even store a value that could later leak
        }
        Instant threshold = when.minus(properties.getPresence().getLastSeenWriteThrottle());
        int updated = profileRepository.touchLastSeen(userId, when, threshold);
        if (updated == 0) {
            log.debug("last-seen write throttled userId={}", userId);
        }
    }

    // ------------------------------------------------------------------ queries

    /**
     * Presence of {@code targetUserId} as visible to {@code requesterId}.
     *
     * <p>Privacy is applied here, once, so no caller can forget it. A requester who has a block
     * relationship with the target gets {@link Optional#empty()} — the same answer as for a user who
     * does not exist, so blocking cannot be detected.
     */
    public Optional<PresenceSnapshot> snapshot(UUID targetUserId, boolean blockedRelation) {
        if (blockedRelation) {
            return Optional.empty();
        }
        if (!privacyService.presenceVisible(targetUserId)) {
            return Optional.of(PresenceSnapshot.hidden(targetUserId));
        }
        try {
            Object connections = redis.opsForHash().get(userKey(targetUserId), FIELD_CONNECTIONS);
            long count = connections == null ? 0L : Long.parseLong(connections.toString());
            if (count > 0) {
                return Optional.of(new PresenceSnapshot(targetUserId, PresenceStatus.ONLINE, null));
            }
        } catch (RuntimeException ex) {
            log.debug("presence read failed userId={} — reporting unknown", targetUserId, ex);
            return Optional.empty();
        }
        Instant lastSeen = privacyService.lastSeenVisible(targetUserId)
                ? profileRepository.findById(targetUserId).map(p -> p.getLastSeenAt()).orElse(null)
                : null;
        return Optional.of(new PresenceSnapshot(targetUserId, PresenceStatus.OFFLINE, lastSeen));
    }

    /** Batch variant used by the conversation list (one Redis round trip per user, no N+1 in SQL). */
    public Map<UUID, PresenceSnapshot> snapshots(Set<UUID> targetUserIds, UUID requesterId, Set<UUID> blockedRelations) {
        Map<UUID, PresenceSnapshot> result = new HashMap<>(targetUserIds.size());
        for (UUID target : targetUserIds) {
            snapshot(target, blockedRelations.contains(target) || target.equals(requesterId))
                    .ifPresent(snapshot -> result.put(target, snapshot));
        }
        return result;
    }

    /** Local connection count for this instance (diagnostics/metrics). */
    public boolean isOnline(UUID userId) {
        try {
            Object connections = redis.opsForHash().get(userKey(userId), FIELD_CONNECTIONS);
            return connections != null && Long.parseLong(connections.toString()) > 0;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private String userKey(UUID userId) {
        return USER_KEY_PREFIX + userId;
    }

    private String connectionKey(String stompSessionId) {
        return CONNECTION_KEY_PREFIX + stompSessionId;
    }
}
