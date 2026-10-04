package me.kpavlov.kt.schema.generator.json

import io.kotest.assertions.json.shouldEqualJson
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.InstantComponentSerializer
import kotlinx.serialization.json.Json
import me.kpavlov.kt.schema.generator.json.serialization.SerializationClassJsonSchemaGenerator
import kotlin.test.Test
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class InstantSchemaTest {
    @Serializable
    data class Event(
        val at: Instant,
        val maybeAt: Instant? = null,
    )

    @Serializable
    data class ComponentEvent(
        @Serializable(with = InstantComponentSerializer::class) val at: Instant,
    )

    @Test
    fun `serialization introspector should map kotlin time Instant to string`() {
        val schema = SerializationClassJsonSchemaGenerator.Default.generateSchemaString(Event.serializer().descriptor)

        schema shouldEqualJson
            // language=JSON
            $$"""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "$id": "me.kpavlov.kt.schema.generator.json.InstantSchemaTest.Event",
              "type": "object",
              "properties": {
                "at": { "type": "string" },
                "maybeAt": { "type": ["string", "null"] }
              },
              "additionalProperties": false,
              "required": ["at"]
            }
            """.trimIndent()
    }

    @Test
    fun `serialization introspector should map InstantComponentSerializer to object`() {
        val schema =
            SerializationClassJsonSchemaGenerator.Default.generateSchemaString(ComponentEvent.serializer().descriptor)

        schema shouldEqualJson
            // language=JSON
            $$"""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "$id": "me.kpavlov.kt.schema.generator.json.InstantSchemaTest.ComponentEvent",
              "type": "object",
              "properties": {
                "at": { "$ref": "#/$defs/kotlinx.serialization.InstantComponentSerializer" }
              },
              "additionalProperties": false,
              "required": ["at"],
              "$defs": {
                "kotlinx.serialization.InstantComponentSerializer": {
                  "type": "object",
                  "properties": {
                    "epochSeconds": { "type": "integer" },
                    "nanosecondsOfSecond": { "type": "integer" }
                  },
                  "required": ["epochSeconds"],
                  "additionalProperties": false
                }
              }
            }
            """.trimIndent()
    }

    @Test
    fun `reflection introspector should map kotlin time Instant to string`() {
        val generator =
            ReflectionClassJsonSchemaGenerator(
                json = Json { encodeDefaults = false },
                config = JsonSchemaConfig.Default,
            )

        val schema = generator.generateSchemaString(Event::class)

        schema shouldEqualJson
            // language=JSON
            $$"""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "$id": "me.kpavlov.kt.schema.generator.json.InstantSchemaTest.Event",
              "type": "object",
              "properties": {
                "at": { "type": "string" },
                "maybeAt": { "type": ["string", "null"] }
              },
              "additionalProperties": false,
              "required": ["at"]
            }
            """.trimIndent()
    }
}
