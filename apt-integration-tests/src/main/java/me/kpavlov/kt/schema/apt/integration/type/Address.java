package me.kpavlov.kt.schema.apt.integration.type;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonTypeName;

/**
 * Simple test model to verify nested object references in generated schemas.
 */
@JsonTypeName("Address")
public record Address(
        @JsonPropertyDescription("City or town name") String city,
        @JsonPropertyDescription("Street name and number") String street) {
}
