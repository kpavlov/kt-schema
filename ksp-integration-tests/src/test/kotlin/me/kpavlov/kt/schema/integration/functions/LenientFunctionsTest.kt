package me.kpavlov.kt.schema.integration.functions

import io.kotest.assertions.json.shouldEqualJson
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlin.test.Test

class LenientFunctionsTest {
    @Test
    fun `parameters with a Kotlin default are not required under the lenient config`() {
        val schema = convertTemperatureJsonSchemaString()

        // language=json
        schema shouldEqualJson
            """
            {
              "type": "function",
              "name": "convertTemperature",
              "description": "Converts a temperature",
              "parameters": {
                "type": "object",
                "properties": {
                  "value": {
                    "type": "number",
                    "description": "Temperature value"
                  },
                  "unit": {
                    "type": "string",
                    "description": "Target unit"
                  },
                  "precision": {
                    "type": "integer",
                    "description": "Decimal places"
                  }
                },
                "required": ["value"]
              }
            }
            """.trimIndent()
    }

    @Test
    fun `jsonSchema matches jsonSchemaString`() {
        convertTemperatureJsonSchema() shouldBe Json.parseToJsonElement(convertTemperatureJsonSchemaString())
    }
}
