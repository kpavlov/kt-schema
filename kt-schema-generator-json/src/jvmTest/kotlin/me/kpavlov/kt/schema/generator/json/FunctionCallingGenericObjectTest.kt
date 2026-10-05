@file:Suppress("FunctionOnlyReturningConstant", "UnusedParameter", "unused")

package me.kpavlov.kt.schema.generator.json

import io.kotest.assertions.json.shouldEqualJson
import kotlinx.serialization.json.Json
import kotlin.test.Test

class FunctionCallingGenericObjectTest {
    private val generator =
        ReflectionFunctionCallingSchemaGenerator(
            json = Json { prettyPrint = true },
            config = FunctionCallingSchemaConfig.Strict,
        )

    data class Box<T>(
        val value: T,
    )

    object Tools {
        fun send(
            text: Box<String>,
            count: Box<Int>,
            nullableText: Box<String?>,
            ids: Box<List<Int>>,
        ): String = ""

        fun sendReversed(
            ids: Box<List<Int>>,
            nullableText: Box<String?>,
            count: Box<Int>,
            text: Box<String>,
        ): String = ""
    }

    // language=json
    private val parameters =
        """
        {
          "type": "object",
          "properties": {
            "text": {
              "type": "object",
              "properties": { "value": { "type": "string" } },
              "required": ["value"],
              "additionalProperties": false
            },
            "count": {
              "type": "object",
              "properties": { "value": { "type": "integer" } },
              "required": ["value"],
              "additionalProperties": false
            },
            "nullableText": {
              "type": "object",
              "properties": { "value": { "type": ["string", "null"] } },
              "required": ["value"],
              "additionalProperties": false
            },
            "ids": {
              "type": "object",
              "properties": { "value": { "type": "array", "items": { "type": "integer" } } },
              "required": ["value"],
              "additionalProperties": false
            }
          },
          "required": ["text", "count", "nullableText", "ids"],
          "additionalProperties": false
        }
        """.trimIndent()

    @Test
    fun `inlines each application of a generic class with its own argument types`() {
        generator.generateSchemaString(Tools::send) shouldEqualJson
            // language=json
            """
            {
              "type": "function",
              "name": "send",
              "description": "",
              "strict": true,
              "parameters": $parameters
            }
            """.trimIndent()
    }

    @Test
    fun `does not depend on parameter order`() {
        val reversed = generator.generateSchemaString(Tools::sendReversed)

        reversed shouldEqualJson
            // language=json
            """
            {
              "type": "function",
              "name": "sendReversed",
              "description": "",
              "strict": true,
              "parameters": {
                "type": "object",
                "properties": {
                  "ids": {
                    "type": "object",
                    "properties": { "value": { "type": "array", "items": { "type": "integer" } } },
                    "required": ["value"],
                    "additionalProperties": false
                  },
                  "nullableText": {
                    "type": "object",
                    "properties": { "value": { "type": ["string", "null"] } },
                    "required": ["value"],
                    "additionalProperties": false
                  },
                  "count": {
                    "type": "object",
                    "properties": { "value": { "type": "integer" } },
                    "required": ["value"],
                    "additionalProperties": false
                  },
                  "text": {
                    "type": "object",
                    "properties": { "value": { "type": "string" } },
                    "required": ["value"],
                    "additionalProperties": false
                  }
                },
                "required": ["ids", "nullableText", "count", "text"],
                "additionalProperties": false
              }
            }
            """.trimIndent()
    }
}
