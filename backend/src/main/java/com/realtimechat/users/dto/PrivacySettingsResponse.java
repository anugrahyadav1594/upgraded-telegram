package com.realtimechat.users.dto;

import com.realtimechat.users.entity.UserPrivacySettings;

/** Privacy settings as returned by {@code GET /api/v1/privacy}. */
public record PrivacySettingsResponse(
        boolean showOnlineStatus,
        boolean showLastSeen,
        boolean showProfileInSearch,
        String allowMessagesFrom,
        boolean readReceipts
) {

    public static PrivacySettingsResponse from(UserPrivacySettings settings) {
        return new PrivacySettingsResponse(
                settings.isShowOnlineStatus(),
                settings.isShowLastSeen(),
                settings.isShowProfileInSearch(),
                settings.getAllowMessagesFrom().name(),
                settings.isReadReceipts());
    }
}
