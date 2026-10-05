@file:Suppress("unused")

package me.kpavlov.kt.schema.generator.json

import io.kotest.assertions.json.shouldEqualJson
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.string.shouldContain
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlin.test.Test

@OptIn(ExperimentalSerializationApi::class)
class CustomDiscriminatorSchemaTest {
    //region Fixture

    @JsonClassDiscriminator("outcome")
    sealed interface Response {
        sealed interface Failure : Response {
            data class Timeout(
                val seconds: Int,
            ) : Failure
        }

        data class Ok(
            val value: String,
        ) : Response
    }

    @JsonClassDiscriminator("kind")
    sealed interface Shape {
        data class Square(
            val kind: String,
        ) : Shape
    }

    object Shapes {
        fun draw(shape: Shape) = shape
    }

    //endregion

    private val functionCallingGenerator =
        ReflectionFunctionCallingSchemaGenerator(
            json = Json { encodeDefaults = false },
            config = FunctionCallingSchemaConfig(includePolymorphicDiscriminator = true),
        )

    private val generator =
        ReflectionClassJsonSchemaGenerator(
            json = Json { encodeDefaults = false },
            config = JsonSchemaConfig.Strict,
        )

    @Test
    fun `discriminator property named by @JsonClassDiscriminator is emitted on every leaf subtype`() {
        val schema = generator.generateSchemaString(Response::class)

        // language=JSON
        schema shouldEqualJson
            $$"""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "$id": "me.kpavlov.kt.schema.generator.json.CustomDiscriminatorSchemaTest.Response",
              "type": "object",
              "oneOf": [
                { "$ref": "#/$defs/me.kpavlov.kt.schema.generator.json.CustomDiscriminatorSchemaTest.Response.Failure" },
                { "$ref": "#/$defs/me.kpavlov.kt.schema.generator.json.CustomDiscriminatorSchemaTest.Response.Ok" }
              ],
              "$defs": {
                "me.kpavlov.kt.schema.generator.json.CustomDiscriminatorSchemaTest.Response.Ok": {
                  "type": "object",
                  "properties": {
                    "outcome": {
                      "type": "string",
                      "const": "me.kpavlov.kt.schema.generator.json.CustomDiscriminatorSchemaTest.Response.Ok"
                    },
                    "value": { "type": "string" }
                  },
                  "required": ["outcome", "value"],
                  "additionalProperties": false
                },
                "me.kpavlov.kt.schema.generator.json.CustomDiscriminatorSchemaTest.Response.Failure.Timeout": {
                  "type": "object",
                  "properties": {
                    "outcome": {
                      "type": "string",
                      "const": "me.kpavlov.kt.schema.generator.json.CustomDiscriminatorSchemaTest.Response.Failure.Timeout"
                    },
                    "seconds": { "type": "integer" }
                  },
                  "required": ["outcome", "seconds"],
                  "additionalProperties": false
                },
                "me.kpavlov.kt.schema.generator.json.CustomDiscriminatorSchemaTest.Response.Failure": {
                  "oneOf": [
                    { "$ref": "#/$defs/me.kpavlov.kt.schema.generator.json.CustomDiscriminatorSchemaTest.Response.Failure.Timeout" }
                  ]
                }
              }
            }
            """.trimIndent()
    }

    @Test
    fun `fails fast when a subtype declares a property named like the discriminator`() {
        val error =
            shouldThrow<IllegalStateException> {
                generator.generateSchemaString(Shape::class)
            }

        error.message shouldContain "'kind'"
        error.message shouldContain "Square"
    }

    @Test
    fun `function calling schema fails fast when a subtype declares a property named like the discriminator`() {
        val error =
            shouldThrow<IllegalStateException> {
                functionCallingGenerator.generateSchemaString(Shapes::draw)
            }

        error.message shouldContain "'kind'"
        error.message shouldContain "Square"
    }
}
