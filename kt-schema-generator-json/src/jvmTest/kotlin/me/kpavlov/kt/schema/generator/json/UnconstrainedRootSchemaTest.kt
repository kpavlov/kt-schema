package me.kpavlov.kt.schema.generator.json

import io.kotest.assertions.json.shouldEqualJson
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlin.test.Test

class UnconstrainedRootSchemaTest {
    // language=json
    private val expectedSchema =
        $$"""
        {
          "$schema": "https://json-schema.org/draft/2020-12/schema",
          "$id": "Any",
          "type": ["array", "boolean", "null", "number", "object", "string"]
        }
        """.trimIndent()

    @Test
    fun `should accept any JSON value for Any root with default config`() {
        // Arrange
        val generator = ReflectionClassJsonSchemaGenerator(Json { encodeDefaults = false }, JsonSchemaConfig.Default)

        // Act
        val schemaString = generator.generateSchemaString(Any::class)
        val schema = generator.generateSchema(Any::class)

        // Assert
        schemaString shouldEqualJson expectedSchema
        schema.type shouldBe listOf("array", "boolean", "null", "number", "object", "string")
        schema.additionalProperties shouldBe null
    }

    @Test
    fun `should accept any JSON value for Any root with strict config`() {
        // Arrange
        val generator =
            ReflectionClassJsonObjectSchemaGenerator(Json { encodeDefaults = false }, JsonSchemaConfig.Strict)

        // Act
        val schemaString = generator.generateSchemaString(Any::class)

        // Assert
        schemaString shouldEqualJson expectedSchema
    }
}
