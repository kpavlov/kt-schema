@file:JvmName("PropertyDefinitionUtils")

package me.kpavlov.kt.schema.generator.json

import me.kpavlov.kt.schema.generator.core.ir.Property
import me.kpavlov.kt.schema.json.AllOfPropertyDefinition
import me.kpavlov.kt.schema.json.AnyOfPropertyDefinition
import me.kpavlov.kt.schema.json.ArrayPropertyDefinition
import me.kpavlov.kt.schema.json.BooleanPropertyDefinition
import me.kpavlov.kt.schema.json.BooleanSchemaDefinition
import me.kpavlov.kt.schema.json.GenericPropertyDefinition
import me.kpavlov.kt.schema.json.JsonSchema
import me.kpavlov.kt.schema.json.JsonSchemaConstants.Types.NUMBER
import me.kpavlov.kt.schema.json.NumericPropertyDefinition
import me.kpavlov.kt.schema.json.ObjectPropertyDefinition
import me.kpavlov.kt.schema.json.OneOfPropertyDefinition
import me.kpavlov.kt.schema.json.PropertyDefinition
import me.kpavlov.kt.schema.json.ReferencePropertyDefinition
import me.kpavlov.kt.schema.json.StringPropertyDefinition
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.jvm.JvmName
import kotlin.math.floor

/**
 * Sets the const value on a property definition.
 * Only StringPropertyDefinition, NumericPropertyDefinition, and BooleanPropertyDefinition support const values.
 */
internal fun setConstValue(
    propertyDef: PropertyDefinition,
    constValue: Any?,
): PropertyDefinition {
    val jsonElement = toJsonElement(constValue) ?: return propertyDef

    return when (propertyDef) {
        is StringPropertyDefinition -> propertyDef.copy(constValue = jsonElement)
        is NumericPropertyDefinition -> propertyDef.copy(constValue = jsonElement)
        is BooleanPropertyDefinition -> propertyDef.copy(constValue = jsonElement)
        else -> propertyDef // Arrays and objects don't support const
    }
}

/**
 * Sets the default value on a property definition.
 */
internal fun setDefaultValue(
    propertyDef: PropertyDefinition,
    defaultValue: Any?,
): PropertyDefinition {
    val jsonElement = toJsonElement(coerceToDeclaredType(propertyDef, defaultValue)) ?: return propertyDef

    return when (propertyDef) {
        is StringPropertyDefinition -> propertyDef.copy(default = jsonElement)
        is NumericPropertyDefinition -> propertyDef.copy(default = jsonElement)
        is BooleanPropertyDefinition -> propertyDef.copy(default = jsonElement)
        is ArrayPropertyDefinition -> propertyDef.copy(default = jsonElement)
        is ObjectPropertyDefinition -> propertyDef.copy(default = jsonElement)
        is ReferencePropertyDefinition -> propertyDef.copy(default = jsonElement)
        is OneOfPropertyDefinition -> propertyDef.copy(default = jsonElement)
        else -> propertyDef
    }
}

/**
 * Coerces an annotation-sourced default value — always a raw `String`, e.g. from
 * `@JsonProperty(defaultValue = "30")` — to match [propertyDef]'s declared JSON type, so `default`
 * isn't emitted as a JSON string next to a numeric/boolean `type`. Values that are already
 * natively typed (e.g. a real Kotlin default obtained via reflection) pass through unchanged.
 *
 * @throws IllegalArgumentException if the string doesn't match [propertyDef]'s declared type,
 * e.g. `"3.14"` on an `integer` property or `"maybe"` on a `boolean` property. A whole-number
 * decimal string such as `"30.0"` is accepted for `integer` properties, per the JSON Schema
 * rule that any zero-fractional-part number satisfies `type: integer`.
 */
private fun coerceToDeclaredType(
    propertyDef: PropertyDefinition,
    value: Any?,
): Any? {
    if (value !is String) return value
    return when (propertyDef) {
        is NumericPropertyDefinition -> coerceNumericDefault(propertyDef, value)
        is BooleanPropertyDefinition ->
            requireNotNull(value.toBooleanStrictOrNull()) {
                "Annotation default '$value' is not a valid boolean for type ${propertyDef.type}"
            }
        else -> value
    }
}

