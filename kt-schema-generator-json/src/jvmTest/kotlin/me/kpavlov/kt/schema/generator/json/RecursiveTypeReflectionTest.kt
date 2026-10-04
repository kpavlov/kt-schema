package me.kpavlov.kt.schema.generator.json

import io.kotest.assertions.json.shouldEqualJson
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import me.kpavlov.kt.schema.Description
import kotlin.test.Test
import kotlin.test.assertFailsWith

class RecursiveTypeReflectionTest {
    //region Test models

    @Suppress("unused")
    sealed interface TreeNode {
        val id: String

        data class Leaf(
            override val id: String,
            val value: String,
        ) : TreeNode

        data class Branch(
            override val id: String,
            val left: TreeNode?,
            val right: TreeNode,
        ) : TreeNode
    }

    data class Tree(
        val root: TreeNode,
    )

    data class LinkedNode(
        val value: String,
        val next: LinkedNode?,
    )

    @JvmInline
    value class RecursiveWrapper(
        val items: List<RecursiveWrapper>,
    )

    data class WithRecursiveWrapper(
        val wrapper: RecursiveWrapper,
    )

    @JvmInline
    value class NullableRecursiveWrapper(
        val items: List<NullableRecursiveWrapper?>,
    )

    data class WithNullableRecursiveWrapper(
        val wrapper: NullableRecursiveWrapper,
    )

    @JvmInline
    value class OptionalRecursiveWrapper(
        val items: List<OptionalRecursiveWrapper>?,
    )

    data class WithOptionalRecursiveWrapper(
        val wrapper: OptionalRecursiveWrapper,
    )

    @Description("Nested items")
    @JvmInline
    value class DescribedRecursiveWrapper(
        val items: List<DescribedRecursiveWrapper>,
    )

    data class WithDescribedRecursiveWrapper(
        val wrapper: DescribedRecursiveWrapper,
    )

    @JvmInline
    value class Ping(
        val pongs: List<Pong>,
    )

    @JvmInline
    value class Pong(
        val pings: List<Ping>,
    )

    data class WithMutuallyRecursiveWrappers(
        val ping: Ping,
    )

    @JvmInline
    value class ChainHolder(
        val chain: Chain,
    )

    data class Chain(
        val value: String,
        val next: ChainHolder?,
    )

    data class WithValueClassCycleThroughObject(
        val holder: ChainHolder,
    )

    @JvmInline
    value class Nest<T>(
        val items: List<Nest<List<T>>>,
    )

    data class WithPolymorphicallyRecursiveWrapper(
        val nest: Nest<Int>,
    )

    object RecursiveFunction {
        fun process(node: LinkedNode): String = node.value
    }

    //endregion

    private val generator =
        ReflectionClassJsonSchemaGenerator(
            json = Json { prettyPrint = true },
            config = JsonSchemaConfig.Default,
        )

    @Test
    fun `should generate schema for recursive sealed hierarchy`() {
        val schema = generator.generateSchemaString(Tree::class)

        schema shouldEqualJson
            // language=JSON
            $$"""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "$id": "me.kpavlov.kt.schema.generator.json.RecursiveTypeReflectionTest.Tree",
              "type": "object",
              "properties": {
                "root": {
                  "$ref": "#/$defs/me.kpavlov.kt.schema.generator.json.RecursiveTypeReflectionTest.TreeNode"
                }
              },
              "required": ["root"],
              "additionalProperties": false,
              "$defs": {
                "me.kpavlov.kt.schema.generator.json.RecursiveTypeReflectionTest.TreeNode": {
                  "oneOf": [
                    { "$ref": "#/$defs/me.kpavlov.kt.schema.generator.json.RecursiveTypeReflectionTest.TreeNode.Branch" },
                    { "$ref": "#/$defs/me.kpavlov.kt.schema.generator.json.RecursiveTypeReflectionTest.TreeNode.Leaf" }
                  ]
                },
                "me.kpavlov.kt.schema.generator.json.RecursiveTypeReflectionTest.TreeNode.Branch": {
                  "type": "object",
                  "properties": {
                    "type": {
                      "type": "string",
                      "const": "me.kpavlov.kt.schema.generator.json.RecursiveTypeReflectionTest.TreeNode.Branch"
                    },
                    "id": { "type": "string" },
                    "left": {
                      "oneOf": [
                        { "type": "null" },
                        { "$ref": "#/$defs/me.kpavlov.kt.schema.generator.json.RecursiveTypeReflectionTest.TreeNode" }
                      ]
                    },
                    "right": {
                      "$ref": "#/$defs/me.kpavlov.kt.schema.generator.json.RecursiveTypeReflectionTest.TreeNode"
                    }
                  },
                  "required": ["type", "id", "left", "right"],
                  "additionalProperties": false
                },
                "me.kpavlov.kt.schema.generator.json.RecursiveTypeReflectionTest.TreeNode.Leaf": {
                  "type": "object",
                  "properties": {
                    "type": {
                      "type": "string",
                      "const": "me.kpavlov.kt.schema.generator.json.RecursiveTypeReflectionTest.TreeNode.Leaf"
                    },
                    "id": { "type": "string" },
                    "value": { "type": "string" }
                  },
                  "required": ["type", "id", "value"],
                  "additionalProperties": false
                }
              }
            }
            """.trimIndent()
    }

