package com.realtimechat.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI metadata, exposed at {@code /v3/api-docs} with Swagger UI at {@code /swagger-ui.html} in
 * development profiles only ({@code springdoc.api-docs.enabled=false} in production).
 *
 * <p>The security scheme is documented as a cookie session plus CSRF header because that is what
 * clients actually have to send — an "authorize with bearer token" button would be misleading.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI realtimeChatOpenApi(AppProperties properties) {
        SecurityScheme sessionCookie = new SecurityScheme()
                .type(SecurityScheme.Type.APIKEY)
                .in(SecurityScheme.In.COOKIE)
                .name(properties.getSession().getCookieName())
                .description("Server-side session cookie (HttpOnly). Obtained from /auth/login or /auth/register.");

        SecurityScheme csrfHeader = new SecurityScheme()
                .type(SecurityScheme.Type.APIKEY)
                .in(SecurityScheme.In.HEADER)
                .name(properties.getSession().getCsrfHeaderName())
                .description("Double-submit CSRF token. Read the " + properties.getSession().getCsrfCookieName()
                        + " cookie and echo it in this header for POST/PATCH/PUT/DELETE.");

        return new OpenAPI()
                .info(new Info()
                        .title("Real-time pseudonymous chat API")
                        .version("v1")
                        .description("""
                                REST API for the pseudonymous real-time chat application.

                                Authentication uses a server-managed session cookie (HttpOnly, SameSite=Lax)
                                plus a double-submit CSRF token. Real-time delivery uses STOMP over WebSocket
                                at /ws — see docs/websocket-protocol.md.

                                Error responses always use the documented envelope:
                                `{ timestamp, status, code, message, path, requestId, details }`.
                                """)
                        .contact(new Contact().name("Real-time chat project"))
                        .license(new License().name("MIT")))
                .servers(List.of(new Server().url(properties.publicBaseUrl()).description("Application")))
                .components(new Components()
                        .addSecuritySchemes("sessionCookie", sessionCookie)
                        .addSecuritySchemes("csrfToken", csrfHeader))
                .addSecurityItem(new SecurityRequirement().addList("sessionCookie").addList("csrfToken"));
    }
}
