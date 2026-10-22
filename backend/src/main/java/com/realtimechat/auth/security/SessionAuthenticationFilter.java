package com.realtimechat.auth.security;

import com.realtimechat.auth.service.SessionService;
import com.realtimechat.common.web.AuthenticatedUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Resolves the session cookie into an authenticated {@link AuthenticatedUser}.
 *
 * <p>This filter is the single point where an HTTP request becomes an identity. Everything
 * downstream (controllers, MVC argument resolution, method security) trusts the
 * {@code SecurityContext} and never re-derives the user from client input — which is what makes
 * "never trust a {@code userId} from the frontend" a structural property rather than a guideline.
 *
 * <p>Failure handling is deliberately quiet: an invalid or expired cookie leaves the request
 * anonymous and lets the authorization layer decide (public endpoints still work; protected ones get
 * a 401). Logging every stale cookie would be noise, so only a malformed token is logged.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class SessionAuthenticationFilter extends OncePerRequestFilter {

    private final SessionService sessionService;
    private final SessionCookieFactory cookieFactory;

    public SessionAuthenticationFilter(SessionService sessionService, SessionCookieFactory cookieFactory) {
        this.sessionService = sessionService;
        this.cookieFactory = cookieFactory;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Optional<String> token = cookieFactory.readSessionToken(request);
        if (token.isPresent() && SecurityContextHolder.getContext().getAuthentication() == null) {
            Optional<SessionService.SessionIdentity> session = sessionService.resolve(token.get());
            session.ifPresent(resolved -> {
                AuthenticatedUser principal =
                        new AuthenticatedUser(resolved.userId(), resolved.username(), resolved.sessionId());
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        List.of(new SimpleGrantedAuthority(resolved.role().authority())));
                SecurityContextHolder.getContext().setAuthentication(authentication);
                MDC.put("userId", principal.userId().toString());
            });
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            // The context is request-scoped: never let it leak into a pooled thread.
            SecurityContextHolder.clearContext();
            MDC.remove("userId");
        }
    }

    /** Public endpoints do not need the session lookup; skipping saves a Redis/DB round trip. */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator/health")
                || path.equals("/api/v1/auth/register")
                || path.equals("/api/v1/auth/login")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs");
    }
}
