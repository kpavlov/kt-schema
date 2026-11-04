package me.kpavlov.kt.schema.generator.json

import io.kotest.assertions.json.shouldEqualJson
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import me.kpavlov.kt.schema.test.fixtures.ValidatedOrder
import me.kpavlov.kt.schema.test.fixtures.placeValidatedOrder
import kotlin.test.Test

private enum class ParcelStatus { OPEN, CLOSED }

private data class ParcelAddress(
    val city: String,
)

private data class Parcel(
    @param:Size(min = 1) val address: ParcelAddress,
    @param:Pattern(regexp = "OPEN|CLOSED") val status: ParcelStatus,
    @param:Size(min = 1) val sender: ParcelAddress?,
)

@Suppress("unused")
private fun shipParcel(
    @Size(min = 1) address: ParcelAddress,
): String = address.city

class ValidationConstraintsSchemaTest {
    @Test
    fun `should emit constraints of referenced types next to the ref`() {
        val schema = ReflectionClassJsonSchemaGenerator.Default.generateSchemaString(Parcel::class)

        schema shouldEqualJson
            // language=JSON
            $$"""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "$id": "me.kpavlov.kt.schema.generator.json.Parcel",
              "type": "object",
              "properties": {
                "address": {
                  "allOf": [
                    { "$ref": "#/$defs/me.kpavlov.kt.schema.generator.json.ParcelAddress" },
                    { "type": "object", "minProperties": 1 }
                  ]
                },
                "status": {
                  "allOf": [
                    { "$ref": "#/$defs/me.kpavlov.kt.schema.generator.json.ParcelStatus" },
                    { "type": "string", "pattern": "OPEN|CLOSED" }
                  ]
                },
                "sender": {
                  "oneOf": [
                    { "type": "null" },
                    {
                      "allOf": [
                        { "$ref": "#/$defs/me.kpavlov.kt.schema.generator.json.ParcelAddress" },
                        { "type": "object", "minProperties": 1 }
                      ]
                    }
                  ]
                }
              },
              "required": ["address", "status", "sender"],
              "additionalProperties": false,
              "$defs": {
                "me.kpavlov.kt.schema.generator.json.ParcelAddress": {
                  "type": "object",
                  "properties": { "city": { "type": "string" } },
                  "required": ["city"],
                  "additionalProperties": false
                },
                "me.kpavlov.kt.schema.generator.json.ParcelStatus": { "type": "string", "enum": ["OPEN", "CLOSED"] }
              }
            }
            """.trimIndent()
    }

    @Test
    fun `should emit constraints of inlined referenced types in function schemas`() {
        val schema = ReflectionFunctionCallingSchemaGenerator.Default.generateSchemaString(::shipParcel)

        schema shouldEqualJson
            // language=JSON
            """
            {
              "type": "function",
              "name": "shipParcel",
              "strict": true,
              "parameters": {
                "type": "object",
                "properties": {
                  "address": {
                    "type": "object",
                    "properties": { "city": { "type": "string" } },
                    "required": ["city"],
                    "additionalProperties": false,
                    "minProperties": 1
                  }
                },
                "required": ["address"],
                "additionalProperties": false
              }
            }
            """.trimIndent()
    }

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
