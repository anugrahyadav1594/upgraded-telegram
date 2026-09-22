package com.realtimechat.presence.ws;

import com.realtimechat.messaging.ws.StompUserPrincipal;
import com.realtimechat.presence.service.PresenceService;
import com.realtimechat.common.realtime.LocalSessionRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

import java.util.UUID;

/**
 * Drives presence from the WebSocket session lifecycle.
 *
 * <p>The principal is the one resolved at the handshake, so presence transitions can never be
 * triggered for a user the connection does not actually belong to.
 *
 * <p>Disconnect handling is defensive: a dropped TCP connection (mobile network change, laptop lid,
 * crashed process) usually arrives as a disconnect event, but if it does not, the Redis TTL expires
 * the presence key and the user is treated as offline — the system self-heals rather than showing a
 * permanently green dot.
 */
@Component
public class PresenceSessionEventListener {

    private static final Logger log = LoggerFactory.getLogger(PresenceSessionEventListener.class);

    private final PresenceService presenceService;
    private final LocalSessionRegistry sessionRegistry;

    public PresenceSessionEventListener(PresenceService presenceService, LocalSessionRegistry sessionRegistry) {
        this.presenceService = presenceService;
        this.sessionRegistry = sessionRegistry;
    }

    @EventListener
    public void onConnected(SessionConnectedEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        UUID userId = userIdOf(accessor);
        if (userId == null) {
            return;
        }
        String sessionId = accessor.getSessionId();
        sessionRegistry.register(sessionId, userId);
        boolean becameOnline = presenceService.onConnected(userId, sessionId);
        log.debug("websocket connected userId={} sessionId={} becameOnline={}", userId, sessionId, becameOnline);
    }

    @EventListener
    public void onDisconnected(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        UUID userId = userIdOf(accessor);
        if (userId == null) {
            return;
        }
        String sessionId = accessor.getSessionId();
        UUID lastSessionOf = sessionRegistry.unregister(sessionId);
        presenceService.onDisconnected(userId, sessionId);
        log.debug("websocket disconnected userId={} sessionId={} lastLocalSession={}",
                userId, sessionId, lastSessionOf != null);
    }

    /** Keeps presence fresh whenever a client interacts (a subscribe is a cheap liveness signal). */
    @EventListener
    public void onSubscribe(SessionSubscribeEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        UUID userId = userIdOf(accessor);
        if (userId != null) {
            presenceService.heartbeat(userId, accessor.getSessionId());
        }
    }

    private UUID userIdOf(StompHeaderAccessor accessor) {
        java.security.Principal principal = accessor.getUser();
        if (principal instanceof StompUserPrincipal stompUser) {
            return stompUser.userId();
        }
        return null;
    }
}
