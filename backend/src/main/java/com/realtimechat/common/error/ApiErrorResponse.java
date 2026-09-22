package com.realtimechat.common.error;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * The single error shape returned by every failing REST endpoint.
 *
 * <pre>
 * {
 *   "timestamp": "2026-09-22T10:41:07.812Z",
 *   "status": 400,
 *   "code": "VALIDATION_FAILED",
 *   "message": "The request could not be processed",
 *   "path": "/api/v1/auth/register",
 *   "requestId": "b0d1e6f2-…",
 *   "details": [ { "field": "username", "code": "PATTERN", "message": "…" } ]
 * }
 * </pre>
 *
 * <p>Contains no stack traces, SQL, internal class names or secrets. Correlate with logs using
 * {@code requestId} (also returned in the {@code X-Request-Id} header).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorResponse(
        Instant timestamp,
        int status,
        String code,
        String message,
        String path,
        String requestId,
        List<FieldErrorDetail> details
) {

    public static ApiErrorResponse of(ErrorCode code, String message, String path, String requestId) {
        return new ApiErrorResponse(Instant.now(), code.status().value(), code.name(), message, path, requestId, null);
    }

    public static ApiErrorResponse of(ErrorCode code,
                                      String message,
                                      String path,
                                      String requestId,
                                      List<FieldErrorDetail> details) {
        return new ApiErrorResponse(Instant.now(), code.status().value(), code.name(), message, path, requestId,
                details == null || details.isEmpty() ? null : details);
    }
}
