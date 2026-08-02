@file:Suppress("unused")

package me.kpavlov.kt.schema.generator.json

import io.kotest.assertions.json.shouldEqualJson
import kotlinx.serialization.json.Json
import kotlin.test.Test

class NestedSealedHierarchyTest {
    //region Fixture

    sealed interface Vehicle {
        sealed interface Motorized : Vehicle {
            data class Car(
                val doors: Int,
            ) : Motorized

            data class Truck(
                val payload: Double,
            ) : Motorized
        }

        data class Bicycle(
            val gears: Int,
        ) : Vehicle
    }

    data class Delivery(val vehicle: Vehicle?)

    //endregion

    private val generator =
        ReflectionClassJsonSchemaGenerator(
            json = Json { encodeDefaults = false },
            config = JsonSchemaConfig.Strict,
        )

    @Test
    fun `nullable vehicle uses oneOf with null and ref to sealed Vehicle`() {
        val schema = generator.generateSchemaString(Delivery::class)

        // language=JSON
        schema shouldEqualJson
            $$"""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "$id": "Delivery",
              "type": "object",
              "properties": {
                "vehicle": {
                  "oneOf": [
                    { "type": "null" },
                    { "$ref": "#/$defs/Vehicle" }
                  ]
                }
              },
              "required": ["vehicle"],
              "additionalProperties": false,
              "$defs": {
                "Vehicle": {
                  "oneOf": [
                    { "$ref": "#/$defs/Bicycle" },
                    { "$ref": "#/$defs/Motorized" }
                  ]
                },
                "Bicycle": {
                  "type": "object",
                  "properties": {
                    "type": {
                      "type": "string",
                      "const": "Bicycle"
                    },
                    "gears": { "type": "integer" }
                  },
                  "required": ["type", "gears"],
                  "additionalProperties": false
                },
                "Car": {
                  "type": "object",
                  "properties": {
                    "type": {
                      "type": "string",
                      "const": "Car"
                    },
                    "doors": { "type": "integer" }
                  },
                  "required": ["type", "doors"],
                  "additionalProperties": false
                },
                "Truck": {
                  "type": "object",
                  "properties": {
                    "type": {
                      "type": "string",
                      "const": "Truck"
                    },
                    "payload": { "type": "number" }
                  },
                  "required": ["type", "payload"],
                  "additionalProperties": false
                },
                "Motorized": {
                  "oneOf": [
                    { "$ref": "#/$defs/Car" },
                    { "$ref": "#/$defs/Truck" }
                  ]
                }
              }
            }
            """.trimIndent()
    }

    @Test
    fun `nested sealed class should be oneOf in defs`() {
        val schema = generator.generateSchemaString(Vehicle::class)

        // language=JSON
        schema shouldEqualJson
            $$"""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "$id": "Vehicle",
              "type": "object",
              "additionalProperties": false,
              "oneOf": [
                { "$ref": "#/$defs/Bicycle" },
                { "$ref": "#/$defs/Motorized" }
              ],
              "$defs": {
                "Bicycle": {
                  "type": "object",
                  "properties": {
                    "type": {
                      "type": "string",
                      "const": "Bicycle"
                    },
                    "gears": { "type": "integer" }
                  },
                  "required": ["type", "gears"],
                  "additionalProperties": false
                },
                "Car": {
                  "type": "object",
                  "properties": {
                    "type": {
                      "type": "string",
                      "const": "Car"
                    },
                    "doors": { "type": "integer" }
                  },
                  "required": ["type", "doors"],
                  "additionalProperties": false
                },
                "Truck": {
                  "type": "object",
                  "properties": {
                    "type": {
                      "type": "string",
                      "const": "Truck"
                    },
                    "payload": { "type": "number" }
                  },
                  "required": ["type", "payload"],
                  "additionalProperties": false
                },
                "Motorized": {
                  "oneOf": [
                    { "$ref": "#/$defs/Car" },
                    { "$ref": "#/$defs/Truck" }
                  ]
                }
              }
            }
            """.trimIndent()
    }
}
