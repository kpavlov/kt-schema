package me.kpavlov.kt.schema.integration.type

import io.kotest.assertions.json.shouldEqualJson
import kotlin.test.Test

class WorkspacePathTripSchemaTest {
    // language=json
    private val expectedSchema =
        $$"""
        {
          "$schema": "https://json-schema.org/draft/2020-12/schema",
          "$id": "me.kpavlov.kt.schema.integration.type.WorkspacePathTrip",
          "type": "object",
          "properties": {
            "workspace": { "type": "string" },
            "count": { "type": "integer" }
          },
          "required": ["workspace", "count"],
          "additionalProperties": false
        }
        """.trimIndent()

    @Test
    fun `flattens inline value class from another module in jsonSchemaString`() {
        WorkspacePathTrip::class.jsonSchemaString shouldEqualJson expectedSchema
    }

    @Test
    fun `flattens inline value class from another module in jsonSchema`() {
        WorkspacePathTrip::class.jsonSchema.toString() shouldEqualJson expectedSchema
    }
}
