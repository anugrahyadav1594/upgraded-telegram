package com.realtimechat.common.util;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Single source of "now".
 *
 * <p>Time-dependent rules (48-hour edit window, token expiry, sliding sessions, last-seen
 * throttling) are tested deterministically by replacing this bean with a controllable
 * implementation, instead of sleeping in tests.
 */
@Component
public class TimeProvider {

    public Instant now() {
        return Instant.now();
    }

    public Instant plus(Duration duration) {
        return now().plus(duration);
    }

    public Instant minus(Duration duration) {
        return now().minus(duration);
    }

    public boolean isBefore(Instant instant, Instant reference) {
        return instant != null && instant.isBefore(reference);
    }
}
