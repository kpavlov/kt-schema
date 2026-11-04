package me.kpavlov.kt.schema.generator.json.serialization

import io.kotest.assertions.json.shouldEqualJson
import me.kpavlov.kt.schema.generator.json.serialization.SerializationClassJsonSchemaGenerator.Companion.jsonSchemaOf
import me.kpavlov.kt.schema.json.encodeToString
import me.kpavlov.kt.schema.test.fixtures.BuiltInTypes
import kotlin.test.Test

class BuiltInConstraintsSerializationTest {
    @Test
    fun `should emit built-in bounds and formats`() {
        val schema = jsonSchemaOf<BuiltInTypes>().encodeToString()

        // language=json
        schema shouldEqualJson
            $$"""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "$id": "me.kpavlov.kt.schema.test.fixtures.BuiltInTypes",
              "type": "object",
              "properties": {
                "tiny": { "type": "integer", "minimum": -128, "maximum": 127 },
                "small": { "type": "integer", "minimum": -32768, "maximum": 32767 },
                "letter": { "type": "string", "minLength": 1, "maxLength": 1 },
                "ubyte": { "type": "integer", "minimum": 0, "maximum": 255 },
                "ushort": { "type": "integer", "minimum": 0, "maximum": 65535 },
                "uint": { "type": "integer", "minimum": 0, "maximum": 4294967295 },
                "ulong": { "type": "integer", "minimum": 0 },
                "optional": { "type": ["integer", "null"], "minimum": -32768, "maximum": 32767 },
                "uid": { "type": "string", "format": "uuid" },
                "at": { "type": "string", "format": "date-time" },
                "count": { "type": "integer" }
              },
              "required": ["tiny", "small", "letter", "ubyte", "ushort", "uint", "ulong", "optional", "uid", "at", "count"],
              "additionalProperties": false
            }
            """.trimIndent()
    }
}
