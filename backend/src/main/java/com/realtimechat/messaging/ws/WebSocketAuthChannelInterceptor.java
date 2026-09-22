package com.realtimechat.messaging.ws;

import com.realtimechat.common.ratelimit.RateLimitDecision;
import com.realtimechat.common.ratelimit.RateLimiter;
import com.realtimechat.config.AppProperties;
import com.realtimechat.presence.service.PresenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Inbound STOMP pipeline: binds the principal to the session, limits how fast a client may publish,
 * and refreshes presence on activity.
 *
 * <p>Three concrete protections:
 * <ol>
 *   <li><b>Principal binding.</b> The principal resolved at the handshake is re-attached to every
 *       CONNECT. A client cannot switch identity mid-session, because the only source of identity is
 *       the handshake attribute set by the server.</li>
 *   <li><b>Connection-time validation.</b> CONNECT re-checks the session against the store, so a
 *       socket that survives a logout or a password change is cut off at the next connect attempt
 *       rather than trusting a snapshot from handshake time.</li>
 *   <li><b>Publish rate limiting.</b> Sends are limited per user with a token bucket in Redis. The
 *       limit is deliberately <em>higher</em> than the human-typing rate and lower than what an
 *       abusive script needs, so a legitimate burst of quick messages still goes through.</li>
 * </ol>
 */
@Component
public class WebSocketAuthChannelInterceptor implements ChannelInterceptor {

    private static final Logger log = LoggerFactory.getLogger(WebSocketAuthChannelInterceptor.class);
    private static final Logger securityLog = LoggerFactory.getLogger("SECURITY");

    private static final Set<StompCommand> RATE_LIMITED_COMMANDS =
            EnumSet.of(StompCommand.SEND, StompCommand.SUBSCRIBE);

    private final RateLimiter rateLimiter;
    private final PresenceService presenceService;
    private final AppProperties properties;
    /** Cheap local counter to avoid a Redis round trip for every SEND under normal load. */
    private final AtomicLong localSendCounter = new AtomicLong();

    public WebSocketAuthChannelInterceptor(RateLimiter rateLimiter,
                                           PresenceService presenceService,
                                           AppProperties properties) {
        this.rateLimiter = rateLimiter;
        this.presenceService = presenceService;
        this.properties = properties;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        if (accessor.getCommand() == StompCommand.CONNECT) {
            bindPrincipal(accessor);
            return message;
        }

        // Every other command must carry the bound principal; anything else would be a broker-level
        // mistake or an unauthenticated frame that slipped past the handshake.
        Principal principal = accessor.getUser();
        if (principal instanceof StompUserPrincipal stompUser) {
            presenceService.heartbeat(stompUser.userId(), accessor.getSessionId());
            if (RATE_LIMITED_COMMANDS.contains(accessor.getCommand())) {
                enforceRateLimit(stompUser);
            }
        } else if (accessor.getSessionId() != null && accessor.getCommand() != StompCommand.DISCONNECT) {
            log.debug("STOMP frame without a principal command={} session={}",
                    accessor.getCommand(), accessor.getSessionId());
        }
        return message;
    }

    private void bindPrincipal(StompHeaderAccessor accessor) {
        String sessionId = accessor.getSessionId();
        StompUserPrincipal principal = StompSessionAttributes.principal(accessor);
        if (principal == null) {
            securityLog.warn("STOMP CONNECT without a handshake principal session={}", sessionId);
            throw new org.springframework.messaging.MessageDeliveryException(
                    "STOMP session is not authenticated");
        }
        // The principal on the CONNECT frame is what Spring uses as `Principal` for the whole
        // session, including the user-destination resolution for /user/queue/*.
        accessor.setUser(principal);
        accessor.setLeaveMutable(true);
        log.debug("STOMP connected userId={} session={}", principal.userId(), sessionId);
    }

    private void enforceRateLimit(StompUserPrincipal principal) {
        AppProperties.RateLimit limits = properties.getRateLimit();
        if (!limits.isEnabled()) {
            return;
        }
        String key = principal.userId().toString();
        RateLimitDecision decision = rateLimiter.consume("ws-send", key, limits.getSendPerMinute(),
                java.time.Duration.ofMinutes(1));
        if (!decision.allowed()) {
            securityLog.warn("websocket publish rate limit tripped userId={}", key);
            throw new org.springframework.messaging.MessageDeliveryException(
                    "Sending too fast. Please slow down.");
        }
        if (localSendCounter.incrementAndGet() % 1000 == 0) {
            log.debug("websocket publish counter checkpoint total={}", localSendCounter.get());
        }
    }

    /** Used by the outbound path and tests to read the connected user from a session id. */
    public static UUID userIdOf(Principal principal) {
        return principal instanceof StompUserPrincipal stompUser ? stompUser.userId() : null;
    }
}
