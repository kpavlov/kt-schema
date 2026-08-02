package com.example.orgchart;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * A department in the organization. References back to {@link Employee} through the
 * {@code head} accessor, demonstrating mutual recursion between two schema types.
 */
@JsonClassDescription("A department in the organization.")
public record Department(
        @JsonPropertyDescription("Name of the department") String name,
        @JsonPropertyDescription("Cost center code") String costCenter,
        @JsonPropertyDescription("Employee heading the department") Employee head) {
}
