@file:Suppress("FunctionOnlyReturningConstant", "unused")

package me.kpavlov.kt.schema.integration.functions

import me.kpavlov.kt.schema.Description
import me.kpavlov.kt.schema.Schema

/*
 * Functions processed with the lenient config (see build.gradle.kts), where a parameter
 * with a Kotlin default value is not required.
 */

@Schema
@Description("Converts a temperature")
fun convertTemperature(
    @Description("Temperature value")
    value: Double,
    @Description("Target unit")
    unit: String = "celsius",
    @Description("Decimal places")
    precision: Int? = null,
): Double = value
