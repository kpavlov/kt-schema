package me.kpavlov.kt.schema.integration.type

import io.kotest.assertions.json.shouldEqualJson
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test

class GenericObjectTripSchemaTest {
    private val pkg = "me.kpavlov.kt.schema.integration.type"

    // language=json
    private val definitions =
        $$"""
        {
          "$$pkg.GenericBox_of_String": {
            "type": "object",
            "properties": { "value": { "type": "string" } },
            "required": ["value"],
            "additionalProperties": false
          },
          "$$pkg.GenericBox_of_Int": {
            "type": "object",
            "properties": { "value": { "type": "integer" } },
            "required": ["value"],
            "additionalProperties": false
          },
          "$$pkg.GenericBox_of_nullable_String": {
            "type": "object",
            "properties": { "value": { "type": ["string", "null"] } },
            "required": ["value"],
            "additionalProperties": false
          },
          "$$pkg.GenericBox_of_List_of_Int": {
            "type": "object",
            "properties": { "value": { "type": "array", "items": { "type": "integer" } } },
            "required": ["value"],
            "additionalProperties": false
          }
        }
        """.trimIndent()

    // language=json
    private val expectedSchema =
        $$"""
        {
          "$schema": "https://json-schema.org/draft/2020-12/schema",
          "$id": "$$pkg.GenericObjectTrip",
          "type": "object",
          "properties": {
            "text": { "$ref": "#/$defs/$$pkg.GenericBox_of_String" },
            "count": { "$ref": "#/$defs/$$pkg.GenericBox_of_Int" },
            "nullableText": { "$ref": "#/$defs/$$pkg.GenericBox_of_nullable_String" },
            "ids": { "$ref": "#/$defs/$$pkg.GenericBox_of_List_of_Int" }
          },
          "required": ["text", "count", "nullableText", "ids"],
          "additionalProperties": false,
          "$defs": $$definitions
        }
        """.trimIndent()

    // language=json
    private val expectedReversedSchema =
        $$"""
        {
          "$schema": "https://json-schema.org/draft/2020-12/schema",
          "$id": "$$pkg.GenericObjectReversedTrip",
          "type": "object",
          "properties": {
            "ids": { "$ref": "#/$defs/$$pkg.GenericBox_of_List_of_Int" },
            "nullableText": { "$ref": "#/$defs/$$pkg.GenericBox_of_nullable_String" },
            "count": { "$ref": "#/$defs/$$pkg.GenericBox_of_Int" },
            "text": { "$ref": "#/$defs/$$pkg.GenericBox_of_String" }
          },
          "required": ["ids", "nullableText", "count", "text"],
          "additionalProperties": false,
          "$defs": $$definitions
        }
        """.trimIndent()

    // language=json
    private val expectedNodeSchema =
        $$"""
        {
          "$schema": "https://json-schema.org/draft/2020-12/schema",
          "$id": "$$pkg.GenericNodeTrip",
          "type": "object",
          "properties": {
            "root": { "$ref": "#/$defs/$$pkg.GenericNode_of_String" }
          },
          "required": ["root"],
          "additionalProperties": false,
          "$defs": {
            "$$pkg.GenericNode_of_String": {
              "type": "object",
              "properties": {
                "value": { "type": "string" },
                "children": { "type": "array", "items": { "$ref": "#/$defs/$$pkg.GenericNode_of_String" } }
              },
              "required": ["value", "children"],
              "additionalProperties": false
            }
          }
        }
        """.trimIndent()

    @Test
    fun `keeps a definition per applied argument in jsonSchemaString`() {
        GenericObjectTrip::class.jsonSchemaString shouldEqualJson expectedSchema
    }

    @Test
    fun `keeps a definition per applied argument in jsonSchema`() {
        GenericObjectTrip::class.jsonSchema.toString() shouldEqualJson expectedSchema
    }

    @Test
    fun `does not depend on property order`() {
        GenericObjectReversedTrip::class.jsonSchemaString shouldEqualJson expectedReversedSchema
    }

    @Test
    fun `resolves recursive generic objects against their own application in jsonSchemaString`() {
        GenericNodeTrip::class.jsonSchemaString shouldEqualJson expectedNodeSchema
    }

    @Test
    fun `resolves recursive generic objects against their own application in jsonSchema`() {
        GenericNodeTrip::class.jsonSchema.toString() shouldEqualJson expectedNodeSchema
    }

    @Test
    fun `keeps wrapped definitions of recursive generic value classes apart`() {
        val schema = Json.parseToJsonElement(GenericIndexTrip::class.jsonSchemaString).jsonObject

        val properties = schema.getValue("properties").jsonObject
        properties.getValue("ints") shouldNotBe properties.getValue("strings")
        schema.getValue($$"$defs").jsonObject.keys.size shouldBe 2
    }
}
