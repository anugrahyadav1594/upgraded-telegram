package com.realtimechat.users.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Partial privacy update. Null fields are left unchanged, which keeps "toggle one switch" from
 * requiring the client to send the full object (and from racing with another tab).
 */
public record UpdatePrivacyRequest(
        Boolean showOnlineStatus,
        Boolean showLastSeen,
        Boolean showProfileInSearch,
        @Pattern(regexp = "EVERYONE|CONTACTS", message = "must be EVERYONE or CONTACTS")
        String allowMessagesFrom,
        Boolean readReceipts
) {
}
