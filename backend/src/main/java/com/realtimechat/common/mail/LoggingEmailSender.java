package com.realtimechat.common.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Development transport: writes the reset link to the application log.
 *
 * <p>This is <b>not</b> a fake mail server and it never runs in production
 * ({@code app.mail.enabled=true} is mandatory in the {@code prod} profile). The email address is
 * masked so that developer machines, screenshots and support bundles do not accumulate real
 * addresses.
 */
@Component
@ConditionalOnProperty(prefix = "app.mail", name = "enabled", havingValue = "false", matchIfMissing = true)
public class LoggingEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailSender.class);

    @Override
    public void sendPasswordReset(String recipient, String resetLink, String validFor) {
        log.info("""
                        
                        ── DEVELOPMENT MAIL ──────────────────────────────────────────────
                        To      : {}
                        Subject : Reset your password
                        Link    : {}
                        Expires : {}
                        (SMTP is disabled. Set MAIL_ENABLED=true and MAIL_HOST to send real mail.)
                        ─────────────────────────────────────────────────────────────────""",
                mask(recipient), resetLink, validFor);
    }

    @Override
    public boolean deliversMail() {
        return false;
    }

    private String mask(String email) {
        if (email == null || !email.contains("@")) {
            return "***";
        }
        int at = email.indexOf('@');
        String local = email.substring(0, at);
        String domain = email.substring(at);
        String visible = local.length() <= 2 ? local.substring(0, 1) : local.substring(0, 2);
        return visible + "***" + domain;
    }
}
