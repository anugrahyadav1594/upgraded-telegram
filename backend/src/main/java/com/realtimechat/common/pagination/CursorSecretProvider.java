package com.realtimechat.common.pagination;

import com.realtimechat.config.AppProperties;
import org.springframework.stereotype.Component;

/**
 * Supplies the HMAC key used for pagination cursors.
 *
 * <p>The key falls back to the session secret so a single {@code SESSION_SECRET} is enough for a
 * correct deployment; production startup validation rejects an empty value.
 */
@Component
public class CursorSecretProvider {

    private final String secret;

    public CursorSecretProvider(AppProperties properties) {
        String configured = properties.getPagination().getCursorSecret();
        if (configured == null || configured.isBlank()) {
            configured = properties.getSession().getSecret();
        }
        this.secret = configured == null ? "" : configured;
    }

    public String secret() {
        return secret;
    }
}
