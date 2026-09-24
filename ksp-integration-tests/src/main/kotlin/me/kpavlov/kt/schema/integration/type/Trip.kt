package me.kpavlov.kt.schema.integration.type

import com.fasterxml.jackson.annotation.JacksonAnnotation
import me.kpavlov.kt.schema.Description
import me.kpavlov.kt.schema.Schema

// Minimal inline value class to test flattening: no companion factory, no extra members.
@JvmInline
value class Age(
    val value: Int,
)

@Description("Distance in km")
@JvmInline
value class Distance(
    val value: Double,
)

@Schema
data class Trip(
    @Description("Traveler's age")
    val travelerAge: Age,
    val distance: Distance,
)
