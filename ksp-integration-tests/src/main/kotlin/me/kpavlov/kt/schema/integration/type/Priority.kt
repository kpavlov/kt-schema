package me.kpavlov.kt.schema.integration.type

import kotlinx.serialization.SerialName
import me.kpavlov.kt.schema.Schema

/**
 * Enum class with SerialName overrides on the class and its entries.
 */
@SerialName("PriorityLevel")
@Schema
enum class Priority {
    @SerialName("p0_critical")
    CRITICAL,

    @SerialName("p1_high")
    HIGH,

    @SerialName("p2_low")
    LOW,
}
