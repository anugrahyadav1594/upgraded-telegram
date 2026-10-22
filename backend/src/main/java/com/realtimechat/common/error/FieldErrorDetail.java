package com.realtimechat.common.error;

/**
 * One field-level validation failure inside an error response.
 *
 * @param field the request field (dot path for nested objects)
 * @param code  a short, stable reason code (e.g. {@code PATTERN}, {@code SIZE}, {@code REQUIRED})
 * @param message a human-readable explanation, safe to show to the user
 */
public record FieldErrorDetail(String field, String code, String message) {
}
