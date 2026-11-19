package me.kpavlov.kt.schema.integration.type

import io.kotest.assertions.json.shouldEqualJson
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlin.test.Test

class SchemaConfigParameterTest {
    @Test
    fun `class schema uses the config named by @Schema`() {
        val schema = SimpleConfigNote::class.jsonSchemaString

        // language=json
        schema shouldEqualJson
            $$"""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "$id": "me.kpavlov.kt.schema.integration.type.SimpleConfigNote",
              "type": "object",
              "properties": {
                "title": { "type": "string" },
                "body": { "type": ["string", "null"] }
              },
              "additionalProperties": false,
              "required": ["title"]
            }
            """.trimIndent()
    }

    @Test
    fun `class jsonSchema matches jsonSchemaString`() {
        SimpleConfigNote::class.jsonSchema.toString() shouldEqualJson SimpleConfigNote::class.jsonSchemaString
    }

    @Test
    fun `function schema uses the config named by @Schema`() {
        val schema = tagNoteJsonSchemaString()

        // language=json
        schema shouldEqualJson
            """
            {
              "type": "function",
              "name": "tagNote",
              "parameters": {
                "type": "object",
                "properties": {
                  "title": { "type": "string" },
                  "tag": { "type": ["string", "null"] }
                },
                "required": ["title"],
                "additionalProperties": false
              }
            }
            """.trimIndent()
    }

    @Test
    fun `function jsonSchema matches jsonSchemaString`() {
        tagNoteJsonSchema() shouldBe Json.parseToJsonElement(tagNoteJsonSchemaString())
    }
}
