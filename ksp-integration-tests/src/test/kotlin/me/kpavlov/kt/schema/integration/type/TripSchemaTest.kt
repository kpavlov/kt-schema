package me.kpavlov.kt.schema.integration.type

import io.kotest.assertions.json.shouldEqualJson
import kotlin.test.Test

/**
 * Tests for Trip schema generation - inline value class flattening.
 */
class TripSchemaTest {
    @Test
    fun `flattens inline value class properties to their wrapped primitive type`() {
        val schema = Trip::class.jsonSchemaString

        // language=json
        schema shouldEqualJson
            $$"""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "$id": "me.kpavlov.kt.schema.integration.type.Trip",
              "type": "object",
              "properties": {
                "travelerAge": {
                  "type": "integer",
                  "description": "Traveler's age"
                },
                "distance": {
                  "type": "number",
                  "description": "Distance in km"
                }
              },
              "required": ["travelerAge", "distance"],
              "additionalProperties": false
            }
            """.trimIndent()
    }
}
