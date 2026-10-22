package com.realtimechat.common.error;

/**
 * Expected failure reported as {@link ErrorCode#NOT_FOUND}.
 */
public class NotFoundException extends ApiException {

    public NotFoundException(String message) {
        super(ErrorCode.NOT_FOUND, message);
    }

    public NotFoundException(String message, Throwable cause) {
        super(ErrorCode.NOT_FOUND, message, cause);
    }
}
