package me.kpavlov.kt.schema.integration.type

import io.kotest.assertions.json.shouldEqualJson
import kotlin.test.Test

/**
 * Tests for the `*Opt` type-name pattern and `@Nullable`-style annotation conventions:
 * both mark a property nullable the same way Kotlin's `?` does.
 */
class NullableConventionSchemaTest {
    @Test
    fun `generates schema marking Opt-suffixed type and Nullable-annotated property as nullable`() {
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
                "phone": { "type": ["string", "null"] }
              },
              "required": ["name", "email", "phone"],
              "additionalProperties": false
            }
            """.trimIndent()
    }
}
