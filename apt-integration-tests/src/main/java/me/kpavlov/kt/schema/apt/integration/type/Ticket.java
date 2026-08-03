package me.kpavlov.kt.schema.apt.integration.type;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * Simple test model to verify a nested enum field reference in generated schemas.
 */
@JsonClassDescription("A support ticket with a lifecycle status.")
public record Ticket(
        @JsonPropertyDescription("Unique ticket identifier") String id,
        @JsonPropertyDescription("Current status of the ticket") Status status) {
}
