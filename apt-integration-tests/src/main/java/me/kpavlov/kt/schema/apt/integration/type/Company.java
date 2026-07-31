package me.kpavlov.kt.schema.apt.integration.type;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * Simple test model to verify plain (non-record) Java class schema generation,
 * including Jackson name overrides and ignored fields.
 */
@JsonClassDescription("A company with a name and a founding year.")
public class Company {

    @JsonProperty("company_name")
    @JsonPropertyDescription("Name of the company")
    private final String name;

    @JsonPropertyDescription("Year the company was founded")
    private final int founded;

    @JsonIgnore
    private final String taxId;

    public Company(String name, int founded, String taxId) {
        this.name = name;
        this.founded = founded;
        this.taxId = taxId;
    }
}
