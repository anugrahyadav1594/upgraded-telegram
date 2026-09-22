package com.realtimechat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Typed application configuration (prefix {@code app}).
 *
 * <p>Everything the application needs beyond Spring Boot's own properties is declared here, so
 * configuration is discoverable, documented (via the configuration processor) and validated at
 * startup instead of being read as scattered {@code @Value} strings.
 */
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private String publicUrl = "http://localhost:5173";
    private final Cors cors = new Cors();
    private final Session session = new Session();
    private final Websocket websocket = new Websocket();
    private final Pagination pagination = new Pagination();
    private final RateLimit rateLimit = new RateLimit();
    private final Messaging messaging = new Messaging();
    private final Presence presence = new Presence();
    private final Moderation moderation = new Moderation();
    private final Notifications notifications = new Notifications();
    private final Mail mail = new Mail();
    private final DevSeed devSeed = new DevSeed();

    public String getPublicUrl() {
        return publicUrl;
    }

    public void setPublicUrl(String publicUrl) {
        this.publicUrl = publicUrl;
    }

    public Cors getCors() {
        return cors;
    }

    public Session getSession() {
        return session;
    }

    public Websocket getWebsocket() {
        return websocket;
    }

    public Pagination getPagination() {
        return pagination;
    }

    public RateLimit getRateLimit() {
        return rateLimit;
    }

    public Messaging getMessaging() {
        return messaging;
    }

    public Presence getPresence() {
        return presence;
    }

    public Moderation getModeration() {
        return moderation;
    }

    public Notifications getNotifications() {
        return notifications;
    }

    public Mail getMail() {
        return mail;
    }

    public DevSeed getDevSeed() {
        return devSeed;
    }

    /** Base URL used to build links in emails (password reset). */
    public String publicBaseUrl() {
        String url = publicUrl == null ? "" : publicUrl.trim();
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    // -----------------------------------------------------------------------------------
    public static class Cors {
        /** Explicit origins; never "*" when credentials are allowed. */
        private List<String> allowedOrigins = new ArrayList<>(List.of("http://localhost:5173"));
        private boolean allowCredentials = true;

        public List<String> getAllowedOrigins() {
            return allowedOrigins;
        }

        public void setAllowedOrigins(List<String> allowedOrigins) {
            this.allowedOrigins = allowedOrigins;
        }

        public boolean isAllowCredentials() {
            return allowCredentials;
        }

        public void setAllowCredentials(boolean allowCredentials) {
            this.allowCredentials = allowCredentials;
        }
    }

    // -----------------------------------------------------------------------------------
    public static class Session {
        private String cookieName = "session";
        private String csrfCookieName = "XSRF-TOKEN";
        private String csrfHeaderName = "X-XSRF-TOKEN";
        private boolean secureCookie = true;
        private String sameSite = "Lax";
        private Duration idleTimeout = Duration.ofDays(7);
        private Duration absoluteTimeout = Duration.ofDays(30);
        private Duration touchInterval = Duration.ofHours(1);
        private Duration cacheTtl = Duration.ofMinutes(5);
        /** HMAC key for signing CSRF tokens and pagination cursors. Required in prod. */
        private String secret = "";

        public String getCookieName() {
            return cookieName;
        }

        public void setCookieName(String cookieName) {
            this.cookieName = cookieName;
        }

        public String getCsrfCookieName() {
            return csrfCookieName;
        }

        public void setCsrfCookieName(String csrfCookieName) {
            this.csrfCookieName = csrfCookieName;
        }

        public String getCsrfHeaderName() {
            return csrfHeaderName;
        }

        public void setCsrfHeaderName(String csrfHeaderName) {
            this.csrfHeaderName = csrfHeaderName;
        }

        public boolean isSecureCookie() {
            return secureCookie;
        }

        public void setSecureCookie(boolean secureCookie) {
            this.secureCookie = secureCookie;
        }

        public String getSameSite() {
            return sameSite;
        }

        public void setSameSite(String sameSite) {
            this.sameSite = sameSite;
        }

        public Duration getIdleTimeout() {
            return idleTimeout;
        }

        public void setIdleTimeout(Duration idleTimeout) {
            this.idleTimeout = idleTimeout;
        }

        public Duration getAbsoluteTimeout() {
            return absoluteTimeout;
        }

        public void setAbsoluteTimeout(Duration absoluteTimeout) {
            this.absoluteTimeout = absoluteTimeout;
        }

        public Duration getTouchInterval() {
            return touchInterval;
        }

        public void setTouchInterval(Duration touchInterval) {
            this.touchInterval = touchInterval;
        }

        public Duration getCacheTtl() {
            return cacheTtl;
        }

        public void setCacheTtl(Duration cacheTtl) {
            this.cacheTtl = cacheTtl;
        }

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }
    }

    // -----------------------------------------------------------------------------------
    public static class Websocket {
        private String endpoint = "/ws";
        private List<String> allowedOrigins = new ArrayList<>(List.of("http://localhost:5173"));
        private Duration connectTimeout = Duration.ofSeconds(10);
        private Duration heartbeatIncoming = Duration.ofSeconds(10);
        private Duration heartbeatOutgoing = Duration.ofSeconds(10);
        private int sendBufferSizeLimit = 512 * 1024;
        private int sendTimeLimitMs = 20_000;
        private int maxSessionsPerUser = 5;
        private int maxFrameSizeBytes = 16 * 1024;

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public List<String> getAllowedOrigins() {
            return allowedOrigins;
        }

        public void setAllowedOrigins(List<String> allowedOrigins) {
            this.allowedOrigins = allowedOrigins;
        }

        public Duration getConnectTimeout() {
            return connectTimeout;
        }

        public void setConnectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
        }

        public Duration getHeartbeatIncoming() {
            return heartbeatIncoming;
        }

        public void setHeartbeatIncoming(Duration heartbeatIncoming) {
            this.heartbeatIncoming = heartbeatIncoming;
        }

        public Duration getHeartbeatOutgoing() {
            return heartbeatOutgoing;
        }

        public void setHeartbeatOutgoing(Duration heartbeatOutgoing) {
            this.heartbeatOutgoing = heartbeatOutgoing;
        }

        public int getSendBufferSizeLimit() {
            return sendBufferSizeLimit;
        }

        public void setSendBufferSizeLimit(int sendBufferSizeLimit) {
            this.sendBufferSizeLimit = sendBufferSizeLimit;
        }

        public int getSendTimeLimitMs() {
            return sendTimeLimitMs;
        }

        public void setSendTimeLimitMs(int sendTimeLimitMs) {
            this.sendTimeLimitMs = sendTimeLimitMs;
        }

        public int getMaxSessionsPerUser() {
            return maxSessionsPerUser;
        }

        public void setMaxSessionsPerUser(int maxSessionsPerUser) {
            this.maxSessionsPerUser = maxSessionsPerUser;
        }

        public int getMaxFrameSizeBytes() {
            return maxFrameSizeBytes;
        }

        public void setMaxFrameSizeBytes(int maxFrameSizeBytes) {
            this.maxFrameSizeBytes = maxFrameSizeBytes;
        }
    }

    // -----------------------------------------------------------------------------------
    public static class Pagination {
        private int defaultLimit = 25;
        private int maxLimit = 100;
        private int messageDefaultLimit = 50;
        private int messageMaxLimit = 100;
        private String cursorSecret = "";

        public int getDefaultLimit() {
            return defaultLimit;
        }

        public void setDefaultLimit(int defaultLimit) {
            this.defaultLimit = defaultLimit;
        }

        public int getMaxLimit() {
            return maxLimit;
        }

        public void setMaxLimit(int maxLimit) {
            this.maxLimit = maxLimit;
        }

        public int getMessageDefaultLimit() {
            return messageDefaultLimit;
        }

        public void setMessageDefaultLimit(int messageDefaultLimit) {
            this.messageDefaultLimit = messageDefaultLimit;
        }

        public int getMessageMaxLimit() {
            return messageMaxLimit;
        }

        public void setMessageMaxLimit(int messageMaxLimit) {
            this.messageMaxLimit = messageMaxLimit;
        }

        public String getCursorSecret() {
            return cursorSecret;
        }

        public void setCursorSecret(String cursorSecret) {
            this.cursorSecret = cursorSecret;
        }

        /** Effective limit for a requested value: null/<=0 → default, > max → max. */
        public int resolveLimit(Integer requested) {
            if (requested == null || requested <= 0) {
                return defaultLimit;
            }
            return Math.min(requested, maxLimit);
        }

        /** Effective message page size (message pages default to 50, max 100). */
        public int resolveMessageLimit(Integer requested) {
            if (requested == null || requested <= 0) {
                return messageDefaultLimit;
            }
            return Math.min(requested, messageMaxLimit);
        }
    }

    // -----------------------------------------------------------------------------------
    public static class RateLimit {
        private boolean enabled = true;
        private int loginPerIp = 10;
        private int loginPerAccount = 5;
        private Duration loginWindow = Duration.ofMinutes(15);
        private int registerPerIp = 5;
        private Duration registerWindow = Duration.ofHours(1);
        private int passwordResetPerEmail = 3;
        private Duration passwordResetWindow = Duration.ofHours(1);
        private int searchPerUser = 60;
        private Duration searchWindow = Duration.ofMinutes(1);
        private int conversationsPerUser = 30;
        private Duration conversationsWindow = Duration.ofHours(1);
        private int messagesPerUser = 30;
        private Duration messagesWindow = Duration.ofMinutes(1);
        private int messagesPerDayPerUser = 600;
        private int reportsPerUser = 10;
        private Duration reportsWindow = Duration.ofDays(1);
        private int handshakePerIp = 60;
        private Duration handshakeWindow = Duration.ofMinutes(1);

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getLoginPerIp() {
            return loginPerIp;
        }

        public void setLoginPerIp(int loginPerIp) {
            this.loginPerIp = loginPerIp;
        }

        public int getLoginPerAccount() {
            return loginPerAccount;
        }

        public void setLoginPerAccount(int loginPerAccount) {
            this.loginPerAccount = loginPerAccount;
        }

        public Duration getLoginWindow() {
            return loginWindow;
        }

        public void setLoginWindow(Duration loginWindow) {
            this.loginWindow = loginWindow;
        }

        public int getRegisterPerIp() {
            return registerPerIp;
        }

        public void setRegisterPerIp(int registerPerIp) {
            this.registerPerIp = registerPerIp;
        }

        public Duration getRegisterWindow() {
            return registerWindow;
        }

        public void setRegisterWindow(Duration registerWindow) {
            this.registerWindow = registerWindow;
        }

        public int getPasswordResetPerEmail() {
            return passwordResetPerEmail;
        }

        public void setPasswordResetPerEmail(int passwordResetPerEmail) {
            this.passwordResetPerEmail = passwordResetPerEmail;
        }

        public Duration getPasswordResetWindow() {
            return passwordResetWindow;
        }

        public void setPasswordResetWindow(Duration passwordResetWindow) {
            this.passwordResetWindow = passwordResetWindow;
        }

        public int getSearchPerUser() {
            return searchPerUser;
        }

        public void setSearchPerUser(int searchPerUser) {
            this.searchPerUser = searchPerUser;
        }

        public Duration getSearchWindow() {
            return searchWindow;
        }

        public void setSearchWindow(Duration searchWindow) {
            this.searchWindow = searchWindow;
        }

        public int getConversationsPerUser() {
            return conversationsPerUser;
        }

        public void setConversationsPerUser(int conversationsPerUser) {
            this.conversationsPerUser = conversationsPerUser;
        }

        public Duration getConversationsWindow() {
            return conversationsWindow;
        }

        public void setConversationsWindow(Duration conversationsWindow) {
            this.conversationsWindow = conversationsWindow;
        }

        public int getMessagesPerUser() {
            return messagesPerUser;
        }

        public void setMessagesPerUser(int messagesPerUser) {
            this.messagesPerUser = messagesPerUser;
        }

        public Duration getMessagesWindow() {
            return messagesWindow;
        }

        public void setMessagesWindow(Duration messagesWindow) {
            this.messagesWindow = messagesWindow;
        }

        public int getMessagesPerDayPerUser() {
            return messagesPerDayPerUser;
        }

        public void setMessagesPerDayPerUser(int messagesPerDayPerUser) {
            this.messagesPerDayPerUser = messagesPerDayPerUser;
        }

        public int getReportsPerUser() {
            return reportsPerUser;
        }

        public void setReportsPerUser(int reportsPerUser) {
            this.reportsPerUser = reportsPerUser;
        }

        public Duration getReportsWindow() {
            return reportsWindow;
        }

        public void setReportsWindow(Duration reportsWindow) {
            this.reportsWindow = reportsWindow;
        }

        public int getHandshakePerIp() {
            return handshakePerIp;
        }

        public void setHandshakePerIp(int handshakePerIp) {
            this.handshakePerIp = handshakePerIp;
        }

        public Duration getHandshakeWindow() {
            return handshakeWindow;
        }

        public void setHandshakeWindow(Duration handshakeWindow) {
            this.handshakeWindow = handshakeWindow;
        }
    }

    // -----------------------------------------------------------------------------------
    public static class Messaging {
        private int maxContentLength = 4000;
        private Duration editWindow = Duration.ofHours(48);
        private Duration typingTtl = Duration.ofSeconds(5);
        private int messagePageSize = 50;
        private List<String> allowedReactions = new ArrayList<>();

        public int getMaxContentLength() {
            return maxContentLength;
        }

        public void setMaxContentLength(int maxContentLength) {
            this.maxContentLength = maxContentLength;
        }

        public Duration getEditWindow() {
            return editWindow;
        }

        public void setEditWindow(Duration editWindow) {
            this.editWindow = editWindow;
        }

        public Duration getTypingTtl() {
            return typingTtl;
        }

        public void setTypingTtl(Duration typingTtl) {
            this.typingTtl = typingTtl;
        }

        public int getMessagePageSize() {
            return messagePageSize;
        }

        public void setMessagePageSize(int messagePageSize) {
            this.messagePageSize = messagePageSize;
        }

        public List<String> getAllowedReactions() {
            return allowedReactions;
        }

        public void setAllowedReactions(List<String> allowedReactions) {
            this.allowedReactions = allowedReactions;
        }
    }

    // -----------------------------------------------------------------------------------
    public static class Presence {
        private Duration ttl = Duration.ofSeconds(60);
        private Duration heartbeatInterval = Duration.ofSeconds(25);
        private Duration lastSeenWriteThrottle = Duration.ofMinutes(5);
        private Duration awayAfter = Duration.ofMinutes(5);

        public Duration getTtl() {
            return ttl;
        }

        public void setTtl(Duration ttl) {
            this.ttl = ttl;
        }

        public Duration getHeartbeatInterval() {
            return heartbeatInterval;
        }

        public void setHeartbeatInterval(Duration heartbeatInterval) {
            this.heartbeatInterval = heartbeatInterval;
        }

        public Duration getLastSeenWriteThrottle() {
            return lastSeenWriteThrottle;
        }

        public void setLastSeenWriteThrottle(Duration lastSeenWriteThrottle) {
            this.lastSeenWriteThrottle = lastSeenWriteThrottle;
        }

        public Duration getAwayAfter() {
            return awayAfter;
        }

        public void setAwayAfter(Duration awayAfter) {
            this.awayAfter = awayAfter;
        }
    }

    // -----------------------------------------------------------------------------------
    public static class Moderation {
        private int maxDescriptionLength = 1000;
        private int autoFlagReportThreshold = 3;
        private int reportRetentionDays = 365;

        public int getMaxDescriptionLength() {
            return maxDescriptionLength;
        }

        public void setMaxDescriptionLength(int maxDescriptionLength) {
            this.maxDescriptionLength = maxDescriptionLength;
        }

        public int getAutoFlagReportThreshold() {
            return autoFlagReportThreshold;
        }

        public void setAutoFlagReportThreshold(int autoFlagReportThreshold) {
            this.autoFlagReportThreshold = autoFlagReportThreshold;
        }

        public int getReportRetentionDays() {
            return reportRetentionDays;
        }

        public void setReportRetentionDays(int reportRetentionDays) {
            this.reportRetentionDays = reportRetentionDays;
        }
    }

    // -----------------------------------------------------------------------------------
    public static class Notifications {
        private int previewLength = 120;
        private int retentionDays = 90;
        private int asyncCorePoolSize = 2;
        private int asyncMaxPoolSize = 8;
        private int asyncQueueCapacity = 500;

        public int getPreviewLength() {
            return previewLength;
        }

        public void setPreviewLength(int previewLength) {
            this.previewLength = previewLength;
        }

        public int getRetentionDays() {
            return retentionDays;
        }

        public void setRetentionDays(int retentionDays) {
            this.retentionDays = retentionDays;
        }

        public int getAsyncCorePoolSize() {
            return asyncCorePoolSize;
        }

        public void setAsyncCorePoolSize(int asyncCorePoolSize) {
            this.asyncCorePoolSize = asyncCorePoolSize;
        }

        public int getAsyncMaxPoolSize() {
            return asyncMaxPoolSize;
        }

        public void setAsyncMaxPoolSize(int asyncMaxPoolSize) {
            this.asyncMaxPoolSize = asyncMaxPoolSize;
        }

        public int getAsyncQueueCapacity() {
            return asyncQueueCapacity;
        }

        public void setAsyncQueueCapacity(int asyncQueueCapacity) {
            this.asyncQueueCapacity = asyncQueueCapacity;
        }
    }

    // -----------------------------------------------------------------------------------
    public static class Mail {
        private boolean enabled = false;
        private String from = "no-reply@realtime-chat.local";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getFrom() {
            return from;
        }

        public void setFrom(String from) {
            this.from = from;
        }
    }

    // -----------------------------------------------------------------------------------
    public static class DevSeed {
        private boolean enabled = false;
        private String password = "";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }
}
