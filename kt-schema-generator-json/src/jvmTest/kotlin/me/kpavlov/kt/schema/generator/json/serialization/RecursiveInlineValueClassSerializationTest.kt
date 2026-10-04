package me.kpavlov.kt.schema.generator.json.serialization

import io.kotest.assertions.json.shouldEqualJson
import io.kotest.matchers.shouldBe
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import me.kpavlov.kt.schema.generator.json.SerialDescription
import me.kpavlov.kt.schema.generator.json.serialization.SerializationClassJsonSchemaGenerator.Companion.jsonSchemaOf
import me.kpavlov.kt.schema.json.encodeToString
import kotlin.test.Test

class RecursiveInlineValueClassSerializationTest {
    //region Test models

    @Serializable
    @JvmInline
    value class Tree(
        val children: List<Tree>,
    )

    @Serializable
    data class WithTree(
        val tree: Tree,
    )

    @Serializable
    @JvmInline
    value class NullableTree(
        val children: List<NullableTree?>,
    )

    @Serializable
    data class WithNullableTree(
        val tree: NullableTree,
    )

    @Serializable
    @JvmInline
    value class OptionalTree(
        val children: List<OptionalTree>?,
    )

    @Serializable
    data class WithOptionalTree(
        val tree: OptionalTree,
    )

    @SerialDescription("Nested children")
    @Serializable
    @JvmInline
    value class DescribedTree(
        val children: List<DescribedTree>,
    )

    @Serializable
    data class WithDescribedTree(
        val tree: DescribedTree,
    )

    @Serializable
    @JvmInline
    value class ChainHolder(
        val chain: Chain,
    )

    @Serializable
    data class Chain(
        val value: String,
        val next: ChainHolder?,
    )

    @Serializable
    data class WithValueClassCycleThroughObject(
        val holder: ChainHolder,
    )

    @Serializable
    @JvmInline
    value class Wrapper<T>(
        val value: T,
    )

    @Serializable
    data class WithNestedGenericValueClass(
        val nested: Wrapper<Wrapper<Int>>,
    )

    @Serializable
    @JvmInline
    value class Nest<T>(
        val items: List<Nest<List<T>>>,
    )

    @Serializable
    data class WithNest(
        val nest: Nest<Int>,
    )

    //endregion

    private fun schemaOf(json: String) = Json.parseToJsonElement(json).jsonObject

    @Test
    fun `should define self-wrapping inline value class by its wrapped array shape`() {
        val schema = jsonSchemaOf<WithTree>().encodeToString()

        schemaOf(schema).getValue($$"$defs").toString() shouldEqualJson
            // language=JSON
            $$"""
            {
              "me.kpavlov.kt.schema.generator.json.serialization.RecursiveInlineValueClassSerializationTest.Tree": {
                "type": "array",
                "items": { "$ref": "#/$defs/me.kpavlov.kt.schema.generator.json.serialization.RecursiveInlineValueClassSerializationTest.Tree" }
              }
            }
            """.trimIndent()
    }

    @Test
    fun `should define inline value class wrapping a list of nullable selves by its array shape`() {
        val schema = jsonSchemaOf<WithNullableTree>().encodeToString()

        schemaOf(schema).getValue($$"$defs").toString() shouldEqualJson
            // language=JSON
            $$"""
            {
              "me.kpavlov.kt.schema.generator.json.serialization.RecursiveInlineValueClassSerializationTest.NullableTree": {
                "type": "array",
                "items": {
                  "oneOf": [
                    { "type": "null" },
                    { "$ref": "#/$defs/me.kpavlov.kt.schema.generator.json.serialization.RecursiveInlineValueClassSerializationTest.NullableTree" }
                  ]
                }
              }
            }
            """.trimIndent()
    }

    @Test
    fun `should keep nullability of the wrapped type on recursive inline value class references`() {
        val schema = schemaOf(jsonSchemaOf<WithOptionalTree>().encodeToString())

        val optionalTreeRef =
            // language=JSON
            $$"""
            {
              "oneOf": [
                { "type": "null" },
                { "$ref": "#/$defs/me.kpavlov.kt.schema.generator.json.serialization.RecursiveInlineValueClassSerializationTest.OptionalTree" }
              ]
            }
            """.trimIndent()
        schema.getValue("properties").jsonObject.getValue("tree").toString() shouldEqualJson optionalTreeRef
        schema.getValue($$"$defs").toString() shouldEqualJson
            $$"""
            {
              "me.kpavlov.kt.schema.generator.json.serialization.RecursiveInlineValueClassSerializationTest.OptionalTree": {
                "type": "array",
                "items": $$optionalTreeRef
              }
            }
            """.trimIndent()
    }

    @Test
    fun `should carry class description onto recursive inline value class definition`() {
        val schema = jsonSchemaOf<WithDescribedTree>().encodeToString()

        schemaOf(schema).getValue($$"$defs").toString() shouldEqualJson
            // language=JSON
            $$"""
            {
              "me.kpavlov.kt.schema.generator.json.serialization.RecursiveInlineValueClassSerializationTest.DescribedTree": {
                "type": "array",
                "description": "Nested children",
                "items": { "$ref": "#/$defs/me.kpavlov.kt.schema.generator.json.serialization.RecursiveInlineValueClassSerializationTest.DescribedTree" }
              }
            }
            """.trimIndent()
    }

    @Test
    fun `should flatten inline value class whose cycle goes through an object`() {
        val schema = jsonSchemaOf<WithValueClassCycleThroughObject>().encodeToString()

        schemaOf(schema).getValue($$"$defs").jsonObject.keys shouldBe
            setOf("me.kpavlov.kt.schema.generator.json.serialization.RecursiveInlineValueClassSerializationTest.Chain")
    }

    @Test
    fun `should flatten nested instantiations of the same generic inline value class`() {
        val schema = jsonSchemaOf<WithNestedGenericValueClass>().encodeToString()

        schemaOf(schema).getValue("properties").toString() shouldEqualJson
            // language=JSON
            """
            {
              "nested": { "type": "integer" }
            }
            """.trimIndent()
    }

    @Test
    fun `should cut polymorphically recursive inline value class off as any value`() {
        val schema = jsonSchemaOf<WithNest>().encodeToString()

        var nest = schemaOf(schema).getValue("properties").jsonObject.getValue("nest")
        repeat(7) { nest = nest.jsonObject.getValue("items") }
        // The 8th level's items are "any value" ({}), which is omitted.
        nest.toString() shouldEqualJson """{"type": "array"}"""
    }
}
