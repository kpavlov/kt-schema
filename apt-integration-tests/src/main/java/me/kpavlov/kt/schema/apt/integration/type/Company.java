package me.kpavlov.kt.schema.apt.integration.type;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * Simple test model to verify plain (non-record) Java class schema generation.
 */
@JsonClassDescription("A company with a name and a founding year.")
public class Company {

    @JsonPropertyDescription("Name of the company")
    private final String name;

    @JsonPropertyDescription("Year the company was founded")
    private final int founded;

    public Company(String name, int founded) {
        this.name = name;
        this.founded = founded;
    }
}
