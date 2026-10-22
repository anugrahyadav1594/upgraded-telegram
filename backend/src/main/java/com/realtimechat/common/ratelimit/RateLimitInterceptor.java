package com.realtimechat.common.ratelimit;

import com.realtimechat.common.error.RateLimitExceededException;
import com.realtimechat.common.web.AuthenticatedUser;
import com.realtimechat.common.web.ClientIpResolver;
import com.realtimechat.config.AppProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;

/**
 * Applies route-level rate limits before a controller runs.
 *
 * <p>Two subject types are used: authenticated operations are limited per user (a shared IP must not
 * let one tenant exhaust another's budget — think university NAT), while anonymous operations are
 * limited per IP. Authentication-sensitive limits (per-account password guessing) are enforced
 * inside the service where the account is known, because the interceptor runs before the body is
 * parsed.
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimiter rateLimiter;
    private final ClientIpResolver ipResolver;
    private final AppProperties properties;

    public RateLimitInterceptor(RateLimiter rateLimiter, ClientIpResolver ipResolver, AppProperties properties) {
        this.rateLimiter = rateLimiter;
        this.ipResolver = ipResolver;
        this.properties = properties;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        AppProperties.RateLimit limits = properties.getRateLimit();
        if (!limits.isEnabled()) {
            return true;
        }

        String path = request.getRequestURI();
        String method = request.getMethod();
        Rule rule = ruleFor(path, method, limits);
        if (rule == null) {
            return true;
        }

        String subject = subjectKey(request, rule.perUser());
        RateLimitDecision decision = rateLimiter.consume(rule.bucket(), subject, rule.limit(), rule.window());

        response.setHeader("X-RateLimit-Limit", String.valueOf(decision.limit()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(decision.remaining()));

        if (!decision.allowed()) {
            long retryAfterSeconds = Math.max(1, decision.retryAfter().toSeconds());
            response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
            throw new RateLimitExceededException(
                    "Too many attempts. Try again in " + retryAfterSeconds + " seconds.");
        }
        return true;
    }

    private String subjectKey(HttpServletRequest request, boolean perUser) {
        if (perUser) {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
                return "u:" + user.userId();
            }
        }
        return "ip:" + ipResolver.resolve(request);
    }

    /** Route → limit mapping. Kept explicit so limits are greppable and reviewable. */
    private Rule ruleFor(String path, String method, AppProperties.RateLimit limits) {
        if (path.startsWith("/api/v1/users/search") && "GET".equals(method)) {
            return new Rule("search", limits.getSearchPerUser(), limits.getSearchWindow(), true);
        }
        if (path.equals("/api/v1/conversations") && "POST".equals(method)) {
            return new Rule("conversations", limits.getConversationsPerUser(), limits.getConversationsWindow(), true);
        }
        if (path.startsWith("/api/v1/users/") && path.endsWith("/block") && "POST".equals(method)) {
            return new Rule("blocks", 60, Duration.ofHours(1), true);
        }
        if (path.equals("/api/v1/reports") && "POST".equals(method)) {
            return new Rule("reports", limits.getReportsPerUser(), limits.getReportsWindow(), true);
        }
        if (path.endsWith("/messages") && "POST".equals(method)) {
            return new Rule("messages", limits.getMessagesPerUser(), limits.getMessagesWindow(), true);
        }
        return null;
    }

    private record Rule(String bucket, int limit, Duration window, boolean perUser) {
    }
}
