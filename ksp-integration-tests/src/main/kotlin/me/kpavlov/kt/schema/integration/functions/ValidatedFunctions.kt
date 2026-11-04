@file:Suppress("unused")

package me.kpavlov.kt.schema.integration.functions

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import me.kpavlov.kt.schema.Schema

@Schema
fun shipOrder(
    @Min(1) quantity: Int,
    @NotBlank customer: String,
    @Size(max = 3) tags: List<String> = emptyList(),
): String = "$quantity for $customer $tags"
