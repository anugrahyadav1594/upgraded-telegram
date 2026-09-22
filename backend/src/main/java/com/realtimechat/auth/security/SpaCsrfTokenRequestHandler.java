package com.realtimechat.auth.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.util.StringUtils;

import java.util.function.Supplier;

/**
 * CSRF request handler for a single-page application using the double-submit cookie pattern.
 *
 * <p>Spring Security's default handler XOR-masks the token to protect against BREACH. That is the
 * right default, but a JavaScript client reads the <em>raw</em> value from the {@code XSRF-TOKEN}
 * cookie and echoes it in a header — so the server must compare the header value literally, while
 * still masking the token it renders into the page.
 *
 * <p>This is the officially documented SPA recipe: mask on render, compare plainly when the value
 * arrived in a header, and fall back to the masked comparison for form submissions.
 */
public class SpaCsrfTokenRequestHandler implements CsrfTokenRequestHandler {

    private final CsrfTokenRequestHandler plain = new CsrfTokenRequestAttributeHandler();
    private final CsrfTokenRequestHandler xor = new XorCsrfTokenRequestAttributeHandler();

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, Supplier<CsrfToken> csrfToken) {
        xor.handle(request, response, csrfToken);
        // Materialise the token so the cookie is written on every response that needs it.
        csrfToken.get();
    }

    @Override
    public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
        String headerValue = request.getHeader(csrfToken.getHeaderName());
        // Prefer the header (SPA / fetch); the plain comparison is correct for a raw cookie value.
        return StringUtils.hasText(headerValue)
                ? plain.resolveCsrfTokenValue(request, csrfToken)
                : xor.resolveCsrfTokenValue(request, csrfToken);
    }
}
