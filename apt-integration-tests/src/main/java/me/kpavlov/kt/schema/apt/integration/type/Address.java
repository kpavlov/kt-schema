package me.kpavlov.kt.schema.apt.integration.type;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * Simple test model to verify nested object references in generated schemas.
 */
public record Address(
        @JsonPropertyDescription("City or town name") String city,
        @JsonPropertyDescription("Street name and number") String street) {
}
