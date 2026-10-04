package me.kpavlov.kt.schema.integration.type

import io.kotest.assertions.json.shouldEqualJson
import kotlin.test.Test

class DescribedTagsTripSchemaTest {
    // language=json
    private val expectedSchema =
        $$"""
        {
          "$schema": "https://json-schema.org/draft/2020-12/schema",
          "$id": "me.kpavlov.kt.schema.integration.type.DescribedTagsTrip",
          "type": "object",
          "properties": {
            "tags": {
              "type": "array",
              "description": "Tag list",
              "items": { "type": "string" }
            }
          },
          "required": ["tags"],
          "additionalProperties": false
        }
        """.trimIndent()

    @Test
    fun `carries class description onto flattened collection of inline value class in jsonSchemaString`() {
        DescribedTagsTrip::class.jsonSchemaString shouldEqualJson expectedSchema
    }

    @Test
    fun `carries class description onto flattened collection of inline value class in jsonSchema`() {
        DescribedTagsTrip::class.jsonSchema.toString() shouldEqualJson expectedSchema
    }
}
