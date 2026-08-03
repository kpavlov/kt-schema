package com.example.orgchart;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;

/**
 * Employment type of an employee. Enums are emitted as a JSON Schema string with an
 * {@code enum} array listing the constants in declaration order; {@code @JsonProperty}
 * renames the JSON values (here, to lowercase).
 */
@JsonClassDescription("Employment type of an employee.")
@JsonTypeName("Employment")
public enum EmploymentType {
    @JsonProperty("full_time")
    FULL_TIME,
    @JsonProperty("part_time")
    PART_TIME,
    @JsonProperty("contractor")
    CONTRACTOR
}