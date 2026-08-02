package com.example.orgchart;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * A postal address nested inside {@link ContactInfo}.
 */
@JsonClassDescription("A postal address.")
public record Address(
        @JsonPropertyDescription("Street and house number")
        String street,
        @JsonPropertyDescription("City or town")
        String city,
        @JsonPropertyDescription("Country")
        String country,
        @JsonPropertyDescription("Postal code")
        String zipCode) {
}
