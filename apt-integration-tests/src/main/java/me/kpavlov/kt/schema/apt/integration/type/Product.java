package me.kpavlov.kt.schema.apt.integration.type;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * Simple test model to verify Java interface schema generation.
 */
@JsonClassDescription("A product with a name and a price.")
public interface Product {

    @JsonPropertyDescription("Name of the product")
    String getName();

    @JsonPropertyDescription("Price of the product in cents")
    int getPrice();
}
