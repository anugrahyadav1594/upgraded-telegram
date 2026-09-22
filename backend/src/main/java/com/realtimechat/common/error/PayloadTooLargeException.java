package com.realtimechat.common.error;

/**
 * Expected failure reported as {@link ErrorCode#PAYLOAD_TOO_LARGE}.
 */
public class PayloadTooLargeException extends ApiException {

    public PayloadTooLargeException(String message) {
        super(ErrorCode.PAYLOAD_TOO_LARGE, message);
    }

    public PayloadTooLargeException(String message, Throwable cause) {
        super(ErrorCode.PAYLOAD_TOO_LARGE, message, cause);
    }
}
