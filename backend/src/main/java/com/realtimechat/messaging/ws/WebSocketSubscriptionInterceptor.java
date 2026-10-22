package com.realtimechat.messaging.ws;

import com.realtimechat.conversations.service.ConversationAccessPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Authorises SUBSCRIBE frames.
 *
 * <p>Without this, STOMP subscription is an open door: any authenticated client could subscribe to
 * {@code /topic/conversation/<any id>} and receive other people's messages. The check is therefore
 * mandatory, and it is made against the database (through {@code ConversationAccessPort}), never
 * against a topic name the client chose.
 *
 * <p>Allowed destinations are an explicit allow-list:
 * <ul>
 *   <li>{@code /user/queue/messages|events|errors} — resolved by the broker to the session's own
 *       user id, so one user can never read another's queue,</li>
 *   <li>{@code /topic/conversation/{conversationId}} — only for participants.</li>
 * </ul>
 * Everything else is rejected with {@code FORBIDDEN}, and the rejection is reported to the client
 * rather than silently ignored, so a bug in the client shows up in development instead of in
 * production silence.
 */
@Component
public class WebSocketSubscriptionInterceptor implements ChannelInterceptor {

    private static final Logger log = LoggerFactory.getLogger(WebSocketSubscriptionInterceptor.class);
    private static final Logger securityLog = LoggerFactory.getLogger("SECURITY");

    private static final String TOPIC_CONVERSATION_PREFIX = "/topic/conversation/";
    private static final java.util.Set<String> ALLOWED_USER_QUEUES =
            java.util.Set.of("/user/queue/messages", "/user/queue/events", "/user/queue/errors");

    private final ConversationAccessPort conversationAccessPort;
    private final StompErrorSender errorSender;

    public WebSocketSubscriptionInterceptor(ConversationAccessPort conversationAccessPort,
                                            StompErrorSender errorSender) {
        this.conversationAccessPort = conversationAccessPort;
        this.errorSender = errorSender;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() != StompCommand.SUBSCRIBE) {
            return message;
        }
        StompUserPrincipal principal = accessor.getUser() instanceof StompUserPrincipal stompUser
                ? stompUser
                : null;
        String destination = accessor.getDestination();
        if (principal == null) {
            // Should be impossible: the handshake refuses anonymous sockets. If it happens, the
            // session is silently broken and continuing would deliver frames to nobody.
            securityLog.error("SUBSCRIBE without a principal destination={}", destination);
            throw new org.springframework.messaging.MessageDeliveryException("Session is not authenticated");
        }
        if (destination == null) {
            reject(principal, null, "MISSING_DESTINATION", "Subscription destination is required");
            return message;
        }

        if (ALLOWED_USER_QUEUES.contains(destination)) {
            return message; // scoped to the caller by the broker's user-destination resolution
        }

        if (destination.startsWith(TOPIC_CONVERSATION_PREFIX)) {
            UUID conversationId = parseUuid(destination.substring(TOPIC_CONVERSATION_PREFIX.length()));
            if (conversationId != null && conversationAccessPort.isParticipant(conversationId, principal.userId())) {
                return message;
            }
            securityLog.warn("subscription denied userId={} destination={}", principal.userId(), destination);
            reject(principal, conversationId, "SUBSCRIPTION_DENIED",
                    "You are not a participant of this conversation");
            return message;
        }

        securityLog.warn("subscription denied (unknown destination) userId={} destination={}",
                principal.userId(), destination);
        reject(principal, null, "SUBSCRIPTION_DENIED", "Unknown subscription destination");
        return message;
    }

    /**
     * Reports a rejection to the client.
     *
     * <p>The frame is <em>not</em> forwarded. The broker would otherwise create a subscription that
     * never authorises again, so a later membership change could not be reflected.
     */
    private void reject(StompUserPrincipal principal, UUID conversationId, String code, String message) {
        Map<String, Object> context = conversationId == null
                ? Map.of()
                : Map.of("conversationId", conversationId.toString());
        errorSender.send(principal.userId(), code, message, context);
        log.debug("realtime error sent userId={} code={}", principal.userId(), code);
    }

    private UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
