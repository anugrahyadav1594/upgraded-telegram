package com.realtimechat.common.error;

/**
 * Expected failure reported as {@link ErrorCode#UNAUTHENTICATED}.
 */
public class UnauthenticatedException extends ApiException {

    public UnauthenticatedException(String message) {
        super(ErrorCode.UNAUTHENTICATED, message);
    }

    public UnauthenticatedException(String message, Throwable cause) {
        super(ErrorCode.UNAUTHENTICATED, message, cause);
    }
}
