package me.kpavlov.kt.schema.integration.type

import io.kotest.assertions.json.shouldEqualJson
import kotlin.test.Test

class TreeNodeSchemaTest {
    @Test
    fun `generates schema for tree with recursive sealed hierarchy`() {
        val schema = Tree::class.jsonSchemaString

        // language=json
        schema shouldEqualJson
            $$"""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "$id": "me.kpavlov.kt.schema.integration.type.Tree",
              "description": "A tree with a root node",
              "type": "object",
              "properties": {
                "root": {
                  "description": "The root node",
                  "$ref": "#/$defs/me.kpavlov.kt.schema.integration.type.TreeNode"
                }
              },
              "required": ["root"],
              "additionalProperties": false,
              "$defs": {
                "me.kpavlov.kt.schema.integration.type.TreeNode": {
                  "description": "A node in a tree structure",
                  "oneOf": [
                    { "$ref": "#/$defs/me.kpavlov.kt.schema.integration.type.TreeNode.Branch" },
                    { "$ref": "#/$defs/me.kpavlov.kt.schema.integration.type.TreeNode.Leaf" }
                  ]
                },
                "me.kpavlov.kt.schema.integration.type.TreeNode.Branch": {
                  "type": "object",
                  "description": "A branch node with recursive children",
                  "properties": {
                    "type": {
                      "type": "string",
                      "const": "me.kpavlov.kt.schema.integration.type.TreeNode.Branch"
                    },
                    "id": {
                      "type": "string",
                      "description": "Node identifier"
                    },
                    "left": {
                      "oneOf": [
                        { "type": "null" },
                        { "$ref": "#/$defs/me.kpavlov.kt.schema.integration.type.TreeNode" }
                      ],
                      "description": "Optional left child"
                    },
                    "right": {
                      "$ref": "#/$defs/me.kpavlov.kt.schema.integration.type.TreeNode",
                      "description": "Right child"
                    }
                  },
                  "required": ["type", "id", "left", "right"],
                  "additionalProperties": false
                },
                "me.kpavlov.kt.schema.integration.type.TreeNode.Leaf": {
                  "type": "object",
                  "description": "A leaf node containing a value",
                  "properties": {
                    "type": {
                      "type": "string",
                      "const": "me.kpavlov.kt.schema.integration.type.TreeNode.Leaf"
                    },
                    "id": {
                      "type": "string",
                      "description": "Node identifier"
                    },
                    "value": {
                      "type": "string",
                      "description": "Leaf value"
                    }
                  },
                  "required": ["type", "id", "value"],
                  "additionalProperties": false
                }
              }
            }
            """.trimIndent()
    }

    @Test
    fun `generates polymorphic schema for recursive sealed root`() {
        val schema = TreeNode::class.jsonSchemaString

        // language=json
        schema shouldEqualJson
            $$"""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "$id": "me.kpavlov.kt.schema.integration.type.TreeNode",
              "description": "A node in a tree structure",
              "type": "object",
              "additionalProperties": false,
              "oneOf": [
                { "$ref": "#/$defs/me.kpavlov.kt.schema.integration.type.TreeNode.Branch" },
                { "$ref": "#/$defs/me.kpavlov.kt.schema.integration.type.TreeNode.Leaf" }
              ],
              "$defs": {
                "me.kpavlov.kt.schema.integration.type.TreeNode": {
                  "description": "A node in a tree structure",
                  "oneOf": [
                    { "$ref": "#/$defs/me.kpavlov.kt.schema.integration.type.TreeNode.Branch" },
                    { "$ref": "#/$defs/me.kpavlov.kt.schema.integration.type.TreeNode.Leaf" }
                  ]
                },
                "me.kpavlov.kt.schema.integration.type.TreeNode.Branch": {
                  "type": "object",
                  "description": "A branch node with recursive children",
                  "properties": {
                    "type": {
                      "type": "string",
                      "const": "me.kpavlov.kt.schema.integration.type.TreeNode.Branch"
                    },
                    "id": {
                      "type": "string",
                      "description": "Node identifier"
                    },
                    "left": {
                      "oneOf": [
                        { "type": "null" },
                        { "$ref": "#/$defs/me.kpavlov.kt.schema.integration.type.TreeNode" }
                      ],
                      "description": "Optional left child"
                    },
                    "right": {
                      "$ref": "#/$defs/me.kpavlov.kt.schema.integration.type.TreeNode",
                      "description": "Right child"
                    }
                  },
                  "required": ["type", "id", "left", "right"],
                  "additionalProperties": false
                },
                "me.kpavlov.kt.schema.integration.type.TreeNode.Leaf": {
                  "type": "object",
                  "description": "A leaf node containing a value",
                  "properties": {
                    "type": {
                      "type": "string",
                      "const": "me.kpavlov.kt.schema.integration.type.TreeNode.Leaf"
                    },
                    "id": {
                      "type": "string",
                      "description": "Node identifier"
                    },
                    "value": {
                      "type": "string",
                      "description": "Leaf value"
                    }
                  },
                  "required": ["type", "id", "value"],
                  "additionalProperties": false
                }
              }
            }
            """.trimIndent()
    }
}
