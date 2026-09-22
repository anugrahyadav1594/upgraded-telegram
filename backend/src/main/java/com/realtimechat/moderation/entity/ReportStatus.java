package com.realtimechat.moderation.entity;

/**
 * Moderation workflow state. Matches {@code reports_status_valid}.
 *
 * <p>Terminal states ({@link #RESOLVED}, {@link #DISMISSED}) require a reviewer and a timestamp, which
 * the schema enforces: a report cannot be closed silently.
 */
public enum ReportStatus {
    OPEN,
    REVIEWING,
    RESOLVED,
    DISMISSED;

    public boolean isTerminal() {
        return this == RESOLVED || this == DISMISSED;
    }
}