private fun coerceNumericDefault(
    propertyDef: NumericPropertyDefinition,
    value: String,
): Number {
    val long = value.toLongOrNull()
    if (long != null) return long

    val double =
        requireNotNull(value.toDoubleOrNull()?.takeIf { it.isFinite() }) {
            "Annotation default '$value' is not a valid number for type ${propertyDef.type}"
        }
    return if (NUMBER in propertyDef.type) {
        double
    } else {
        require(double == floor(double)) {
            "Annotation default '$value' is not a valid integer for type ${propertyDef.type}"
        }
        double.toLong()
    }
}

/**
 * Applies a property's constant or default value to its definition, if applicable.
 *
 * A constant property gets its value emitted via `const`; a non-required property with a known
 * default value gets it emitted via `default`. Shared by both the plain JSON Schema and function
 * calling transformers.
 */
internal fun applyDefaultOrConst(
    propertyDef: PropertyDefinition,
    property: Property,
    isRequired: Boolean,
): PropertyDefinition =
    when {
        property.isConstant -> setConstValue(propertyDef, property.defaultValue)
        !isRequired && property.defaultValue != null -> setDefaultValue(propertyDef, property.defaultValue)
        else -> propertyDef
    }

/**
 * Sets the description on a property definition, if [PropertyDefinition] supports it.
 */
internal fun setDescription(
    propertyDef: PropertyDefinition,
    description: String,
): PropertyDefinition =
    when (propertyDef) {
        is StringPropertyDefinition -> propertyDef.copy(description = description)
        is NumericPropertyDefinition -> propertyDef.copy(description = description)
        is BooleanPropertyDefinition -> propertyDef.copy(description = description)
        is ArrayPropertyDefinition -> propertyDef.copy(description = description)
        is ObjectPropertyDefinition -> propertyDef.copy(description = description)
        is AnyOfPropertyDefinition -> propertyDef.copy(description = description)
        is OneOfPropertyDefinition -> propertyDef.copy(description = description)
        is GenericPropertyDefinition -> propertyDef.copy(description = description)
        is AllOfPropertyDefinition -> propertyDef.copy(description = description)
        is ReferencePropertyDefinition -> propertyDef.copy(description = description)
        is JsonSchema -> propertyDef.copy(description = description)
        is BooleanSchemaDefinition -> propertyDef // no description field
    }

/**
 * Removes the nullable flag from a property definition.
 */
internal fun removeNullableFlag(propertyDef: PropertyDefinition): PropertyDefinition =
    when (propertyDef) {
        is StringPropertyDefinition -> propertyDef.copy(nullable = null)
        is NumericPropertyDefinition -> propertyDef.copy(nullable = null)
        is BooleanPropertyDefinition -> propertyDef.copy(nullable = null)
        is ArrayPropertyDefinition -> propertyDef.copy(nullable = null)
        is ObjectPropertyDefinition -> propertyDef.copy(nullable = null)
        else -> propertyDef
    }

/**
 * Converts a Kotlin value to a JsonElement.
 */
private fun toJsonElement(value: Any?): JsonElement? =
    when (value) {
        null -> {
            JsonNull
        }

        is String -> {
            JsonPrimitive(value)
        }

        is Number -> {
            JsonPrimitive(value)
        }

        is Boolean -> {
            JsonPrimitive(value)
        }

        is Enum<*> -> {
            JsonPrimitive(value.name)
        }

        is List<*> -> {
            JsonArray(value.mapNotNull { toJsonElement(it) })
        }

        is Array<*> -> {
            JsonArray(value.mapNotNull { toJsonElement(it) })
        }

        is Map<*, *> -> {
            val entries =
                value.entries.mapNotNull { (k, v) ->
                    val key = k?.toString() ?: return@mapNotNull null
                    val element = toJsonElement(v) ?: return@mapNotNull null
                    key to element
                }
            JsonObject(entries.toMap())
        }

        else -> {
            null
        }
    }
