package me.kpavlov.kt.schema.apt.integration.type;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;
import java.util.Map;

/**
 * Simple test model to verify collection and map references to nested objects
 * in generated schemas.
 */
@JsonClassDescription("A catalog of addresses.")
public record Catalog(
        @JsonPropertyDescription("Addresses in the catalog") List<Address> addresses,
        @JsonPropertyDescription("Addresses grouped by city") Map<String, Address> byCity) {
}
