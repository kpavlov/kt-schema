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

    @Test
    fun `honors Jackson name override declared only on the child override`() {
        val schema = JacksonNote::class.jsonSchemaString

        // language=json
        schema shouldEqualJson
            $$"""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "$id": "me.kpavlov.kt.schema.integration.type.JacksonNote",
              "type": "object",
              "properties": {
                "body": {
                  "type": "string"
                },
                "memo_id": {
                  "type": "string"
                }
              },
              "required": [
                "body",
                "memo_id"
              ],
              "additionalProperties": false
            }
            """.trimIndent()
    }

    @Test
    fun `does not duplicate a constructor-overridden sealed property under its raw name`() {
        val schema = JacksonEntry::class.jsonSchemaString

        // language=json
        schema shouldEqualJson
            $$"""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "$id": "me.kpavlov.kt.schema.integration.type.JacksonEntry",
              "type": "object",
              "properties": {
                "entry_id": {
                  "type": "string"
                },
                "label": {
                  "type": "string"
                }
              },
              "required": [
                "entry_id",
                "label"
              ],
              "additionalProperties": false
            }
            """.trimIndent()
    }
}
