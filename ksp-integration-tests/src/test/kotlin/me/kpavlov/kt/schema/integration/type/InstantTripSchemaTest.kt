package me.kpavlov.kt.schema.integration.type

import io.kotest.assertions.json.shouldEqualJson
import kotlin.test.Test

class InstantTripSchemaTest {
    // language=json
    private val expectedSchema =
        $$"""
        {
          "$schema": "https://json-schema.org/draft/2020-12/schema",
          "$id": "me.kpavlov.kt.schema.integration.type.InstantTrip",
          "type": "object",
          "properties": {
            "departure": { "type": "string" },
            "arrival": { "type": ["string", "null"] }
          },
          "required": ["departure", "arrival"],
          "additionalProperties": false
        }
        """.trimIndent()

    @Test
    fun `maps kotlin time Instant to string in jsonSchemaString`() {
        InstantTrip::class.jsonSchemaString shouldEqualJson expectedSchema
    }

    @Test
    fun `maps kotlin time Instant to string in jsonSchema`() {
        InstantTrip::class.jsonSchema.toString() shouldEqualJson expectedSchema
    }
}
