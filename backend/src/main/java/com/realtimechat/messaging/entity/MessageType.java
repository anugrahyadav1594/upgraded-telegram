package com.realtimechat.messaging.entity;

/**
 * Message kinds supported by the storage model.
 *
 * <p>{@link #TEXT} is the only type this release produces. {@link #IMAGE} and {@link #FILE} have a
 * schema (the {@code attachments} table) and a wire representation, but the API rejects them with
 * {@code UNSUPPORTED_MESSAGE_TYPE} rather than accepting an attachment that could never be stored or
 * served. {@link #SYSTEM} is reserved for server-generated notices.
 */
public enum MessageType {
    TEXT,
    IMAGE,
    FILE,
    SYSTEM
}
