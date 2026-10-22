package com.realtimechat.messaging.dto;

import java.time.Instant;

/**
 * Delivery state of a message from the caller's perspective.
 *
 * <p>For the sender this is the aggregate over recipients (the "ticks": sent, delivered, read); for a
 * recipient it is their own receipt. The distinction is made server-side so the UI has one rule:
 * render what the field says.
 */
public record ReceiptResponse(String status, Instant deliveredAt, Instant readAt) {

    public static ReceiptResponse sent() {
        return new ReceiptResponse("SENT", null, null);
    }
}
