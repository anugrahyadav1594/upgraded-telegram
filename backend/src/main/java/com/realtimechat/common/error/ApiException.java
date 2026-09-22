package com.realtimechat.common.error;

/**
 * Base class for every deliberate, client-visible failure.
 *
 * <p>Throwing an {@code ApiException} means "this is an expected outcome that must be reported with
 * a specific status and code"; anything else that escapes a controller is converted to
 * {@code INTERNAL_ERROR} with no internal detail leaked.
 */
public class ApiException extends RuntimeException {

    private final ErrorCode errorCode;

    protected ApiException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    protected ApiException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
