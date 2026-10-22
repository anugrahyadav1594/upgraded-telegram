package com.realtimechat.common.error;

/**
 * Expected failure reported as {@link ErrorCode#INVALID_REQUEST}.
 */
public class InvalidRequestException extends ApiException {

    public InvalidRequestException(String message) {
        super(ErrorCode.INVALID_REQUEST, message);
    }

    public InvalidRequestException(String message, Throwable cause) {
        super(ErrorCode.INVALID_REQUEST, message, cause);
    }
}
