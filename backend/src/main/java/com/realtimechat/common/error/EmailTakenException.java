package com.realtimechat.common.error;

/** Registration/update rejected because the email address is already registered. */
public class EmailTakenException extends ApiException {

    public EmailTakenException(String message) {
        super(ErrorCode.EMAIL_TAKEN, message);
    }
}
