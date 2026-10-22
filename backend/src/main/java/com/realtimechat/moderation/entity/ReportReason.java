package com.realtimechat.moderation.entity;

/**
 * Report categories.
 *
 * <p>A fixed vocabulary rather than free text: it makes the moderation queue sortable and countable
 * ("how many harassment reports this week?") and keeps unfiltered user text out of the operational
 * tooling. Anything that does not fit still has {@link #OTHER} plus the description field.
 */
public enum ReportReason {
    SPAM,
    HARASSMENT,
    HATE_SPEECH,
    IMPERSONATION,
    SEXUAL_CONTENT,
    VIOLENCE,
    SELF_HARM,
    OTHER
}
