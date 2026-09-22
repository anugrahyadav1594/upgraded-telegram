package com.realtimechat.messaging.dto;

/**
 * Aggregated reactions for one emoji on one message.
 *
 * @param reactedByMe computed for the requesting user, so the client never has to cross-reference the
 *                    full reactor list — which also means the API does not have to expose who
 *                    reacted (a privacy leak nobody asked for)
 */
public record ReactionSummaryResponse(String emoji, int count, boolean reactedByMe) {
}
