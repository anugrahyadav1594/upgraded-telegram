package com.realtimechat.common.error;

/**
 * Expected failure reported as {@link ErrorCode#BLOCKED}.
 */
public class BlockedException extends ApiException {

    public BlockedException(String message) {
        super(ErrorCode.BLOCKED, message);
    }

    public BlockedException(String message, Throwable cause) {
        super(ErrorCode.BLOCKED, message, cause);
    }
}
