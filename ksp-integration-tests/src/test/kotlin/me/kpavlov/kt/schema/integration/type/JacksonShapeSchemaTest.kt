package me.kpavlov.kt.schema.integration.type

import io.kotest.assertions.json.shouldEqualJson
import kotlin.test.Test

/**
 * Tests for Jackson @JsonTypeName polymorphic subtype naming.
 */
class JacksonShapeSchemaTest {
    @Test
    fun `@JsonTypeName overrides subtype names in defs and discriminator`() {
        val schema = JacksonShape::class.jsonSchemaString

        // language=json
        schema shouldEqualJson
            $$"""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "$id": "me.kpavlov.kt.schema.integration.type.JacksonShape",
              "description": "A shape described with Jackson @JsonTypeName annotations.",
              "type": "object",
              "additionalProperties": false,
              "oneOf": [
                {
                  "$ref": "#/$defs/circle"
                },
                {
                  "$ref": "#/$defs/square"
                }
              ],
              "$defs": {
                "circle": {
                  "type": "object",
                  "properties": {
                    "type": {
                      "type": "string",
                      "const": "circle"
                    },
                    "radius": {
                      "type": "number"
                    }
                  },
                  "required": [
                    "type",
                    "radius"
                  ],
                  "additionalProperties": false
                },
                "square": {
                  "type": "object",
                  "properties": {
                    "type": {
                      "type": "string",
                      "const": "square"
                    },
                    "side": {
                      "type": "number"
                    }
                  },
                  "required": [
                    "type",
                    "side"
                  ],
                  "additionalProperties": false
                }
              }
            }
            """.trimIndent()
    }
}
