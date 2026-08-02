package me.kpavlov.kt.schema.integration.type

import io.kotest.assertions.json.shouldEqualJson
import kotlin.test.Test

/**
 * Tests for Animal schema generation - sealed class polymorphism.
 */
class AnimalSchemaTest {
    @Test
    fun `generates polymorphic schema with oneOf composition`() {
        val schema = Animal::class.jsonSchemaString

        // language=json
        schema shouldEqualJson
            $$"""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "$id": "Animal",
              "description": "Multicellular eukaryotic organism of the kingdom Metazoa",
              "type": "object",
              "additionalProperties": false,
              "oneOf": [
                {
                  "$ref": "#/$defs/Cat"
                },
                {
                  "$ref": "#/$defs/Dog"
                }
              ],
              "$defs": {
                "Cat": {
                  "type": "object",
                  "properties": {
                   "type": {
                      "type": "string",
                      "const": "Cat"
                    },
                    "name": {
                      "type": "string",
                      "description": "Animal's name"
                    }
                  },
                  "required": [
                    "type",
                    "name"
                  ],
                  "additionalProperties": false
                },
                "Dog": {
                  "type": "object",
                  "properties": {
                    "type": {
                      "type": "string",
                      "const": "Dog"
                    },
                    "name": {
                      "type": "string",
                      "description": "Animal's name"
                    }
                  },
                  "required": [
                    "type",
                    "name"
                  ],
                  "additionalProperties": false
                }
              }
            } 
            """.trimIndent()
    }
}
