package com.realtimechat.messaging.ws;

import com.realtimechat.auth.service.SessionService;
import com.realtimechat.common.realtime.LocalSessionRegistry;
import com.realtimechat.common.web.AuthenticatedUser;
import com.realtimechat.config.AppProperties;
import com.realtimechat.auth.security.SessionCookieFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;
import java.util.Optional;

/**
 * Authenticates the WebSocket upgrade request.
 *
 * <p>Why the handshake and not the first STOMP frame: the browser sends the {@code HttpOnly} session
 * cookie with the upgrade request, so identity is established before any STOMP code runs. An
 * unauthenticated client is rejected with {@code 401} and never reaches the message broker — there is
 * no window in which an anonymous connection exists.
 *
 * <p>Threading note: the handshake interceptor runs on a servlet request thread but stores the
 * principal in the <b>WebSocket session attributes</b> (not in a ThreadLocal or the
 * {@code SecurityContext}), because subsequent frames arrive on different threads with no servlet
 * request to inherit from.
 */
@Component
public class WebSocketAuthHandshakeInterceptor implements HandshakeInterceptor {

    private static final Logger log = LoggerFactory.getLogger(WebSocketAuthHandshakeInterceptor.class);
    private static final Logger securityLog = LoggerFactory.getLogger("SECURITY");

    /** Session attribute keys (constants so the channel interceptor cannot misspell them). */
    public static final String ATTR_PRINCIPAL = "stompPrincipal";
    public static final String ATTR_HANDSHAKE_TIME = "handshakeTime";

    private final SessionService sessionService;
    private final SessionCookieFactory cookieFactory;
    private final LocalSessionRegistry sessionRegistry;
    private final AppProperties properties;

    public WebSocketAuthHandshakeInterceptor(SessionService sessionService,
                                             SessionCookieFactory cookieFactory,
                                             LocalSessionRegistry sessionRegistry,
                                             AppProperties properties) {
        this.sessionService = sessionService;
        this.cookieFactory = cookieFactory;
        this.sessionRegistry = sessionRegistry;
        this.properties = properties;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {
        if (!(request instanceof ServletServerHttpRequest servletRequest)) {
            reject(response, HttpStatus.BAD_REQUEST, "Handshake must be an HTTP request");
            return false;
        }
        var httpRequest = servletRequest.getServletRequest();

        Optional<String> token = cookieFactory.readSessionToken(httpRequest);
        if (token.isEmpty()) {
            // No cookie at all: the client is simply not logged in. Not logged as a security event,
            // because a logged-out tab reconnecting would otherwise fill the log with warnings.
            log.debug("websocket handshake without a session cookie");
            reject(response, HttpStatus.UNAUTHORIZED, "Authentication required");
            return false;
        }

        Optional<SessionService.SessionIdentity> identity = sessionService.resolve(token.get());
        if (identity.isEmpty()) {
            securityLog.warn("websocket handshake with an invalid or expired session");
            reject(response, HttpStatus.UNAUTHORIZED, "Session expired");
            return false;
        }
        SessionService.SessionIdentity session = identity.get();

        // Concurrency cap: a small number of tabs/devices per account is normal, an unbounded number
        // is either a bug or an attempt to exhaust server memory.
        int existing = sessionRegistry.sessionCountFor(session.userId());
        if (existing >= properties.getWebsocket().getMaxSessionsPerUser()) {
            log.info("websocket handshake refused: session limit reached userId={} existing={}",
                    session.userId(), existing);
            reject(response, HttpStatus.TOO_MANY_REQUESTS, "Too many active connections");
            return false;
        }

        StompUserPrincipal principal = new StompUserPrincipal(session.userId(), session.username(),
                session.sessionId());
        attributes.put(ATTR_PRINCIPAL, principal);
        attributes.put(ATTR_HANDSHAKE_TIME, System.currentTimeMillis());
        attributes.put("httpSessionId", httpRequest.getSession(false) == null
                ? null : httpRequest.getSession(false).getId());
        log.debug("websocket handshake accepted userId={} sessionId={}", session.userId(), session.sessionId());
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        if (exception != null) {
            log.warn("websocket handshake failed after acceptance", exception);
        }
    }

    private void reject(ServerHttpResponse response, HttpStatus status, String reason) {
        response.setStatusCode(status);
        // A plain body: the client's StompClient reports the status code, which is all the UI needs
        // to decide between "reconnect" and "go to login".
        response.getHeaders().add("X-Handshake-Rejection", reason.replace(' ', '-'));
    }
}
