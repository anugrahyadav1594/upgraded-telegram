package com.realtimechat.messaging.ws;

import com.realtimechat.common.web.AuthenticatedUser;

import java.security.Principal;
import java.util.UUID;

/**
 * The principal attached to every authenticated STOMP session.
 *
 * <p>WebSocket handlers <b>never</b> read a user id from the frame payload: they read it from here.
 * That is the single most important authorisation property of the realtime layer — a client can put
 * {@code "senderId": "<someone else>"} in a SEND body all day and it is simply ignored, because the
 * payload is a request, not an identity.
 *
 * @param userId    authenticated account
 * @param username  handle at connect time (used for logging and display)
 * @param sessionId server-issued session that authenticated the socket, so it can be revoked live
 */
public record StompUserPrincipal(UUID userId, String username, UUID sessionId) implements Principal {

    @Override
    public String getName() {
        return userId.toString();
    }

    public static StompUserPrincipal from(AuthenticatedUser user) {
        return new StompUserPrincipal(user.userId(), user.username(), user.sessionId());
    }

    @Override
    public String toString() {
        // Never include the session id: principals end up in debug logs and error messages.
        return "StompUserPrincipal[userId=" + userId + ", username=" + username + "]";
    }
}
