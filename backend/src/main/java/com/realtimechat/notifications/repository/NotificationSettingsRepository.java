package com.realtimechat.notifications.repository;

import com.realtimechat.notifications.entity.NotificationSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/** Notification preference persistence (one row per user, created lazily). */
public interface NotificationSettingsRepository extends JpaRepository<NotificationSettings, UUID> {
}
