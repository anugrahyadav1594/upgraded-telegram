package com.realtimechat.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * JSON configuration.
 *
 * <p>Relies on Spring Boot's auto-configured {@code ObjectMapper} (JavaTime module, ISO-8601
 * timestamps, non-null inclusion) and only adds the two rules the API contract depends on:
 * <ul>
 *   <li><b>Unknown properties are rejected.</b> A client sending {@code role},
 *       {@code emailVerified} or {@code userId} gets a 400 instead of having the field silently
 *       ignored — the cheap half of mass-assignment protection (the other half is that request
 *       bodies bind to explicit DTO records, never to entities).</li>
 *   <li><b>Non-null only.</b> Optional fields are omitted rather than serialised as {@code null},
 *       which keeps payloads small and makes "absent" unambiguous for clients.</li>
 * </ul>
 *
 * <p>DTOs deliberately avoid Jackson-specific annotations so that a future Spring Boot 4/Jackson 3
 * migration is a mechanical import change (see {@code docs/architecture.md §18}).
 */
@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jsonContractCustomizer() {
        return builder -> builder
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .featuresToEnable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .featuresToDisable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES);
    }

    /** Exposed so integration tests can assert the exact wire contract. */
    @Bean
    public ObjectMapperContract objectMapperContract(ObjectMapper objectMapper) {
        return new ObjectMapperContract(objectMapper);
    }

    /** Tiny holder used by tests and by the OpenAPI configuration to reuse the same mapper. */
    public record ObjectMapperContract(ObjectMapper mapper) {
    }
}
