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
