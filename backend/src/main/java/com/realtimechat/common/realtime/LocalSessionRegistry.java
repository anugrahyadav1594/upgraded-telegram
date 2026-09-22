package com.realtimechat.common.realtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks which STOMP sessions are connected to <b>this</b> instance.
 *
 * <p>WebSocket fan-out is two-phase: a Redis Pub/Sub message reaches every instance, and each
 * instance delivers only to the sessions it actually holds. That is what makes the realtime layer
 * multi-instance correct without sticky sessions for delivery, and why this registry stays
 * in-memory (it describes a process, so it must not be shared).
 */
@Component
public class LocalSessionRegistry {

    private static final Logger log = LoggerFactory.getLogger(LocalSessionRegistry.class);

    private final SimpMessagingTemplate messagingTemplate;
    private final Map<UUID, Set<String>> sessionsByUser = new ConcurrentHashMap<>();
    private final Map<String, UUID> userBySession = new ConcurrentHashMap<>();

    public LocalSessionRegistry(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void register(String stompSessionId, UUID userId) {
        sessionsByUser.computeIfAbsent(userId, key -> ConcurrentHashMap.newKeySet()).add(stompSessionId);
        userBySession.put(stompSessionId, userId);
    }

    /** Removes a session; returns the owning user when this was their last local session. */
    public UUID unregister(String stompSessionId) {
        UUID userId = userBySession.remove(stompSessionId);
        if (userId == null) {
            return null;
        }
        Set<String> sessions = sessionsByUser.get(userId);
        if (sessions != null) {
            sessions.remove(stompSessionId);
            if (sessions.isEmpty()) {
                sessionsByUser.remove(userId);
                return userId;
            }
        }
        return null;
    }

    public boolean isUserLocal(UUID userId) {
        Set<String> sessions = sessionsByUser.get(userId);
        return sessions != null && !sessions.isEmpty();
    }

    public int localSessionCount() {
        return userBySession.size();
    }

    public int localUserCount() {
        return sessionsByUser.size();
    }

    public Set<UUID> localUsers() {
        return Set.copyOf(sessionsByUser.keySet());
    }

    public int sessionCountFor(UUID userId) {
        Set<String> sessions = sessionsByUser.get(userId);
        return sessions == null ? 0 : sessions.size();
    }

    /**
     * Delivers one already-serialised frame to every local session of the given users.
     *
     * @param recipients user ids to deliver to
     * @param type       event type (selects the user queue)
     * @param destination explicit destination override (topic broadcasts)
     * @param payload    JSON string produced by the broadcaster
     */
    public void deliver(Collection<UUID> recipients, RealtimeEventType type, String destination, String payload) {
        if (destination != null) {
            try {
                messagingTemplate.convertAndSend(destination, payload);
            } catch (RuntimeException ex) {
                log.warn("topic delivery failed destination={} type={}", destination, type, ex);
            }
            return;
        }
        for (UUID userId : recipients) {
            if (!isUserLocal(userId)) {
                continue; // this instance holds no session for the user
            }
            try {
                messagingTemplate.convertAndSendToUser(
                        userId.toString(), type.target().destination(), payload);
            } catch (RuntimeException ex) {
                // A failure to push a frame is not a correctness problem: the data is in
                // PostgreSQL and the client resynchronises. Log and continue with other users so
                // one broken session cannot stall the fan-out.
                log.warn("realtime delivery failed userId={} type={}", userId, type, ex);
            }
        }
    }
}