    @Test
    fun `should generate schema for self-referencing type`() {
        val schema = generator.generateSchemaString(LinkedNode::class)

        schema shouldEqualJson
            // language=JSON
            $$"""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "$id": "me.kpavlov.kt.schema.generator.json.RecursiveTypeReflectionTest.LinkedNode",
              "type": "object",
              "properties": {
                "value": { "type": "string" },
                "next": {
                  "oneOf": [
                    { "type": "null" },
                    { "$ref": "#/$defs/me.kpavlov.kt.schema.generator.json.RecursiveTypeReflectionTest.LinkedNode" }
                  ]
                }
              },
                  "required": ["value", "next"],
                  "additionalProperties": false,
                  "$defs": {
                    "me.kpavlov.kt.schema.generator.json.RecursiveTypeReflectionTest.LinkedNode": {
                      "type": "object",
                      "properties": {
                        "value": { "type": "string" },
                        "next": {
                          "oneOf": [
                            { "type": "null" },
                            { "$ref": "#/$defs/me.kpavlov.kt.schema.generator.json.RecursiveTypeReflectionTest.LinkedNode" }
                          ]
                        }
                      },
                      "required": ["value", "next"],
                      "additionalProperties": false
                    }
                  }
                }
            """.trimIndent()
    }

    @Test
    fun `should throw instead of stack overflowing for self-referencing type in function-calling schema`() {
        val functionGenerator = ReflectionFunctionCallingSchemaGenerator.Default

        val exception =
            assertFailsWith<IllegalArgumentException> {
                functionGenerator.generateSchema(RecursiveFunction::process)
            }

        exception.message shouldContain "Type nesting exceeds 8 levels"
        exception.message shouldContain "cannot be represented in a function-calling schema"
    }

    @Test
    fun `should define self-wrapping inline value class by its wrapped array shape`() {
        val schema = generator.generateSchemaString(WithRecursiveWrapper::class)

        schema shouldEqualJson
            // language=JSON
            $$"""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "$id": "me.kpavlov.kt.schema.generator.json.RecursiveTypeReflectionTest.WithRecursiveWrapper",
              "type": "object",
              "properties": {
                "wrapper": { "$ref": "#/$defs/me.kpavlov.kt.schema.generator.json.RecursiveTypeReflectionTest.RecursiveWrapper" }
              },
              "required": ["wrapper"],
              "additionalProperties": false,
              "$defs": {
                "me.kpavlov.kt.schema.generator.json.RecursiveTypeReflectionTest.RecursiveWrapper": {
                  "type": "array",
                  "items": { "$ref": "#/$defs/me.kpavlov.kt.schema.generator.json.RecursiveTypeReflectionTest.RecursiveWrapper" }
                }
              }
            }
            """.trimIndent()
    }

    @Test
    fun `should define inline value class wrapping a list of nullable selves by its array shape`() {
        val schema = generator.generateSchemaString(WithNullableRecursiveWrapper::class)

        schema shouldEqualJson
            // language=JSON
            $$"""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "$id": "me.kpavlov.kt.schema.generator.json.RecursiveTypeReflectionTest.WithNullableRecursiveWrapper",
              "type": "object",
              "properties": {
                "wrapper": { "$ref": "#/$defs/me.kpavlov.kt.schema.generator.json.RecursiveTypeReflectionTest.NullableRecursiveWrapper" }
              },
              "required": ["wrapper"],
              "additionalProperties": false,
              "$defs": {
                "me.kpavlov.kt.schema.generator.json.RecursiveTypeReflectionTest.NullableRecursiveWrapper": {
                  "type": "array",
                  "items": {
                    "oneOf": [
                      { "type": "null" },
                      { "$ref": "#/$defs/me.kpavlov.kt.schema.generator.json.RecursiveTypeReflectionTest.NullableRecursiveWrapper" }
                    ]
                  }
                }
              }
            }
            """.trimIndent()
    }

