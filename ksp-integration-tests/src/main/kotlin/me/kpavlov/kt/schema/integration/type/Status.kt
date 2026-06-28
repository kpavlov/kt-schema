package me.kpavlov.kt.schema.integration.type

import me.kpavlov.kt.schema.Description
import me.kpavlov.kt.schema.Schema

/**
 * Enum class with Schema annotation
 */
@Description("Current lifecycle status of an entity.")
@Schema
enum class Status {
    @Description("Entity is active and usable")
    ACTIVE,

    @Description("Entity is inactive or disabled")
    INACTIVE,

    @Description("Entity is pending activation or approval")
    PENDING,
}
