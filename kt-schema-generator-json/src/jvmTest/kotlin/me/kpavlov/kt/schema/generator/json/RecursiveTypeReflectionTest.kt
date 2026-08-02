package me.kpavlov.kt.schema.generator.json

import io.kotest.assertions.json.shouldEqualJson
import io.kotest.matchers.string.shouldContain
import kotlinx.serialization.json.Json
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
              "$id": "Tree",
              "type": "object",
              "properties": {
                "root": {
                  "$ref": "#/$defs/TreeNode"
                }
              },
              "required": ["root"],
              "additionalProperties": false,
              "$defs": {
                "TreeNode": {
                  "oneOf": [
                    { "$ref": "#/$defs/Branch" },
                    { "$ref": "#/$defs/Leaf" }
                  ]
                },
                "Branch": {
                  "type": "object",
                  "properties": {
                    "type": {
                      "type": "string",
                      "const": "Branch"
                    },
                    "id": { "type": "string" },
                    "left": {
                      "oneOf": [
                        { "type": "null" },
                        { "$ref": "#/$defs/TreeNode" }
                      ]
                    },
                    "right": {
                      "$ref": "#/$defs/TreeNode"
                    }
                  },
                  "required": ["type", "id", "left", "right"],
                  "additionalProperties": false
                },
                "Leaf": {
                  "type": "object",
                  "properties": {
                    "type": {
                      "type": "string",
                      "const": "Leaf"
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
              "$id": "LinkedNode",
              "type": "object",
              "properties": {
                "value": { "type": "string" },
                "next": {
                  "oneOf": [
                    { "type": "null" },
                    { "$ref": "#/$defs/LinkedNode" }
                  ]
                }
              },
                  "required": ["value", "next"],
                  "additionalProperties": false,
                  "$defs": {
                    "LinkedNode": {
                      "type": "object",
                      "properties": {
                        "value": { "type": "string" },
                        "next": {
                          "oneOf": [
                            { "type": "null" },
                            { "$ref": "#/$defs/LinkedNode" }
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
}
