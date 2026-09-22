package com.realtimechat.common.error;

/**
 * Expected failure reported as {@link ErrorCode#UNSUPPORTED_MESSAGE_TYPE}.
 */
public class UnsupportedMessageTypeException extends ApiException {

    public UnsupportedMessageTypeException(String message) {
        super(ErrorCode.UNSUPPORTED_MESSAGE_TYPE, message);
    }

    public UnsupportedMessageTypeException(String message, Throwable cause) {
        super(ErrorCode.UNSUPPORTED_MESSAGE_TYPE, message, cause);
    }
}
