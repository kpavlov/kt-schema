package me.kpavlov.kt.schema.generator.json

import io.kotest.assertions.json.shouldEqualJson
import kotlinx.serialization.json.Json
import kotlin.test.Test

class GenericObjectSchemaTest {
    data class Box<T>(
        val value: T,
    )

    data class Payload(
        val text: Box<String>,
        val count: Box<Int>,
        val nullableText: Box<String?>,
        val ids: Box<List<Int>>,
    )

    private val pkg = "me.kpavlov.kt.schema.generator.json.GenericObjectSchemaTest"

    private fun schemaOf(config: JsonSchemaConfig) =
        ReflectionClassJsonSchemaGenerator(json = Json { encodeDefaults = false }, config = config)
            .generateSchemaString(Payload::class)

    @Test
    fun `Lenient should emit a definition per application under a name with its arguments`() {
        schemaOf(JsonSchemaConfig.Lenient) shouldEqualJson
            // language=json
            $$"""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "$id": "Payload",
              "type": "object",
              "properties": {
                "text": { "$ref": "#/$defs/Box_of_String" },
                "count": { "$ref": "#/$defs/Box_of_Int" },
                "nullableText": { "$ref": "#/$defs/Box_of_nullable_String" },
                "ids": { "$ref": "#/$defs/Box_of_List_of_Int" }
              },
              "required": ["text", "count", "nullableText", "ids"],
              "$defs": {
                "Box_of_String": {
                  "type": "object",
                  "properties": { "value": { "type": "string" } },
                  "required": ["value"]
                },
                "Box_of_Int": {
                  "type": "object",
                  "properties": { "value": { "type": "integer" } },
                  "required": ["value"]
                },
                "Box_of_nullable_String": {
                  "type": "object",
                  "properties": { "value": { "type": "string" } },
                  "required": []
                },
                "Box_of_List_of_Int": {
                  "type": "object",
                  "properties": { "value": { "type": "array", "items": { "type": "integer" } } },
                  "required": ["value"]
                }
              }
            }
            """.trimIndent()
    }

    @Test
    fun `Strict should keep the declaration name fully qualified`() {
        schemaOf(JsonSchemaConfig.Strict) shouldEqualJson
            // language=json
            $$"""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "$id": "$$pkg.Payload",
              "type": "object",
              "properties": {
                "text": { "$ref": "#/$defs/$$pkg.Box_of_String" },
                "count": { "$ref": "#/$defs/$$pkg.Box_of_Int" },
                "nullableText": { "$ref": "#/$defs/$$pkg.Box_of_nullable_String" },
                "ids": { "$ref": "#/$defs/$$pkg.Box_of_List_of_Int" }
              },
              "required": ["text", "count", "nullableText", "ids"],
              "additionalProperties": false,
              "$defs": {
                "$$pkg.Box_of_String": {
                  "type": "object",
                  "properties": { "value": { "type": "string" } },
                  "required": ["value"],
                  "additionalProperties": false
                },
                "$$pkg.Box_of_Int": {
                  "type": "object",
                  "properties": { "value": { "type": "integer" } },
                  "required": ["value"],
                  "additionalProperties": false
                },
                "$$pkg.Box_of_nullable_String": {
                  "type": "object",
                  "properties": { "value": { "type": ["string", "null"] } },
                  "required": ["value"],
                  "additionalProperties": false
                },
                "$$pkg.Box_of_List_of_Int": {
                  "type": "object",
                  "properties": { "value": { "type": "array", "items": { "type": "integer" } } },
                  "required": ["value"],
                  "additionalProperties": false
                }
              }
            }
            """.trimIndent()
    }
}
