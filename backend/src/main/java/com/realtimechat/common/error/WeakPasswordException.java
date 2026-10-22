package com.realtimechat.common.error;

/**
 * The submitted password does not satisfy the policy.
 *
 * <p>Reported as {@code 400 VALIDATION_FAILED} so clients treat it like any other field problem and
 * can attach the message to the password input.
 */
public class WeakPasswordException extends ApiException {

    public WeakPasswordException(String message) {
        super(ErrorCode.VALIDATION_FAILED, message);
    }
}
