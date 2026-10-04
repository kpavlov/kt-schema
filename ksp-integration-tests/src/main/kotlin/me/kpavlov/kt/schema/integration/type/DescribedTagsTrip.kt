package me.kpavlov.kt.schema.integration.type

import me.kpavlov.kt.schema.Description
import me.kpavlov.kt.schema.Schema

@Description("Tag list")
@JvmInline
value class DescribedTags(
    val items: List<String>,
)

@Schema
data class DescribedTagsTrip(
    val tags: DescribedTags,
)
