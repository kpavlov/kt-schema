package me.kpavlov.kt.schema.apt.integration.type;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Negative;
import jakarta.validation.constraints.NegativeOrZero;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

/**
 * Test model to verify Jakarta Bean Validation constraints are emitted by the APT processor.
 */
public record Shipment(
    @Min(1) @Max(100) int quantity,
    @DecimalMin("0.01") @DecimalMax(value = "1000", inclusive = false) double price,
    @Positive long weight,
    @PositiveOrZero int discount,
    @Negative int debit,
    @NegativeOrZero int credit,
    @Size(min = 2, max = 50) String name,
    @NotEmpty List<String> tags,
    @NotBlank String note,
    @Pattern(regexp = "[A-Z]{3}") String currency,
    @Email String contact,
    @Size(max = 5) Map<String, String> attributes,
    @Min(0) byte level
) {
}
