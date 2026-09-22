package com.realtimechat.common.util;

import com.realtimechat.config.AppProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * The curated reaction allow-list.
 *
 * <p>Restricting reactions to a known set keeps arbitrary Unicode payloads (including invisible
 * characters) out of the database, makes aggregation predictable across clients, and prevents the
 * API from becoming a general-purpose string store. The list is configuration, so extending it
 * needs no code change.
 */
@Component
public class EmojiCatalog {

    private final List<String> allowed;
    private final Set<String> allowedSet;

    public EmojiCatalog(AppProperties properties) {
        List<String> configured = properties.getMessaging().getAllowedReactions();
        this.allowed = configured == null || configured.isEmpty() ? List.of("👍", "❤️", "😂") : List.copyOf(configured);
        this.allowedSet = Set.copyOf(this.allowed);
    }

    public boolean isAllowed(String emoji) {
        return emoji != null && allowedSet.contains(emoji);
    }

    public List<String> allowed() {
        return allowed;
    }
}
