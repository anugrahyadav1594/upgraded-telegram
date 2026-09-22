package com.realtimechat.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.realtimechat.auth.security.CsrfCookieFilter;
import com.realtimechat.auth.security.SecurityErrorResponder;
import com.realtimechat.auth.security.SessionAuthenticationFilter;
import com.realtimechat.auth.security.SpaCsrfTokenRequestHandler;
import com.realtimechat.common.error.ApiErrorResponse;
import com.realtimechat.common.error.ErrorCode;
import com.realtimechat.common.web.RequestIdFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.Instant;
import java.util.List;

/**
 * Security configuration: the authorization matrix, session strategy, CSRF and CORS.
 *
 * <p>Design decisions (see {@code docs/security-design.md}):
 * <ul>
 *   <li><b>Stateless</b> — no servlet {@code HttpSession}. Authentication comes from an opaque
 *       token in an HttpOnly cookie, resolved per request by {@link SessionAuthenticationFilter}.</li>
 *   <li><b>Deny by default</b> — only an explicit allow-list of endpoints is public.</li>
 *   <li><b>Double-submit CSRF</b> — the {@code XSRF-TOKEN} cookie is readable by JavaScript on
 *       purpose and must be echoed in the {@code X-XSRF-TOKEN} header for unsafe methods.</li>
 *   <li><b>No wildcard CORS with credentials</b> — origins come from configuration.</li>
 *   <li><b>Argon2id</b> password hashing (BouncyCastle provider is declared in {@code pom.xml}).</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    /** Endpoints that must work without a session. Deliberately a short, explicit list. */
    private static final String[] PUBLIC_ENDPOINTS = {
            "/api/v1/auth/register",
            "/api/v1/auth/login",
            "/api/v1/auth/forgot-password",
            "/api/v1/auth/reset-password",
            "/actuator/health",
            "/actuator/health/**",
            "/actuator/info",
            "/error"
    };

    private final AppProperties properties;
    private final ObjectMapper objectMapper;

    public SecurityConfig(AppProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * Argon2id with the OWASP baseline parameters: 19 MiB memory, 2 iterations, 1 lane, 16-byte salt,
     * 32-byte hash — the exact values documented in {@code docs/security-design.md §3} and
     * required by NFR-S-1.
     *
     * <p>Chosen over bcrypt/PBKDF2 because it is memory-hard, which raises the cost of GPU-backed
     * cracking. The parameters are encoded in every hash, so they can be raised later and existing
     * hashes upgraded transparently on the next successful login.
     *
     * <p>Declared as a static factory rather than inline in the bean so that the one place which
     * creates hashes outside a request — the {@code --hash-password} utility used to prepare the
     * development seed — produces hashes this configuration will verify. Spring's
     * {@code defaultsForSpringSecurity_v5_8()} is deliberately *not* used: its 16 MiB memory setting
     * would silently disagree with the documentation.
     */
    public static PasswordEncoder argon2idPasswordEncoder() {
        return new Argon2PasswordEncoder(SALT_LENGTH_BYTES, HASH_LENGTH_BYTES, PARALLELISM, MEMORY_KIB, ITERATIONS);
    }

    /** Salt size in bytes; 16 is the Argon2 recommendation and is stored inside the PHC string. */
    private static final int SALT_LENGTH_BYTES = 16;
    /** Output hash size in bytes. */
    private static final int HASH_LENGTH_BYTES = 32;
    /** Degree of parallelism (lanes). */
    private static final int PARALLELISM = 1;
    /** Memory cost in KiB — 19 MiB, the OWASP baseline. */
    private static final int MEMORY_KIB = 19456;
    /** Time cost: number of passes over memory. */
    private static final int ITERATIONS = 2;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return argon2idPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   SessionAuthenticationFilter sessionAuthenticationFilter,
                                                   SecurityErrorResponder errorResponder) throws Exception {
        http
                // ---------------------------------------------------------------- CSRF
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler())
                        // The WebSocket handshake is a GET and carries an Origin check instead;
                        // STOMP frames are protected by session authorization.
                        .ignoringRequestMatchers("/ws/**", "/actuator/**")
                        .ignoringRequestMatchers("/api/v1/auth/login", "/api/v1/auth/register",
                                "/api/v1/auth/forgot-password", "/api/v1/auth/reset-password")
                )
                // Pre-auth endpoints still need a token for state changes. The four above are
                // intentionally exempt from CSRF because they create the session or are safe for
                // anonymous callers (login CSRF is mitigated by SameSite=Lax + Origin checks).
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers
                        .frameOptions(frame -> frame.deny())
                        .contentTypeOptions(Customizer.withDefaults())
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31_536_000))
                        .referrerPolicy(referrer -> referrer.policy(
                                org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        .permissionsPolicyHeader(policy -> policy.policy(
                                "camera=(), microphone=(), geolocation=(), payment=()"))
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; "
                                        + "script-src 'self'; "
                                        + "style-src 'self' 'unsafe-inline'; "
                                        + "img-src 'self' https: data:; "
                                        + "connect-src 'self' wss: https:; "
                                        + "font-src 'self'; "
                                        + "object-src 'none'; "
                                        + "base-uri 'none'; "
                                        + "frame-ancestors 'none'; "
                                        + "form-action 'self'"))
                        .crossOriginOpenerPolicy(coop -> coop.policy(
                                org.springframework.security.web.header.writers.CrossOriginOpenerPolicyHeaderWriter.CrossOriginOpenerPolicy.SAME_ORIGIN))
                )
                // ------------------------------------------------------ Authorization
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        // WebSocket handshake: authenticated by WebSocketAuthHandshakeInterceptor,
                        // which resolves the same session cookie and rejects before any STOMP frame.
                        .requestMatchers("/ws", "/ws/**").permitAll()
                        // API documentation: enabled only in dev/docker profiles (see application-dev.yml)
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/actuator/prometheus", "/actuator/metrics/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                // --------------------------------------------------------- Failures
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, authException) ->
                                errorResponder.write(response, request, ErrorCode.UNAUTHENTICATED,
                                        "Authentication is required"))
                        .accessDeniedHandler((request, response, accessDeniedException) ->
                                errorResponder.write(response, request, ErrorCode.FORBIDDEN,
                                        "You do not have permission to perform this action"))
                )
                // ----------------------------------------------------------- Filters
                .addFilterBefore(sessionAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                // Renders the CSRF token into the response cookie so a SPA can read it.
                .addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class)
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .logout(logout -> logout.disable())
                .anonymous(Customizer.withDefaults());

        return http.build();
    }

    /** CORS: explicit origins, credentials allowed, no wildcard. */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(properties.getCors().getAllowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of(
                HttpHeaders.CONTENT_TYPE, HttpHeaders.ACCEPT, HttpHeaders.AUTHORIZATION,
                properties.getSession().getCsrfHeaderName(), RequestIdFilter.HEADER, "X-Client-Version"));
        configuration.setExposedHeaders(List.of(
                RequestIdFilter.HEADER, "X-RateLimit-Limit", "X-RateLimit-Remaining",
                "Retry-After", "Idempotent-Replay", HttpHeaders.LOCATION));
        configuration.setAllowCredentials(properties.getCors().isAllowCredentials());
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    /** Error body helper used by the entry point / access-denied handler (not a controller advice). */
    @Bean
    public ApiErrorResponseFactory apiErrorResponseFactory() {
        return new ApiErrorResponseFactory(objectMapper);
    }

    /**
     * Builds error bodies outside the MVC exception pipeline (Spring Security filters run before
     * {@code @RestControllerAdvice} exists for the request).
     */
    public static class ApiErrorResponseFactory {

        private final ObjectMapper objectMapper;

        public ApiErrorResponseFactory(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
        }

        public ApiErrorResponse create(ErrorCode code, String message, String path, String requestId) {
            return ApiErrorResponse.of(code, message, path, requestId);
        }

        public ApiErrorResponse createFallback() {
            return new ApiErrorResponse(Instant.now(), 500, ErrorCode.INTERNAL_ERROR.name(),
                    "An unexpected error occurred", null, null, null);
        }

        public ObjectMapper objectMapper() {
            return objectMapper;
        }
    }
}
