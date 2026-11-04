package me.kpavlov.kt.schema.integration.type

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

@Schema
@Suppress("MagicNumber")
data class ValidatedShipment(
    @Min(1) @Max(100) val quantity: Int,
    @DecimalMin("0.01") @DecimalMax(value = "1000", inclusive = false) val price: Double,
    @Positive val weight: Long,
    @PositiveOrZero val discount: Int,
    @Negative val debit: Int,
    @NegativeOrZero val credit: Int,
    @Size(min = 2, max = 50) val name: String,
    @NotEmpty val tags: List<String>,
    @NotBlank val note: String?,
    @Pattern(regexp = "[A-Z]{3}") val currency: String,
    @Email val contact: String,
    @Size(max = 5) val attributes: Map<String, String>,
    @Min(0) val level: Byte,
)
