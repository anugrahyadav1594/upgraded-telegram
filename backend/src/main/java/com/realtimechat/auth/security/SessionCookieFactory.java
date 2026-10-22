package com.realtimechat.auth.security;

import com.realtimechat.config.AppProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;

/**
 * Creates and clears the session cookie.
 *
 * <p>Attribute choices, and why they matter:
 * <ul>
 *   <li>{@code HttpOnly} — JavaScript cannot read it, so an XSS bug cannot exfiltrate the session.</li>
 *   <li>{@code Secure} — the browser never sends it over plain HTTP (mandatory in production).</li>
 *   <li>{@code SameSite=Lax} — the browser does not attach it to cross-site POSTs or cross-site
 *       WebSocket handshakes. This is the primary defence against login CSRF and cross-site socket
 *       hijacking, and it still allows ordinary top-level navigation to the app.</li>
 *   <li>{@code Path=/} — required for both {@code /api/**} and the {@code /ws} handshake.</li>
 *   <li>No {@code Domain} attribute — host-only cookies limit exposure to sibling subdomains.</li>
 * </ul>
 *
 * <p>The header is written as a raw {@code Set-Cookie} string because {@code SameSite} has no
 * representation in {@link Cookie}.
 */
@Component
public class SessionCookieFactory {

    private final AppProperties properties;

    public SessionCookieFactory(AppProperties properties) {
        this.properties = properties;
    }

    public void writeSessionCookie(HttpServletResponse response, String token, Duration maxAge) {
        response.addHeader(HttpHeaders.SET_COOKIE,
                buildHeader(properties.getSession().getCookieName(), token, maxAge, true));
    }

    public void clearSessionCookie(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE,
                buildHeader(properties.getSession().getCookieName(), "", Duration.ZERO, true));
    }

    /**
     * Reads the session token from the request.
     *
     * <p>Works for both HTTP requests and the WebSocket upgrade request, because the browser attaches
     * cookies to the handshake as well — which is exactly why the socket needs no token in the URL
     * (URLs end up in logs and referrers).
     */
    public Optional<String> readSessionToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        String name = properties.getSession().getCookieName();
        return Arrays.stream(cookies)
                .filter(cookie -> name.equals(cookie.getName()))
                .map(Cookie::getValue)
                .filter(value -> value != null && !value.isBlank())
                .findFirst();
    }

    private String buildHeader(String name, String value, Duration maxAge, boolean httpOnly) {
        AppProperties.Session session = properties.getSession();
        boolean secure = session.isSecureCookie() || "None".equalsIgnoreCase(session.getSameSite());
        StringBuilder header = new StringBuilder(96);
        header.append(name).append('=').append(value == null ? "" : value)
                .append("; Path=/")
                .append("; Max-Age=").append(Math.max(0, maxAge.toSeconds()))
                .append("; SameSite=").append(session.getSameSite());
        if (secure) {
            header.append("; Secure");
        }
        if (httpOnly) {
            header.append("; HttpOnly");
        }
        return header.toString();
    }
}
