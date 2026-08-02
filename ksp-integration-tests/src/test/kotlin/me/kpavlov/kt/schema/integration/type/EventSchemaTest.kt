package me.kpavlov.kt.schema.integration.type

import io.kotest.assertions.json.shouldEqualJson
import kotlin.test.Test

/**
 * Tests for @SchemaIgnore on sealed subtypes — ignored subtypes must not appear
 * in the polymorphic oneOf schema or $defs.
 */
class EventSchemaTest {
    @Test
    fun `sealed class excludes @SchemaIgnore and @JsonIgnoreType subtypes from oneOf schema`() {
        val schema = Event::class.jsonSchemaString

        // language=json
        schema shouldEqualJson
            $$"""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "$id": "Event",
              "description": "An application event",
              "type": "object",
              "additionalProperties": false,
              "oneOf": [
                {
                  "$ref": "#/$defs/Click"
                },
                {
                  "$ref": "#/$defs/PageView"
                }
              ],
              "$defs": {
                "Click": {
                  "type": "object",
                  "description": "User clicked on an element",
                  "properties": {
                    "type": {
                      "type": "string",
                      "const": "Click"
                    },
                    "timestamp": {
                      "type": "integer"
                    },
                    "x": {
                      "type": "integer",
                      "description": "X coordinate"
                    },
                    "y": {
                      "type": "integer",
                      "description": "Y coordinate"
                    }
                  },
                  "required": [
                    "type",
                    "timestamp",
                    "x",
                    "y"
                  ],
                  "additionalProperties": false
                },
                "PageView": {
                  "type": "object",
                  "description": "Page was viewed",
                  "properties": {
                    "type": {
                      "type": "string",
                      "const": "PageView"
                    },
                    "timestamp": {
                      "type": "integer"
                    },
                    "url": {
                      "type": "string",
                      "description": "Page URL"
                    }
                  },
                  "required": [
                    "type",
                    "timestamp",
                    "url"
                  ],
                  "additionalProperties": false
                }
              }
            }
            """.trimIndent()
    }
}
