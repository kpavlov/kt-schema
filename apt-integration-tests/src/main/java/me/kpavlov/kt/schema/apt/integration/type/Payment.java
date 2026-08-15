package me.kpavlov.kt.schema.apt.integration.type;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * Test model to verify BigInteger and BigDecimal schema generation in APT.
 */
@JsonClassDescription("A payment with BigInteger quantity and BigDecimal amount.")
public record Payment(
    @JsonPropertyDescription("Quantity of items")
    BigInteger quantity,
    @JsonPropertyDescription("Total amount of payment")
    BigDecimal amount
) {
}
