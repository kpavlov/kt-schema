package me.kpavlov.kt.schema.integration.type

import com.fasterxml.jackson.annotation.JsonTypeName
import me.kpavlov.kt.schema.Description
import me.kpavlov.kt.schema.Schema

/**
 * Sealed hierarchy using Jackson @JsonTypeName to override the polymorphic
 * discriminator value and $defs key for each subtype.
 */
@Schema
@Description("A shape described with Jackson @JsonTypeName annotations.")
sealed class JacksonShape {
    @JsonTypeName("circle")
    data class Circle(val radius: Double) : JacksonShape()

    @JsonTypeName("square")
    data class Square(val side: Double) : JacksonShape()
}
