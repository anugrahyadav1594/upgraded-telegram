package com.realtimechat.common.error;

/**
 * Expected failure reported as {@link ErrorCode#CONFLICT}.
 */
public class ConflictException extends ApiException {

    public ConflictException(String message) {
        super(ErrorCode.CONFLICT, message);
    }

    public ConflictException(String message, Throwable cause) {
        super(ErrorCode.CONFLICT, message, cause);
    }
}
