package com.realtimechat.common.web;

import java.util.UUID;

/**
 * The authenticated principal for both HTTP and STOMP requests.
 *
 * <p>Everything the application needs to authorize an action, and nothing more: no email, no
 * password hash, no session token. A controller or {@code @MessageMapping} handler must derive the
 * acting user from this object and never from a request body or path variable.
 *
 * @param userId    the authenticated user's id (never taken from the client)
 * @param username  the user's public handle (for logging and presence display)
 * @param sessionId the session row id, so "log out all devices" can revoke this connection
 */
public record AuthenticatedUser(UUID userId, String username, UUID sessionId) {

    @Override
    public String toString() {
        // Deliberately excludes the session id to keep log lines free of correlatable secrets.
        return "AuthenticatedUser[" + userId + ", @" + username + "]";
    }
}
