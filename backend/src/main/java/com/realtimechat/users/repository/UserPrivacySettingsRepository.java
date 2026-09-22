package com.realtimechat.users.repository;

import com.realtimechat.users.entity.UserPrivacySettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Privacy settings lookups. Callers must treat a missing row as "defaults", never as unrestricted. */
public interface UserPrivacySettingsRepository extends JpaRepository<UserPrivacySettings, UUID> {

    List<UserPrivacySettings> findAllByUserIdIn(Collection<UUID> userIds);
}
