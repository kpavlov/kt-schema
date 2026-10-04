package me.kpavlov.kt.schema.integration.type

import io.kotest.assertions.json.shouldEqualJson
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test

class RecursiveValueClassTripSchemaTest {
    // language=json
    private val expectedSchema =
        $$"""
        {
          "$schema": "https://json-schema.org/draft/2020-12/schema",
          "$id": "me.kpavlov.kt.schema.integration.type.RecursiveValueClassTrip",
          "type": "object",
          "properties": {
            "wrapper": { "$ref": "#/$defs/me.kpavlov.kt.schema.integration.type.RecursiveWrapper" }
          },
          "required": ["wrapper"],
          "additionalProperties": false,
          "$defs": {
            "me.kpavlov.kt.schema.integration.type.RecursiveWrapper": {
              "type": "array",
              "items": { "$ref": "#/$defs/me.kpavlov.kt.schema.integration.type.RecursiveWrapper" }
            }
          }
        }
        """.trimIndent()

    @Test
    fun `defines self-wrapping inline value class by its wrapped array shape in jsonSchemaString`() {
        RecursiveValueClassTrip::class.jsonSchemaString shouldEqualJson expectedSchema
    }

    @Test
    fun `defines self-wrapping inline value class by its wrapped array shape in jsonSchema`() {
        RecursiveValueClassTrip::class.jsonSchema.toString() shouldEqualJson expectedSchema
    }

    @Test
    fun `defines inline value class wrapping a list of nullable selves by its array shape`() {
        // language=json
        NullableRecursiveValueClassTrip::class.jsonSchemaString shouldEqualJson
            $$"""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "$id": "me.kpavlov.kt.schema.integration.type.NullableRecursiveValueClassTrip",
              "type": "object",
              "properties": {
                "wrapper": { "$ref": "#/$defs/me.kpavlov.kt.schema.integration.type.NullableRecursiveWrapper" }
              },
              "required": ["wrapper"],
              "additionalProperties": false,
              "$defs": {
                "me.kpavlov.kt.schema.integration.type.NullableRecursiveWrapper": {
                  "type": "array",
                  "items": {
                    "oneOf": [
                      { "type": "null" },
                      { "$ref": "#/$defs/me.kpavlov.kt.schema.integration.type.NullableRecursiveWrapper" }
                    ]
                  }
                }
              }
            }
            """.trimIndent()
    }

    @Test
    fun `keeps nullability of the wrapped type on recursive inline value class references`() {
        // language=json
        OptionalRecursiveValueClassTrip::class.jsonSchemaString shouldEqualJson
            $$"""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "$id": "me.kpavlov.kt.schema.integration.type.OptionalRecursiveValueClassTrip",
              "type": "object",
              "properties": {
                "wrapper": {
                  "oneOf": [
                    { "type": "null" },
                    { "$ref": "#/$defs/me.kpavlov.kt.schema.integration.type.OptionalRecursiveWrapper" }
                  ]
                }
              },
              "required": ["wrapper"],
              "additionalProperties": false,
              "$defs": {
                "me.kpavlov.kt.schema.integration.type.OptionalRecursiveWrapper": {
                  "type": "array",
                  "items": {
                    "oneOf": [
                      { "type": "null" },
                      { "$ref": "#/$defs/me.kpavlov.kt.schema.integration.type.OptionalRecursiveWrapper" }
                    ]
                  }
                }
              }
            }
            """.trimIndent()
    }

    @Test
    fun `defines only the re-entered class of mutually recursive inline value classes`() {
        MutuallyRecursiveValueClassTrip::class.jsonSchema.getValue($$"$defs").jsonObject.keys shouldBe
            setOf("me.kpavlov.kt.schema.integration.type.Ping")
    }

    @Test
    fun `flattens inline value class whose cycle goes through an object`() {
        // language=json
        ValueClassCycleThroughObjectTrip::class.jsonSchemaString shouldEqualJson
            $$"""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "$id": "me.kpavlov.kt.schema.integration.type.ValueClassCycleThroughObjectTrip",
              "type": "object",
              "properties": {
                "holder": { "$ref": "#/$defs/me.kpavlov.kt.schema.integration.type.Chain" }
              },
              "required": ["holder"],
              "additionalProperties": false,
              "$defs": {
                "me.kpavlov.kt.schema.integration.type.Chain": {
                  "type": "object",
                  "properties": {
                    "value": { "type": "string" },
                    "next": {
                      "oneOf": [
                        { "type": "null" },
                        { "$ref": "#/$defs/me.kpavlov.kt.schema.integration.type.Chain" }
                      ]
                    }
                  },
                  "required": ["value", "next"],
                  "additionalProperties": false
                }
              }
            }
            """.trimIndent()
    }

    @Test
    fun `flattens nested instantiations of the same generic inline value class`() {
        // language=json
        NestedGenericValueClassTrip::class.jsonSchemaString shouldEqualJson
            $$"""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "$id": "me.kpavlov.kt.schema.integration.type.NestedGenericValueClassTrip",
              "type": "object",
              "properties": {
                "nested": { "type": "integer" }
              },
              "required": ["nested"],
              "additionalProperties": false
            }
            """.trimIndent()
    }

    @Test
    fun `cuts polymorphically recursive inline value class off as any value`() {
        val properties = PolymorphicallyRecursiveValueClassTrip::class.jsonSchema.getValue("properties")
        var nest = properties.jsonObject.getValue("nest")
        repeat(7) { nest = nest.jsonObject.getValue("items") }
        // The 8th level's items are "any value" ({}), which is omitted.
        nest.toString() shouldEqualJson """{"type": "array"}"""
    }
}
