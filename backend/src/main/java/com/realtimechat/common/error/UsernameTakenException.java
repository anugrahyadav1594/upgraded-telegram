package com.realtimechat.common.error;

/** Registration/update rejected because the username is already taken. */
public class UsernameTakenException extends ApiException {

    public UsernameTakenException(String message) {
        super(ErrorCode.USERNAME_TAKEN, message);
    }
}
