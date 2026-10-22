package com.realtimechat.users.service;

import java.util.UUID;

/**
 * Port for the unread-notification badge, implemented by the {@code notifications} module.
 *
 * <p>Declared here (rather than importing the notifications module) so the account view can show the
 * badge without creating a dependency from {@code users} to {@code notifications} — which would be a
 * cycle, since notifications already reference users.
 */
public interface NotificationCountPort {

    long unreadCount(UUID userId);
}
