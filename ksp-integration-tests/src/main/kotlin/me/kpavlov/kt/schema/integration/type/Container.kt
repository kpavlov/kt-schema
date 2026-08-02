package me.kpavlov.kt.schema.integration.type

import kotlinx.serialization.SerialName
import me.kpavlov.kt.schema.Description
import me.kpavlov.kt.schema.Schema

/**
 * Generic class to test KSP with generics
 */
@Description("A generic container that wraps content with optional metadata.")
@Schema
@SerialName("Container")
data class Container<T>(
    @Description("The wrapped content value")
    val content: T,
    @Description("Arbitrary metadata key-value pairs")
    val metadata: Map<String, Any> = emptyMap(),
)
