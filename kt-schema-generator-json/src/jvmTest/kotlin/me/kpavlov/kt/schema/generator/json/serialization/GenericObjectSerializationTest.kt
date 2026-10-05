package me.kpavlov.kt.schema.generator.json.serialization

import io.kotest.assertions.json.shouldEqualJson
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import me.kpavlov.kt.schema.generator.json.serialization.SerializationClassJsonSchemaGenerator.Companion.jsonSchemaOf
import me.kpavlov.kt.schema.json.encodeToString
import kotlin.test.Test

class GenericObjectSerializationTest {
    //region Test models

    @Serializable
    data class Box<T>(
        val value: T,
    )

    @Serializable
    data class Payload(
        val text: Box<String>,
        val count: Box<Int>,
        val nullableText: Box<String?>,
        val ids: Box<List<Int>>,
    )

    @Serializable
    data class ReversedPayload(
        val ids: Box<List<Int>>,
        val nullableText: Box<String?>,
        val count: Box<Int>,
        val text: Box<String>,
    )

    @Serializable
    data class SingleUse(
        val first: Box<String>,
        val second: Box<String>,
    )

    @Serializable
    data class Node<T>(
        val value: T,
        val children: List<Node<T>>,
    )

    @Serializable
    data class TwoTrees(
        val strings: Node<String>,
        val ints: Node<Int>,
    )

    @Serializable
    data class Nested(
        val strings: Box<Box<String>>,
        val ints: Box<Box<Int>>,
    )

    @Serializable
    data class Skew<T>(
        val value: T,
        val next: Skew<List<T>>? = null,
    )

    @Serializable
    data class SkewRoot(
        val skew: Skew<Int>,
    )

    @Serializable
    @JvmInline
    value class Index<T>(
        val byKey: Map<T, Index<T>>,
    )

    @Serializable
    data class Indexes(
        val ints: Index<Int>,
        val strings: Index<String>,
    )

    //endregion

    private val pkg = "me.kpavlov.kt.schema.generator.json.serialization.GenericObjectSerializationTest"

    private inline fun <reified T> schemaOf(): String = jsonSchemaOf<T>().encodeToString()

    private fun String.definitions() = Json.parseToJsonElement(this).jsonObject.getValue($$"$defs").jsonObject

    @Test
    fun `should keep a definition per applied argument`() {
        schemaOf<Payload>() shouldEqualJson
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

    @Test
    fun `should not depend on property discovery order`() {
        schemaOf<ReversedPayload>().definitions() shouldBe schemaOf<Payload>().definitions()
    }

    @Test
    fun `should keep the plain name when a class has a single application`() {
        schemaOf<SingleUse>().definitions().keys shouldContainExactlyInAnyOrder listOf("$pkg.Box")
    }

    @Test
    fun `should resolve each application of a recursive generic class against itself`() {
        schemaOf<TwoTrees>() shouldEqualJson
            // language=json
            $$"""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "$id": "$$pkg.TwoTrees",
              "type": "object",
              "properties": {
                "strings": { "$ref": "#/$defs/$$pkg.Node_of_String" },
                "ints": { "$ref": "#/$defs/$$pkg.Node_of_Int" }
              },
              "required": ["strings", "ints"],
              "additionalProperties": false,
              "$defs": {
                "$$pkg.Node_of_String": {
                  "type": "object",
                  "properties": {
                    "value": { "type": "string" },
                    "children": { "type": "array", "items": { "$ref": "#/$defs/$$pkg.Node_of_String" } }
                  },
                  "required": ["value", "children"],
                  "additionalProperties": false
                },
                "$$pkg.Node_of_Int": {
                  "type": "object",
                  "properties": {
                    "value": { "type": "integer" },
                    "children": { "type": "array", "items": { "$ref": "#/$defs/$$pkg.Node_of_Int" } }
                  },
                  "required": ["value", "children"],
                  "additionalProperties": false
                }
              }
            }
            """.trimIndent()
    }

    @Test
    fun `should tell nested applications of the same generic class apart`() {
        schemaOf<Nested>().definitions().keys shouldContainExactlyInAnyOrder
            listOf(
                "$pkg.Box_of_String",
                "$pkg.Box_of_Int",
                "$pkg.Box_of_Box_of_String",
                "$pkg.Box_of_Box_of_Int",
            )
    }

    @Test
    fun `should stop at a bounded nesting of a polymorphically recursive class`() {
        schemaOf<SkewRoot>().definitions().keys.count { it.startsWith("$pkg.Skew") } shouldBe 8
    }

    @Test
    fun `should keep wrapped definitions of recursive generic value classes apart`() {
        val properties =
            Json
                .parseToJsonElement(schemaOf<Indexes>())
                .jsonObject
                .getValue("properties")
                .jsonObject

        properties.getValue("ints") shouldNotBe properties.getValue("strings")
    }
}
