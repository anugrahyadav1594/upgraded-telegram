package com.realtimechat.auth.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Forces the CSRF token to be rendered so the {@code XSRF-TOKEN} cookie is actually written.
 *
 * <p>Spring Security defers token generation until something reads it, which means a single-page app
 * could load, receive no cookie, and then fail every {@code POST} with a 403. Touching the token in a
 * filter guarantees the cookie exists after the first response.
 */
public class CsrfCookieFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (csrfToken != null) {
            // Reading the value is what triggers the CookieCsrfTokenRepository to set the cookie.
            csrfToken.getToken();
        }
        filterChain.doFilter(request, response);
    }
}
