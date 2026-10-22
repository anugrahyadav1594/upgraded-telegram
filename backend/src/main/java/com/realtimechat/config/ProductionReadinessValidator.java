package com.realtimechat.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Fails application startup when the {@code prod} profile is misconfigured.
 *
 * <p>Rationale: an insecure production deployment should not be possible by forgetting an
 * environment variable. A missing session secret, a wildcard CORS origin or a disabled mail
 * transport are all conditions that would either weaken security or break account recovery, so they
 * are treated as configuration errors rather than warnings.
 */
@Component
@Profile("prod")
public class ProductionReadinessValidator implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(ProductionReadinessValidator.class);
    private static final int MIN_SECRET_LENGTH = 32;

    private final AppProperties properties;

    public ProductionReadinessValidator(AppProperties properties) {
        this.properties = properties;
    }

    @Override
    public void afterPropertiesSet() {
        List<String> problems = new ArrayList<>();

        String secret = properties.getSession().getSecret();
        if (secret == null || secret.isBlank()) {
            problems.add("SESSION_SECRET is not set");
        } else if (secret.length() < MIN_SECRET_LENGTH) {
            problems.add("SESSION_SECRET must be at least " + MIN_SECRET_LENGTH + " characters");
        } else if (secret.toLowerCase().contains("change-me") || secret.toLowerCase().contains("example")) {
            problems.add("SESSION_SECRET still contains a placeholder value");
        }

        if (properties.getCors().getAllowedOrigins() == null || properties.getCors().getAllowedOrigins().isEmpty()) {
            problems.add("CORS_ALLOWED_ORIGINS must list the real frontend origin(s)");
        } else if (properties.getCors().getAllowedOrigins().stream().anyMatch(origin -> origin.contains("*"))) {
            problems.add("CORS_ALLOWED_ORIGINS must not contain a wildcard when credentials are enabled");
        }

        if (properties.getCors().isAllowCredentials() && !properties.getSession().isSecureCookie()) {
            problems.add("SESSION_COOKIE_SECURE must be true in production");
        }

        if (!properties.getMail().isEnabled()) {
            problems.add("MAIL_ENABLED must be true in production (password reset must deliver)");
        }

        if (properties.getDevSeed().isEnabled()) {
            problems.add("DEV_SEED_ENABLED must be false in production");
        }

        if (properties.publicBaseUrl() == null || !properties.publicBaseUrl().startsWith("https://")) {
            problems.add("APP_PUBLIC_URL must be an https:// URL in production");
        }

        if (!problems.isEmpty()) {
            log.error("production configuration is not valid:\n  - {}", String.join("\n  - ", problems));
            throw new IllegalStateException("Invalid production configuration: " + String.join("; ", problems));
        }
        log.info("production configuration validated ({} checks passed)", 6);
    }
}
