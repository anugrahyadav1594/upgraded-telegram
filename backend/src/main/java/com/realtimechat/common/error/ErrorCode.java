package com.realtimechat.common.error;

import org.springframework.http.HttpStatus;

/**
 * Stable, machine-readable error codes.
 *
 * <p>Clients branch on {@link #name()} (or the {@code code} field of the error response), never on
 * the human-readable message. The HTTP status is derived here so a code cannot be returned with an
 * inconsistent status by accident. See {@code docs/api-specification.md §1}.
 */
public enum ErrorCode {

    VALIDATION_FAILED(HttpStatus.BAD_REQUEST),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED),
    FORBIDDEN(HttpStatus.FORBIDDEN),
    CSRF_INVALID(HttpStatus.FORBIDDEN),
    BLOCKED(HttpStatus.FORBIDDEN),
    NOT_FOUND(HttpStatus.NOT_FOUND),
    USERNAME_TAKEN(HttpStatus.CONFLICT),
    EMAIL_TAKEN(HttpStatus.CONFLICT),
    CONFLICT(HttpStatus.CONFLICT),
    UNSUPPORTED_MESSAGE_TYPE(HttpStatus.UNPROCESSABLE_ENTITY),
    MESSAGE_NOT_EDITABLE(HttpStatus.UNPROCESSABLE_ENTITY),
    PRIVACY_RESTRICTED(HttpStatus.UNPROCESSABLE_ENTITY),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS),
    PAYLOAD_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR),
    SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
