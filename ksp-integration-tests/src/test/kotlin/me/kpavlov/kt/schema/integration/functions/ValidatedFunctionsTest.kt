package me.kpavlov.kt.schema.integration.functions

import io.kotest.assertions.json.shouldEqualJson
import kotlin.test.Test

class ValidatedFunctionsTest {
    @Test
    fun `shipOrder emits Jakarta constraints on parameters`() {
        val schema = shipOrderJsonSchemaString()

        // language=json
        schema shouldEqualJson
            """
            {
              "type": "function",
              "name": "shipOrder",
              "strict": true,
              "parameters": {
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
            }
            """.trimIndent()
    }
}
