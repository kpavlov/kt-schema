package me.kpavlov.kt.schema.integration.type

import com.fasterxml.jackson.annotation.JsonIgnoreType
import me.kpavlov.kt.schema.Description
import me.kpavlov.kt.schema.Schema
import me.kpavlov.kt.schema.SchemaIgnore

/**
 * Test model for @SchemaIgnore on sealed subtypes.
 */
@Description("An application event")
@Schema
sealed class Event {
    abstract val timestamp: Long

    @Description("User clicked on an element")
    data class Click(
        override val timestamp: Long,
        @Description("X coordinate")
        val x: Int,
        @Description("Y coordinate")
        val y: Int,
    ) : Event()

    @Description("Page was viewed")
    data class PageView(
        override val timestamp: Long,
        @Description("Page URL")
        val url: String,
    ) : Event()

    @SchemaIgnore
    data class Internal(
        override val timestamp: Long,
        val trace: String,
    ) : Event()

    @JsonIgnoreType
    data class Jackson(
        override val timestamp: Long,
        val json: String,
    ) : Event()
}
