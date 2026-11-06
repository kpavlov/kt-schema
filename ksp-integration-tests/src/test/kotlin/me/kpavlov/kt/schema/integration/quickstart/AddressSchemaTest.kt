package me.kpavlov.kt.schema.integration.quickstart

import io.kotest.assertions.json.shouldEqualJson
import kotlin.test.Test

class AddressSchemaTest {
    @Test
    fun `generates the schema shown in the quick start and generated schemas guides`() {
        val schema = Address::class.jsonSchemaString

        // language=json
        schema shouldEqualJson
            $$"""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "$id": "me.kpavlov.kt.schema.integration.quickstart.Address",
              "description": "A postal address for deliveries and billing.",
              "type": "object",
              "properties": {
                "street": {
                  "type": "string",
                  "description": "Street address, including house number",
                  "maxLength": 120
                },
                "city": {
                  "type": "string",
                  "description": "City or town name",
                  "minLength": 1,
                  "maxLength": 80
                },
                "zipCode": {
                  "type": "string",
                  "description": "Postal or ZIP code",
                  "pattern": "[0-9]{5}"
                },
                "country": {
                  "type": "string",
                  "description": "Two-letter ISO country code; defaults to US",
                  "pattern": "[A-Z]{2}"
                }
              },
              "required": ["street", "city", "zipCode", "country"],
              "additionalProperties": false
            }
            """.trimIndent()
    }
}
