package me.kpavlov.kt.schema.generator.json.serialization

import io.kotest.assertions.json.shouldEqualJson
import kotlinx.serialization.Serializable
import me.kpavlov.kt.schema.generator.json.JsonSchemaConfig
import me.kpavlov.kt.schema.generator.json.serialization.SerializationClassJsonSchemaGenerator.Companion.jsonSchemaOf
import me.kpavlov.kt.schema.json.encodeToString
import kotlin.test.Test

class LenientSerializationNamesTest {
    @Serializable
    data class Inner(
        val value: String,
    )

    @Serializable
    data class Outer(
        val inner: Inner,
    )

    @Test
    fun `Lenient should keep full serial names`() {
        val generator = SerializationClassJsonSchemaGenerator(jsonSchemaConfig = JsonSchemaConfig.Lenient)
        val pkg = "me.kpavlov.kt.schema.generator.json.serialization.LenientSerializationNamesTest"

        jsonSchemaOf<Outer>(generator).encodeToString() shouldEqualJson
            // language=json
            $$"""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "$id": "$$pkg.Outer",
              "type": "object",
              "properties": { "inner": { "$ref": "#/$defs/$$pkg.Inner" } },
              "required": ["inner"],
              "$defs": {
                "$$pkg.Inner": {
                  "type": "object",
                  "properties": { "value": { "type": "string" } },
                  "required": ["value"]
                }
              }
            }
            """.trimIndent()
    }
}
