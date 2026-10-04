package me.kpavlov.kt.schema.integration.type

import io.kotest.assertions.json.shouldEqualJson
import kotlin.test.Test

class GenericWrapperTripSchemaTest {
    // language=json
    private val expectedSchema =
        $$"""
        {
          "$schema": "https://json-schema.org/draft/2020-12/schema",
          "$id": "me.kpavlov.kt.schema.integration.type.GenericWrapperTrip",
          "type": "object",
          "properties": {
            "intWrapper": {
              "type": "integer"
            },
            "nullableStringWrapper": {
              "type": ["string", "null"]
            },
            "listWrapper": {
              "type": "array",
              "items": {
                "type": "integer"
              }
            }
          },
          "required": ["intWrapper", "nullableStringWrapper", "listWrapper"],
          "additionalProperties": false
        }
        """.trimIndent()

    @Test
    fun `resolves type arguments of generic inline value classes in jsonSchemaString`() {
        GenericWrapperTrip::class.jsonSchemaString shouldEqualJson expectedSchema
    }

    @Test
    fun `resolves type arguments of generic inline value classes in jsonSchema`() {
        GenericWrapperTrip::class.jsonSchema.toString() shouldEqualJson expectedSchema
    }
}
