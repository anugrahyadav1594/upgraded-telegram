package com.realtimechat.messaging.ws;

import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.web.socket.WebSocketSession;

import java.security.Principal;
import java.util.Map;

/**
 * Reads the handshake attributes from a STOMP session.
 *
 * <p>Spring exposes the WebSocket session attributes on the {@code simpSessionAttributes} header.
 * Centralising the access keeps the two interceptors honest about the same key and gives one place
 * to handle the "attributes are absent" case (which happens for a session created without a
 * handshake, e.g. in a unit test).
 */
final class StompSessionAttributes {

    private StompSessionAttributes() {
    }

    static StompUserPrincipal principal(StompHeaderAccessor accessor) {
        Principal existing = accessor.getUser();
        if (existing instanceof StompUserPrincipal stompUser) {
            return stompUser;
        }
        Map<String, Object> attributes = accessor.getSessionAttributes();
        if (attributes == null) {
            return null;
        }
        Object principal = attributes.get(WebSocketAuthHandshakeInterceptor.ATTR_PRINCIPAL);
        return principal instanceof StompUserPrincipal stompUser ? stompUser : null;
    }

    static String sessionId(WebSocketSession session) {
        return session == null ? null : session.getId();
    }
}
