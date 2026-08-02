/*
 * Copyright © 2026 Konstantin Pavlov and contributors
 */
package me.kpavlov.kt.schema.integration.type

import io.kotest.assertions.json.shouldEqualJson
import kotlin.test.Test

class BuiltInJsonTypesSchemaTest {
    @Test
    fun `generates schema for class with built-in json types`() {
        val schema = BuiltInJsonTypes::class.jsonSchemaString

        schema shouldEqualJson
            // language=JSON
            $$"""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "$id": "me.kpavlov.kt.schema.integration.type.BuiltInJsonTypes",
              "type": "object",
              "properties": {
                "objProp": {
                  "type": "object",
                  "additionalProperties": {}
                },
                "objPropOpt": {
                  "type": ["object", "null"],
                  "additionalProperties": {}
                },
                "elementProp": {},
                "elementPropOpt": {},
                "arrayProp": {
                  "type": "array"
                },
                "arrayPropOpt": {
                  "type": ["array", "null"]
                },
                "primitive": {},
                "primitiveOpt": {},
                "nullProp": {},
                "nullPropOpt": {}
              },
              "required": [
                "objProp",
                "objPropOpt",
                "elementProp",
                "elementPropOpt",
                "arrayProp",
                "arrayPropOpt",
                "primitive",
                "primitiveOpt",
                "nullProp",
                "nullPropOpt"
              ],
              "additionalProperties": false
            }
            """.trimIndent()
    }
}
