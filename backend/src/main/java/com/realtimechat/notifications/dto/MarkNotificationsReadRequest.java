package com.realtimechat.notifications.dto;

import java.util.List;
import java.util.UUID;

/**
 * {@code POST /api/v1/notifications/read} body.
 *
 * <p>An omitted or empty {@code ids} list means "mark everything read" — that is what the UI's
 * "mark all" action sends, and it avoids a round trip to discover which ids are currently unread.
 */
public record MarkNotificationsReadRequest(List<UUID> ids) {
}
