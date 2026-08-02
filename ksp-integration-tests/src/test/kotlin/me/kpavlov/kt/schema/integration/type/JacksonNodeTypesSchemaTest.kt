package me.kpavlov.kt.schema.integration.type

import io.kotest.assertions.json.shouldEqualJson
import kotlin.test.Test

class JacksonNodeTypesSchemaTest {
    @Test
    fun `generates schema for class with jackson databind node types`() {
        val schema = JacksonNodeTypes::class.jsonSchemaString

        schema shouldEqualJson
            // language=json
            $$"""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "$id": "JacksonNodeTypes",
              "type": "object",
              "properties": {
                "jsonNode": {},
                "jsonNodeOpt": {},
                "objectNode": {},
                "objectNodeOpt": {},
                "arrayNode": {},
                "arrayNodeOpt": {},
                "valueNode": {},
                "stringNode": { "type": "string" },
                "stringNodeOpt": { "type": ["string", "null"] },
                "booleanNode": { "type": "boolean" },
                "intNode": { "type": "integer" },
                "longNode": { "type": "integer" },
                "doubleNode": { "type": "number" },
                "nullNode": {}
              },
              "required": [
                "jsonNode",
                "jsonNodeOpt",
                "objectNode",
                "objectNodeOpt",
                "arrayNode",
                "arrayNodeOpt",
                "valueNode",
                "stringNode",
                "stringNodeOpt",
                "booleanNode",
                "intNode",
                "longNode",
                "doubleNode",
                "nullNode"
              ],
              "additionalProperties": false
            }
            """.trimIndent()
    }

    @Test
    fun `generates jsonSchema object for class with jackson databind node types`() {
        val schema = JacksonNodeTypes::class.jsonSchema

        schema.toString() shouldEqualJson
            // language=json
            $$"""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "$id": "JacksonNodeTypes",
              "type": "object",
              "properties": {
                "jsonNode": {},
                "jsonNodeOpt": {},
                "objectNode": {},
                "objectNodeOpt": {},
                "arrayNode": {},
                "arrayNodeOpt": {},
                "valueNode": {},
                "stringNode": { "type": "string" },
                "stringNodeOpt": { "type": ["string", "null"] },
                "booleanNode": { "type": "boolean" },
                "intNode": { "type": "integer" },
                "longNode": { "type": "integer" },
                "doubleNode": { "type": "number" },
                "nullNode": {}
              },
              "required": [
                "jsonNode",
                "jsonNodeOpt",
                "objectNode",
                "objectNodeOpt",
                "arrayNode",
                "arrayNodeOpt",
                "valueNode",
                "stringNode",
                "stringNodeOpt",
                "booleanNode",
                "intNode",
                "longNode",
                "doubleNode",
                "nullNode"
              ],
              "additionalProperties": false
            }
            """.trimIndent()
    }
}
