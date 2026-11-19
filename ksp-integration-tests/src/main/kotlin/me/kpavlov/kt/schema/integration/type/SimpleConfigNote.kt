package me.kpavlov.kt.schema.integration.type

import me.kpavlov.kt.schema.Schema

// Uses the `simple` config through the annotation, while the module is processed with the strict default
@Schema(config = "simple")
data class SimpleConfigNote(
    val title: String,
    val body: String? = null,
)

@Schema(config = "simple")
@Suppress("unused", "FunctionOnlyReturningConstant")
fun tagNote(
    title: String,
    tag: String? = null,
): String = title
