package com.realtimechat.common.mail;

/**
 * Outbound transactional email (password reset).
 *
 * <p>An interface rather than a direct {@code JavaMailSender} call because development and
 * production need different transports: production sends through SMTP, development writes the
 * message to the application log so a developer can follow the reset link without credentials and
 * without any fake mail infrastructure in a production path.
 */
public interface EmailSender {

    /**
     * Sends (or, in development, logs) a password-reset message.
     *
     * @param recipient the destination address
     * @param resetLink the single-use link containing the raw token
     * @param validFor  human-readable validity, e.g. "30 minutes"
     */
    void sendPasswordReset(String recipient, String resetLink, String validFor);

    /** Whether this implementation actually delivers mail (drives operator-facing logging). */
    boolean deliversMail();
}
