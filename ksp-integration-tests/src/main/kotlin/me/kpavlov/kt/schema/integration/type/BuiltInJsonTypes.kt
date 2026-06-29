/*
 * Copyright © 2026 Konstantin Pavlov and contributors
 */
package me.kpavlov.kt.schema.integration.type

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import me.kpavlov.kt.schema.Schema

@Schema
@Serializable
data class BuiltInJsonTypes(
    val objProp: JsonObject,
    val objPropOpt: JsonObject?,
    val elementProp: JsonElement,
    val elementPropOpt: JsonElement?,
    val arrayProp: JsonArray,
    val arrayPropOpt: JsonArray?,
    val primitive: JsonPrimitive,
    val primitiveOpt: JsonPrimitive?,
    val nullProp: JsonNull,
    val nullPropOpt: JsonNull?,
)
