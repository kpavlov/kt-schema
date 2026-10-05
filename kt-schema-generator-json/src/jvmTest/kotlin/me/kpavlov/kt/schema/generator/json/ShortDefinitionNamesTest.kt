package me.kpavlov.kt.schema.generator.json

import com.fasterxml.jackson.annotation.JsonTypeName
import io.kotest.assertions.json.shouldEqualJson
import kotlinx.serialization.json.Json
import kotlin.test.Test

class ShortDefinitionNamesTest {
    @JsonTypeName("Shared")
    data class TextValue(
        val text: String,
    )

    sealed interface ResultA {
        data class Success(
            val value: String,
        ) : ResultA
    }

    sealed interface ResultB {
        data class Success(
            val data: Int,
        ) : ResultB
    }

    data class Payload(
        val textValue: TextValue,
        val a: ResultA,
        val b: ResultB,
    )

    private val pkg = "me.kpavlov.kt.schema.generator.json.ShortDefinitionNamesTest"

    private fun schemaOf(config: JsonSchemaConfig) =
        ReflectionClassJsonSchemaGenerator(json = Json { encodeDefaults = false }, config = config)
            .generateSchemaString(Payload::class)

    @Test
    fun `Lenient should shorten defs keys refs id and discriminator const`() {
        schemaOf(JsonSchemaConfig.Lenient) shouldEqualJson
            // language=json
            $$"""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "$id": "Payload",
              "type": "object",
              "properties": {
                "textValue": { "$ref": "#/$defs/Shared" },
                "a": { "$ref": "#/$defs/ResultA" },
                "b": { "$ref": "#/$defs/ResultB" }
              },
              "required": ["textValue", "a", "b"],
              "$defs": {
                "Shared": {
                  "type": "object",
                  "properties": { "text": { "type": "string" } },
                  "required": ["text"]
                },
                "ResultA": { "oneOf": [ { "$ref": "#/$defs/ResultA.Success" } ] },
                "ResultA.Success": {
                  "type": "object",
                  "properties": {
                    "type": { "type": "string", "const": "ResultA.Success" },
                    "value": { "type": "string" }
                  },
                  "required": ["type", "value"]
                },
                "ResultB": { "oneOf": [ { "$ref": "#/$defs/ResultB.Success" } ] },
                "ResultB.Success": {
                  "type": "object",
                  "properties": {
                    "type": { "type": "string", "const": "ResultB.Success" },
                    "data": { "type": "integer" }
                  },
                  "required": ["type", "data"]
                }
              }
            }
            """.trimIndent()
    }

    @Test
    fun `Strict should keep FQN names`() {
        val schema = schemaOf(JsonSchemaConfig.Strict)

        schema shouldEqualJson
            // language=json
            $$"""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "$id": "$$pkg.Payload",
              "type": "object",
              "properties": {
                "textValue": { "$ref": "#/$defs/Shared" },
                "a": { "$ref": "#/$defs/$$pkg.ResultA" },
                "b": { "$ref": "#/$defs/$$pkg.ResultB" }
              },
              "required": ["textValue", "a", "b"],
              "additionalProperties": false,
              "$defs": {
                "Shared": {
                  "type": "object",
                  "properties": { "text": { "type": "string" } },
                  "required": ["text"],
                  "additionalProperties": false
                },
                "$$pkg.ResultA": { "oneOf": [ { "$ref": "#/$defs/$$pkg.ResultA.Success" } ] },
                "$$pkg.ResultA.Success": {
                  "type": "object",
                  "properties": {
                    "type": { "type": "string", "const": "$$pkg.ResultA.Success" },
                    "value": { "type": "string" }
                  },
                  "required": ["type", "value"],
                  "additionalProperties": false
                },
                "$$pkg.ResultB": { "oneOf": [ { "$ref": "#/$defs/$$pkg.ResultB.Success" } ] },
                "$$pkg.ResultB.Success": {
                  "type": "object",
                  "properties": {
                    "type": { "type": "string", "const": "$$pkg.ResultB.Success" },
                    "data": { "type": "integer" }
                  },
                  "required": ["type", "data"],
                  "additionalProperties": false
                }
              }
            }
            """.trimIndent()
    }
}
