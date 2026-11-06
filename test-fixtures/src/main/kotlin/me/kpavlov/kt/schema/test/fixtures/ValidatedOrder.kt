@file:Suppress("unused", "LongParameterList", "MagicNumber")

package me.kpavlov.kt.schema.test.fixtures

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

/** Covers every Jakarta Bean Validation constraint that maps to a JSON Schema keyword. */
data class ValidatedOrder(
    @param:Min(1) @param:Max(100) val quantity: Int,
    @param:DecimalMin("0.01") @param:DecimalMax(value = "1000", inclusive = false) val price: Double,
    @param:Positive val weight: Long,
    @param:PositiveOrZero val discount: Int,
    @param:Negative val debit: Int,
    @param:NegativeOrZero val credit: Int,
    @param:Size(min = 2, max = 50) val name: String,
    @param:NotEmpty val tags: List<String>,
    @param:NotBlank val note: String?,
    @param:Pattern(regexp = "[A-Z]{3}") val currency: String,
    @param:Email val contact: String,
    @param:Size(max = 5) val attributes: Map<String, String>,
    @param:Min(0) val level: Byte,
)

/** Takes the same constraints as [ValidatedOrder], on function parameters. */
fun placeValidatedOrder(
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
