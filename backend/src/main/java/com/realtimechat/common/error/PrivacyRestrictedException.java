package com.realtimechat.common.error;

/**
 * Expected failure reported as {@link ErrorCode#PRIVACY_RESTRICTED}.
 */
public class PrivacyRestrictedException extends ApiException {

    public PrivacyRestrictedException(String message) {
        super(ErrorCode.PRIVACY_RESTRICTED, message);
    }

    public PrivacyRestrictedException(String message, Throwable cause) {
        super(ErrorCode.PRIVACY_RESTRICTED, message, cause);
    }
}
