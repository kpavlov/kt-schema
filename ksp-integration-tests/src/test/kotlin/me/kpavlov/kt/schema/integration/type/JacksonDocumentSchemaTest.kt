package me.kpavlov.kt.schema.integration.type

import io.kotest.assertions.json.shouldEqualJson
import kotlin.test.Test

class JacksonDocumentSchemaTest {
    @Test
    fun `inherited sealed property honors Jackson name override`() {
        val schema = JacksonReport::class.jsonSchemaString

        // language=json
        schema shouldEqualJson
            $$"""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "$id": "me.kpavlov.kt.schema.integration.type.JacksonReport",
              "type": "object",
              "properties": {
                "body": {
                  "type": "string"
                },
                "document_id": {
                  "type": "string"
                }
              },
              "required": [
                "body",
                "document_id"
              ],
              "additionalProperties": false
            }
            """.trimIndent()
    }
}
