package me.kpavlov.kt.schema.generator.json

import io.kotest.assertions.json.shouldEqualJson
import me.kpavlov.kt.schema.test.fixtures.ValidatedOrder
import me.kpavlov.kt.schema.test.fixtures.placeValidatedOrder
import kotlin.test.Test

class ValidationConstraintsSchemaTest {
    @Test
    fun `should emit Jakarta constraints of class properties`() {
        val schema = ReflectionClassJsonSchemaGenerator.Default.generateSchemaString(ValidatedOrder::class)

        schema shouldEqualJson
            // language=JSON
            $$"""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "$id": "me.kpavlov.kt.schema.test.fixtures.ValidatedOrder",
              "type": "object",
              "properties": {
                "quantity": { "type": "integer", "minimum": 1, "maximum": 100 },
                "price": { "type": "number", "minimum": 0.01, "exclusiveMaximum": 1000 },
                "weight": { "type": "integer", "exclusiveMinimum": 0 },
                "discount": { "type": "integer", "minimum": 0 },
                "debit": { "type": "integer", "exclusiveMaximum": 0 },
                "credit": { "type": "integer", "maximum": 0 },
                "name": { "type": "string", "minLength": 2, "maxLength": 50 },
                "tags": { "type": "array", "items": { "type": "string" }, "minItems": 1 },
                "note": { "type": ["string", "null"], "minLength": 1 },
                "currency": { "type": "string", "pattern": "[A-Z]{3}" },
                "contact": { "type": "string", "format": "email" },
                "attributes": {
                  "type": "object",
                  "additionalProperties": { "type": "string" },
                  "maxProperties": 5
                },
                "level": { "type": "integer", "minimum": 0, "maximum": 127 }
              },
              "required": [
                "quantity", "price", "weight", "discount", "debit", "credit", "name", "tags", "note",
                "currency", "contact", "attributes", "level"
              ],
              "additionalProperties": false
            }
            """.trimIndent()
    }

    @Test
    fun `should emit Jakarta constraints of function parameters`() {
        val schema = ReflectionFunctionCallingSchemaGenerator.Default.generateSchemaString(::placeValidatedOrder)

        schema shouldEqualJson
            // language=JSON
            """
            {
              "type": "function",
              "name": "placeValidatedOrder",
              "strict": true,
              "parameters": {
                "type": "object",
                "properties": {
                  "quantity": { "type": "integer", "minimum": 1 },
                  "customer": { "type": "string", "minLength": 1 },
                  "tags": { "type": "array", "items": { "type": "string" }, "maxItems": 3 }
                },
                "required": ["quantity", "customer", "tags"],
                "additionalProperties": false
              }
            }
            """.trimIndent()
    }
}
