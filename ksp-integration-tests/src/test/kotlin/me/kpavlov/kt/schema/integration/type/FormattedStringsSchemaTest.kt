package me.kpavlov.kt.schema.integration.type

import io.kotest.assertions.json.shouldEqualJson
import kotlin.test.Test

class FormattedStringsSchemaTest {
    // language=json
    private val expectedSchema =
        $$"""
        {
          "$schema": "https://json-schema.org/draft/2020-12/schema",
          "$id": "me.kpavlov.kt.schema.integration.type.FormattedStrings",
          "type": "object",
          "properties": {
            "id": { "type": "string", "format": "uuid" },
            "uid": { "type": "string", "format": "uuid" },
            "at": { "type": "string", "format": "date-time" },
            "day": { "type": "string", "format": "date" },
            "clock": { "type": "string", "format": "time" },
            "stamp": { "type": "string", "format": "date-time" },
            "local": { "type": "string" }
          },
          "required": ["id", "uid", "at", "day", "clock", "stamp", "local"],
          "additionalProperties": false
        }
        """.trimIndent()

    @Test
    fun `emits string formats in jsonSchemaString`() {
        FormattedStrings::class.jsonSchemaString shouldEqualJson expectedSchema
    }

    @Test
    fun `emits string formats in jsonSchema`() {
        FormattedStrings::class.jsonSchema.toString() shouldEqualJson expectedSchema
    }
}
