package com.realtimechat.common.util;

import com.realtimechat.config.AppProperties;
import org.springframework.stereotype.Component;

/**
 * Produces the short, safe previews used in conversation lists and notifications.
 *
 * <p>Previews are truncated on a word boundary where possible and deliberately never contain the
 * whole message body — a notification must not become a second copy of the message store
 * (see {@code docs/database-design.md §5}).
 */
@Component
public class PreviewFormatter {

    private final int defaultLength;

    public PreviewFormatter(AppProperties properties) {
        this.defaultLength = properties.getNotifications().getPreviewLength();
    }

    public String preview(String content) {
        return preview(content, defaultLength);
    }

    public String preview(String content, int maxLength) {
        if (content == null) {
            return null;
        }
        String normalized = TextNormalizer.normalizeText(content);
        if (normalized == null) {
            return null;
        }
        String singleLine = normalized.replace('\n', ' ').replaceAll("\\s{2,}", " ").trim();
        if (singleLine.length() <= maxLength) {
            return singleLine;
        }
        String cut = singleLine.substring(0, maxLength - 1);
        int lastSpace = cut.lastIndexOf(' ');
        if (lastSpace > maxLength / 2) {
            cut = cut.substring(0, lastSpace);
        }
        return cut.stripTrailing() + "…";
    }
}
