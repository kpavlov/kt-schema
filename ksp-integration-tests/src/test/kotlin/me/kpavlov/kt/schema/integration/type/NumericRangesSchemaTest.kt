package me.kpavlov.kt.schema.integration.type

import io.kotest.assertions.json.shouldEqualJson
import kotlin.test.Test

class NumericRangesSchemaTest {
    // language=json
    private val expectedSchema =
        $$"""
        {
          "$schema": "https://json-schema.org/draft/2020-12/schema",
          "$id": "me.kpavlov.kt.schema.integration.type.NumericRanges",
          "type": "object",
          "properties": {
            "tiny": { "type": "integer", "minimum": -128, "maximum": 127 },
            "small": { "type": "integer", "minimum": -32768, "maximum": 32767 },
            "letter": { "type": "string", "minLength": 1, "maxLength": 1 },
            "ubyte": { "type": "integer", "minimum": 0, "maximum": 255 },
            "ushort": { "type": "integer", "minimum": 0, "maximum": 65535 },
            "uint": { "type": "integer", "minimum": 0, "maximum": 4294967295 },
            "ulong": { "type": "integer", "minimum": 0 },
            "count": { "type": "integer" },
            "raw": { "type": "array", "items": { "type": "integer", "minimum": -128, "maximum": 127 } },
            "optional": { "type": ["integer", "null"], "minimum": -32768, "maximum": 32767 }
          },
          "required": ["tiny", "small", "letter", "ubyte", "ushort", "uint", "ulong", "count", "raw", "optional"],
          "additionalProperties": false
        }
        """.trimIndent()

    @Test
    fun `emits built-in bounds in jsonSchemaString`() {
        NumericRanges::class.jsonSchemaString shouldEqualJson expectedSchema
    }

    @Test
    fun `emits built-in bounds in jsonSchema`() {
        NumericRanges::class.jsonSchema.toString() shouldEqualJson expectedSchema
    }
}
