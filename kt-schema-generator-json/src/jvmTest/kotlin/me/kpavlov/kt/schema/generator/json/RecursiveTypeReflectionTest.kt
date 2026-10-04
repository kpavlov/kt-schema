package me.kpavlov.kt.schema.generator.json

import io.kotest.assertions.json.shouldEqualJson
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
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

    object RecursiveFunction {
        @Suppress("unused")
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
    fun `should register fallback definition for self-wrapping inline value class`() {
        val schema = generator.generateSchemaString(WithRecursiveWrapper::class)

        schema shouldEqualJson
            // language=JSON
            $$"""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "$id": "me.kpavlov.kt.schema.generator.json.RecursiveTypeReflectionTest.WithRecursiveWrapper",
              "type": "object",
              "properties": {
                "wrapper": {
                  "type": "array",
                  "items": { "$ref": "#/$defs/me.kpavlov.kt.schema.generator.json.RecursiveTypeReflectionTest.RecursiveWrapper" }
                }
              },
              "required": ["wrapper"],
              "additionalProperties": false,
              "$defs": {
                "me.kpavlov.kt.schema.generator.json.RecursiveTypeReflectionTest.RecursiveWrapper": {
                  "type": "object",
                  "properties": {
                    "items": {
                      "type": "array",
                      "items": { "$ref": "#/$defs/me.kpavlov.kt.schema.generator.json.RecursiveTypeReflectionTest.RecursiveWrapper" }
                    }
                  },
                  "required": ["items"],
                  "additionalProperties": false
                }
              }
            }
            """.trimIndent()
    }

    @Test
    fun `should stop flattening at nullable self-reference of inline value class`() {
        val schema = generator.generateSchemaString(WithNullableRecursiveWrapper::class)

        schema shouldEqualJson
            // language=JSON
            $$"""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "$id": "me.kpavlov.kt.schema.generator.json.RecursiveTypeReflectionTest.WithNullableRecursiveWrapper",
              "type": "object",
              "properties": {
                "wrapper": {
                  "type": "array",
                  "items": {
                    "oneOf": [
                      { "type": "null" },
                      { "$ref": "#/$defs/me.kpavlov.kt.schema.generator.json.RecursiveTypeReflectionTest.NullableRecursiveWrapper" }
                    ]
                  }
                }
              },
              "required": ["wrapper"],
              "additionalProperties": false,
              "$defs": {
                "me.kpavlov.kt.schema.generator.json.RecursiveTypeReflectionTest.NullableRecursiveWrapper": {
                  "type": "object",
                  "properties": {
                    "items": {
                      "type": "array",
                      "items": {
                        "oneOf": [
                          { "type": "null" },
                          { "$ref": "#/$defs/me.kpavlov.kt.schema.generator.json.RecursiveTypeReflectionTest.NullableRecursiveWrapper" }
                        ]
                      }
                    }
                  },
                  "required": ["items"],
                  "additionalProperties": false
                }
              }
            }
            """.trimIndent()
    }

    @Test
    fun `should register fallback definitions for mutually recursive inline value classes`() {
        val schema = Json.parseToJsonElement(generator.generateSchemaString(WithMutuallyRecursiveWrappers::class))

        schema.jsonObject.getValue($$"$defs").jsonObject.keys shouldBe
            setOf(
                "me.kpavlov.kt.schema.generator.json.RecursiveTypeReflectionTest.Ping",
                "me.kpavlov.kt.schema.generator.json.RecursiveTypeReflectionTest.Pong",
            )
    }
}
