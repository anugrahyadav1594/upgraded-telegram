package com.realtimechat.common.mail;

import com.realtimechat.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * SMTP implementation, active when {@code app.mail.enabled=true}.
 *
 * <p>Delivery failures are logged and swallowed on purpose: the caller (password reset) must return
 * the same neutral response whether or not the address exists, and an SMTP outage must not leak
 * account existence through a different status code. Operators alert on the log line instead.
 */
@Component
@ConditionalOnProperty(prefix = "app.mail", name = "enabled", havingValue = "true")
public class SmtpEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailSender.class);

    private final JavaMailSender mailSender;
    private final String from;

    public SmtpEmailSender(JavaMailSender mailSender, AppProperties properties) {
        this.mailSender = mailSender;
        this.from = properties.getMail().getFrom();
    }

    @Override
    public void sendPasswordReset(String recipient, String resetLink, String validFor) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(recipient);
        message.setSubject("Reset your password");
        message.setText("""
                Someone requested a password reset for this email address.

                Reset link: %s

                The link can be used once and expires in %s.
                If you did not request this, you can safely ignore this email — your password
                will not change.
                """.formatted(resetLink, validFor));
        try {
            mailSender.send(message);
            log.info("password reset email dispatched");
        } catch (MailException ex) {
            log.error("password reset email could not be delivered", ex);
        }
    }

    @Override
    public boolean deliversMail() {
        return true;
    }
}
