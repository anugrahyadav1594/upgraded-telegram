package com.realtimechat.common.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/**
 * Resolves the client IP for rate limiting and session metadata.
 *
 * <p>{@code X-Forwarded-For} is only trusted when Spring Boot's
 * {@code server.forward-headers-strategy} has already processed it (the request then carries the
 * resolved remote address). Otherwise the socket address wins — an untrusted header must never be
 * able to bypass a rate limit by claiming a fresh IP on every request.
 */
@Component
public class ClientIpResolver {

    public String resolve(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        return remote == null ? "unknown" : remote;
    }
}
