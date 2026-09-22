package com.realtimechat.common.error;

/**
 * Expected failure reported as {@link ErrorCode#RATE_LIMITED}.
 */
public class RateLimitExceededException extends ApiException {

    public RateLimitExceededException(String message) {
        super(ErrorCode.RATE_LIMITED, message);
    }

    public RateLimitExceededException(String message, Throwable cause) {
        super(ErrorCode.RATE_LIMITED, message, cause);
    }
}
