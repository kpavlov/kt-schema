package me.kpavlov.kt.schema.apt.integration.type;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * Simple test model to verify basic Java annotation-processor schema generation,
 * including Jackson name overrides and ignored properties.
 */
@JsonClassDescription("A person with a first and last name and age.")
public record Person(
        @JsonProperty("given_name") @JsonPropertyDescription("Given name of the person") String firstName,
        @JsonPropertyDescription("Family name of the person") String lastName,
        @JsonPropertyDescription("Age of the person in years") int age,
        @JsonIgnore String ssn) {
}
