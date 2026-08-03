package me.kpavlov.kt.schema.apt.integration.type;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;

/**
 * Simple test model to verify JsonTypeName/JsonProperty name overrides on a Java enum.
 */
@JsonTypeName("PriorityLevel")
public enum Priority {
    @JsonProperty("low")
    LOW,
    @JsonProperty("medium")
    MEDIUM,
    @JsonProperty("high")
    HIGH
}
