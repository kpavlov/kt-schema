package me.kpavlov.kt.schema.generator.json

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.json.shouldEqualJson
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import kotlinx.serialization.json.Json
import kotlin.test.Test

data class Message(
    val text: String,
    val priority: Int = 0,
    val note: String? = null,
    val author: String?,
    val attachment: Attachment? = null,
)

data class Attachment(
    val url: String,
)

/**
 * Tests for [ReflectionClassJsonSchemaGenerator] with [JsonSchemaConfig.Lenient].
 *
 * Lenient is compact and permissive: only non-nullable fields without defaults are required,
 * nullable types carry no null marker, and extra properties are allowed.
 */
class LenientSchemaGeneratorTest {
    private val generator =
        ReflectionClassJsonSchemaGenerator(
            json = Json { encodeDefaults = false },
            config = JsonSchemaConfig.Lenient,
        )

    @Test
    fun `should generate permissive schema for class with nullable and default fields`() {
        // Given
        val target = Message::class

        // When
        val schema = generator.generateSchemaString(target)

        // Then
        schema shouldEqualJson
            // language=json
            $$"""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "$id": "me.kpavlov.kt.schema.generator.json.Message",
              "type": "object",
              "properties": {
                "text":       { "type": "string" },
                "priority":   { "type": "integer", "default": 0 },
                "note":       { "type": "string" },
                "author":     { "type": "string" },
                "attachment": { "$ref": "#/$defs/me.kpavlov.kt.schema.generator.json.Attachment" }
              },
              "required": ["text"],
              "$defs": {
                "me.kpavlov.kt.schema.generator.json.Attachment": {
                  "type": "object",
                  "properties": { "url": { "type": "string" } },
                  "required": ["url"]
                }
              }
            }
            """.trimIndent()
    }

    @Test
    fun `should not emit additionalProperties or null markers`() {
        // When
        val schema = generator.generateSchemaString(Message::class)

        // Then
        assertSoftly(schema) {
            shouldNotContain("additionalProperties")
            shouldNotContain("\"null\"")
            shouldNotContain("nullable")
        }
    }

    @Test
    fun `should not require nullable field without default`() {
        // When
        val schema = generator.generateSchema(Message::class)

        // Then
        assertSoftly(schema) {
            required shouldBe listOf("text")
            additionalProperties shouldBe null
        }
    }
}
