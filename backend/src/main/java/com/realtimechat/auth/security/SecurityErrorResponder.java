package com.realtimechat.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.realtimechat.common.error.ApiErrorResponse;
import com.realtimechat.common.error.ErrorCode;
import com.realtimechat.common.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Writes the standard error envelope for failures raised inside the security filter chain (before
 * {@code @RestControllerAdvice} can see them).
 *
 * <p>Without this, a 401 from Spring Security would produce an empty HTML body and the frontend
 * would have to special-case it — the API contract must hold for <em>every</em> response.
 */
@Component
public class SecurityErrorResponder {

    private static final Logger log = LoggerFactory.getLogger(SecurityErrorResponder.class);

    private final ObjectMapper objectMapper;

    public SecurityErrorResponder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void write(HttpServletResponse response,
                      HttpServletRequest request,
                      ErrorCode code,
                      String message) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(code.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        ApiErrorResponse body = ApiErrorResponse.of(code, message, request.getRequestURI(),
                RequestIdFilter.currentRequestId());
        objectMapper.writeValue(response.getWriter(), body);
    }

    /** Used by the WebSocket handshake interceptor, which has no response body to write into. */
    public void logHandshakeRejection(HttpServletRequest request, ErrorCode code) {
        log.warn("websocket handshake rejected code={} path={} requestId={}", code,
                request.getRequestURI(), RequestIdFilter.currentRequestId());
    }
}
