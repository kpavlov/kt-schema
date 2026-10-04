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
            "wrapper": {
              "type": "array",
              "items": {
                "$ref": "#/$defs/me.kpavlov.kt.schema.integration.type.RecursiveWrapper"
              }
            }
          },
          "required": ["wrapper"],
          "additionalProperties": false,
          "$defs": {
            "me.kpavlov.kt.schema.integration.type.RecursiveWrapper": {
              "type": "object",
              "properties": {
                "items": {
                  "type": "array",
                  "items": {
                    "$ref": "#/$defs/me.kpavlov.kt.schema.integration.type.RecursiveWrapper"
                  }
                }
              },
              "required": ["items"],
              "additionalProperties": false
            }
          }
        }
        """.trimIndent()

    @Test
    fun `registers the fallback object node of a self-wrapping inline value class in jsonSchemaString`() {
        RecursiveValueClassTrip::class.jsonSchemaString shouldEqualJson expectedSchema
    }

    @Test
    fun `registers the fallback object node of a self-wrapping inline value class in jsonSchema`() {
        RecursiveValueClassTrip::class.jsonSchema.toString() shouldEqualJson expectedSchema
    }

    @Test
    fun `stops flattening at nullable self-reference of inline value class`() {
        // language=json
        NullableRecursiveValueClassTrip::class.jsonSchemaString shouldEqualJson
            $$"""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "$id": "me.kpavlov.kt.schema.integration.type.NullableRecursiveValueClassTrip",
              "type": "object",
              "properties": {
                "wrapper": {
                  "type": "array",
                  "items": {
                    "oneOf": [
                      { "type": "null" },
                      { "$ref": "#/$defs/me.kpavlov.kt.schema.integration.type.NullableRecursiveWrapper" }
                    ]
                  }
                }
              },
              "required": ["wrapper"],
              "additionalProperties": false,
              "$defs": {
                "me.kpavlov.kt.schema.integration.type.NullableRecursiveWrapper": {
                  "type": "object",
                  "properties": {
                    "items": {
                      "type": "array",
                      "items": {
                        "oneOf": [
                          { "type": "null" },
                          { "$ref": "#/$defs/me.kpavlov.kt.schema.integration.type.NullableRecursiveWrapper" }
                        ]
                      }
                    }
                  },
                  "required": ["items"],
                  "additionalProperties": false
                }
              }
            }
            """.trimIndent()
    }

    @Test
    fun `registers fallback definitions for mutually recursive inline value classes`() {
        MutuallyRecursiveValueClassTrip::class.jsonSchema.getValue($$"$defs").jsonObject.keys shouldBe
            setOf(
                "me.kpavlov.kt.schema.integration.type.Ping",
                "me.kpavlov.kt.schema.integration.type.Pong",
            )
    }
}
