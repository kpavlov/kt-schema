package me.kpavlov.kt.schema.integration.quickstart

import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import me.kpavlov.kt.schema.Description
import me.kpavlov.kt.schema.Schema

/**
 * A postal address for deliveries and billing.
 */
@Schema
data class Address(
    @Description("Street address, including house number") @Size(max = 120) val street: String,
    @Description("City or town name") @Size(min = 1, max = 80) val city: String,
    @Description("Postal or ZIP code") @Pattern(regexp = "[0-9]{5}") val zipCode: String,
    @Description("Two-letter ISO country code; defaults to US")
    @Pattern(regexp = "[A-Z]{2}") val country: String = "US",
)
