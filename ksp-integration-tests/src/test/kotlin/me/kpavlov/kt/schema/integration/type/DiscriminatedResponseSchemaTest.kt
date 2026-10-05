package me.kpavlov.kt.schema.integration.type

import io.kotest.assertions.json.shouldEqualJson
import kotlin.test.Test

class DiscriminatedResponseSchemaTest {
    // language=json
    private val expectedSchema =
        $$"""
        {
          "$schema": "https://json-schema.org/draft/2020-12/schema",
          "$id": "me.kpavlov.kt.schema.integration.type.DiscriminatedResponse",
          "type": "object",
          "oneOf": [
            { "$ref": "#/$defs/me.kpavlov.kt.schema.integration.type.DiscriminatedResponse.Failure" },
            { "$ref": "#/$defs/me.kpavlov.kt.schema.integration.type.DiscriminatedResponse.Ok" }
          ],
          "$defs": {
            "me.kpavlov.kt.schema.integration.type.DiscriminatedResponse.Ok": {
              "type": "object",
              "properties": {
                "outcome": {
                  "type": "string",
                  "const": "me.kpavlov.kt.schema.integration.type.DiscriminatedResponse.Ok"
                },
                "value": { "type": "string" }
              },
              "required": ["outcome", "value"],
              "additionalProperties": false
            },
            "me.kpavlov.kt.schema.integration.type.DiscriminatedResponse.Failure.Timeout": {
              "type": "object",
              "properties": {
                "outcome": {
                  "type": "string",
                  "const": "me.kpavlov.kt.schema.integration.type.DiscriminatedResponse.Failure.Timeout"
                },
                "seconds": { "type": "integer" }
              },
              "required": ["outcome", "seconds"],
              "additionalProperties": false
            },
            "me.kpavlov.kt.schema.integration.type.DiscriminatedResponse.Failure": {
              "oneOf": [
                { "$ref": "#/$defs/me.kpavlov.kt.schema.integration.type.DiscriminatedResponse.Failure.Timeout" }
              ]
            }
          }
        }
        """.trimIndent()

    @Test
    fun `@JsonClassDiscriminator names the discriminator property of every leaf in jsonSchemaString`() {
        DiscriminatedResponse::class.jsonSchemaString shouldEqualJson expectedSchema
    }

    @Test
    fun `@JsonClassDiscriminator names the discriminator property of every leaf in jsonSchema`() {
        DiscriminatedResponse::class.jsonSchema.toString() shouldEqualJson expectedSchema
    }
}