    @Test
    fun `should keep nullability of the wrapped type on recursive inline value class references`() {
        val schema = generator.generateSchemaString(WithOptionalRecursiveWrapper::class)

        schema shouldEqualJson
            // language=JSON
            $$"""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "$id": "me.kpavlov.kt.schema.generator.json.RecursiveTypeReflectionTest.WithOptionalRecursiveWrapper",
              "type": "object",
              "properties": {
                "wrapper": {
                  "oneOf": [
                    { "type": "null" },
                    { "$ref": "#/$defs/me.kpavlov.kt.schema.generator.json.RecursiveTypeReflectionTest.OptionalRecursiveWrapper" }
                  ]
                }
              },
              "required": ["wrapper"],
              "additionalProperties": false,
              "$defs": {
                "me.kpavlov.kt.schema.generator.json.RecursiveTypeReflectionTest.OptionalRecursiveWrapper": {
                  "type": "array",
                  "items": {
                    "oneOf": [
                      { "type": "null" },
                      { "$ref": "#/$defs/me.kpavlov.kt.schema.generator.json.RecursiveTypeReflectionTest.OptionalRecursiveWrapper" }
                    ]
                  }
                }
              }
            }
            """.trimIndent()
    }

    @Test
    fun `should carry class description onto recursive inline value class definition`() {
        val schema = Json.parseToJsonElement(generator.generateSchemaString(WithDescribedRecursiveWrapper::class))

        schema.jsonObject.getValue($$"$defs").toString() shouldEqualJson
            // language=JSON
            $$"""
            {
              "me.kpavlov.kt.schema.generator.json.RecursiveTypeReflectionTest.DescribedRecursiveWrapper": {
                "type": "array",
                "description": "Nested items",
                "items": { "$ref": "#/$defs/me.kpavlov.kt.schema.generator.json.RecursiveTypeReflectionTest.DescribedRecursiveWrapper" }
              }
            }
            """.trimIndent()
    }

    @Test
    fun `should define only the re-entered class of mutually recursive inline value classes`() {
        val schema = Json.parseToJsonElement(generator.generateSchemaString(WithMutuallyRecursiveWrappers::class))

        schema.jsonObject
            .getValue($$"$defs")
            .jsonObject.keys shouldBe
            setOf("me.kpavlov.kt.schema.generator.json.RecursiveTypeReflectionTest.Ping")
    }

    @Test
    fun `should flatten inline value class whose cycle goes through an object`() {
        val schema = generator.generateSchemaString(WithValueClassCycleThroughObject::class)

        schema shouldEqualJson
            // language=JSON
            $$"""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "$id": "me.kpavlov.kt.schema.generator.json.RecursiveTypeReflectionTest.WithValueClassCycleThroughObject",
              "type": "object",
              "properties": {
                "holder": { "$ref": "#/$defs/me.kpavlov.kt.schema.generator.json.RecursiveTypeReflectionTest.Chain" }
              },
              "required": ["holder"],
              "additionalProperties": false,
              "$defs": {
                "me.kpavlov.kt.schema.generator.json.RecursiveTypeReflectionTest.Chain": {
                  "type": "object",
                  "properties": {
                    "value": { "type": "string" },
                    "next": {
                      "oneOf": [
                        { "type": "null" },
                        { "$ref": "#/$defs/me.kpavlov.kt.schema.generator.json.RecursiveTypeReflectionTest.Chain" }
                      ]
                    }
                  },
                  "required": ["value", "next"],
                  "additionalProperties": false
                }
              }
            }
            """.trimIndent()
    }

    @Test
    fun `should cut polymorphically recursive inline value class off as any value`() {
        val schemaString = generator.generateSchemaString(WithPolymorphicallyRecursiveWrapper::class)
        val schema = Json.parseToJsonElement(schemaString)

        var nest =
            schema.jsonObject
                .getValue("properties")
                .jsonObject
                .getValue("nest")
        repeat(7) { nest = nest.jsonObject.getValue("items") }
        // The 8th level's items are "any value" ({}), which is omitted.
        nest.toString() shouldEqualJson """{"type": "array"}"""
    }
}
