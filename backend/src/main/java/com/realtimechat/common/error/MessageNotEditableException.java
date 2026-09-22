package com.realtimechat.common.error;

/**
 * Expected failure reported as {@link ErrorCode#MESSAGE_NOT_EDITABLE}.
 */
public class MessageNotEditableException extends ApiException {

    public MessageNotEditableException(String message) {
        super(ErrorCode.MESSAGE_NOT_EDITABLE, message);
    }

    public MessageNotEditableException(String message, Throwable cause) {
        super(ErrorCode.MESSAGE_NOT_EDITABLE, message, cause);
    }
}
