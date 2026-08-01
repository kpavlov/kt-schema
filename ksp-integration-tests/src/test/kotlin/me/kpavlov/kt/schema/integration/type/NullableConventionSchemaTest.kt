package me.kpavlov.kt.schema.integration.type

import io.kotest.assertions.json.shouldEqualJson
import kotlin.test.Test

/**
 * Tests for the `*Opt` type-name pattern and `@Nullable`-style annotation conventions:
 * both mark a property nullable the same way Kotlin's `?` does.
 */
class NullableConventionSchemaTest {
    @Test
    fun `Opt-suffixed type and param- and getter-targeted Nullable-annotated properties as nullable`() {
        val schema = NullableConvention::class.jsonSchemaString

        // language=json
        schema shouldEqualJson
            $$"""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "$id": "me.kpavlov.kt.schema.integration.type.NullableConvention",
              "$defs": {
                "me.kpavlov.kt.schema.integration.type.EmailOpt": {
                  "type": "object",
                  "properties": {
                    "value": { "type": "string" }
                  },
                  "required": ["value"],
                  "additionalProperties": false
                }
              },
              "type": "object",
              "properties": {
                "name": { "type": "string" },
                "email": {
                  "oneOf": [
                    { "type": "null" },
                    { "$ref": "#/$defs/me.kpavlov.kt.schema.integration.type.EmailOpt" }
                  ]
                },
                "phone": { "type": ["string", "null"] },
                "fax": { "type": ["string", "null"] }
              },
              "required": ["name", "email", "phone", "fax"],
              "additionalProperties": false
            }
            """.trimIndent()
    }
}
