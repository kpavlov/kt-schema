package me.kpavlov.kt.schema.integration.type

import io.kotest.assertions.json.shouldEqualJson
import kotlin.test.Test

/**
 * Tests for the `@Nullable`-style annotation convention: marks a property nullable the same
 * way Kotlin's `?` does, regardless of whether the annotation targets the constructor parameter
 * or the property getter.
 */
class NullableConventionSchemaTest {
    @Test
    fun `param- and getter-targeted Nullable-annotated properties are nullable`() {
        val schema = NullableConvention::class.jsonSchemaString

        // language=json
        schema shouldEqualJson
            $$"""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "$id": "me.kpavlov.kt.schema.integration.type.NullableConvention",
              "type": "object",
              "properties": {
                "name": { "type": "string" },
                "phone": { "type": ["string", "null"] },
                "fax": { "type": ["string", "null"] }
              },
              "required": ["name", "phone", "fax"],
              "additionalProperties": false
            }
            """.trimIndent()
    }
}
