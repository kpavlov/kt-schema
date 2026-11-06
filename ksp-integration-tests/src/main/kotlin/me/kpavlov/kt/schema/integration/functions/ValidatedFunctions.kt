@file:Suppress("unused", "LongParameterList", "MagicNumber")

package me.kpavlov.kt.schema.integration.functions

import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Negative
import jakarta.validation.constraints.NegativeOrZero
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size
import me.kpavlov.kt.schema.Schema

// Takes the same constraints as ValidatedShipment. A KDoc here would become the schema description.
@Schema
fun shipOrder(
    @Min(1) @Max(100) quantity: Int,
    @DecimalMin("0.01") @DecimalMax(value = "1000", inclusive = false) price: Double,
    @Positive weight: Long,
    @PositiveOrZero discount: Int,
    @Negative debit: Int,
    @NegativeOrZero credit: Int,
    @Size(min = 2, max = 50) name: String,
    @NotEmpty tags: List<String>,
    @NotBlank note: String?,
    @Pattern(regexp = "[A-Z]{3}") currency: String,
    @Email contact: String,
    @Size(max = 5) attributes: Map<String, String>,
    @Min(0) level: Byte,
): String = "$quantity of $name"
