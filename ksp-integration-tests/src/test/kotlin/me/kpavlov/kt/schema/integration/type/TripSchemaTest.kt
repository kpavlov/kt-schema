package me.kpavlov.kt.schema.integration.type

import io.kotest.assertions.json.shouldEqualJson
import kotlin.test.Test

class TripSchemaTest {
    // language=json
    private val expectedSchema =
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

    @Test
    fun `flattens inline value class properties to their wrapped primitive type`() {
        Trip::class.jsonSchemaString shouldEqualJson expectedSchema
    }

    @Test
    fun `flattens inline value class properties to their wrapped primitive type in jsonSchema`() {
        Trip::class.jsonSchema.toString() shouldEqualJson expectedSchema
    }
}
