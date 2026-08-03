package me.kpavlov.kt.schema.integration.type

import io.kotest.assertions.json.shouldEqualJson
import kotlin.test.Test

/**
 * Tests for Priority schema generation - SerialName overrides on an enum class and its entries.
 */
class PrioritySchemaTest {
    @Test
    fun `generates enum schema with SerialName-overridden id and values`() {
        val schema = Priority::class.jsonSchemaString

        // language=json
        schema shouldEqualJson
            $$"""
            {
              "$id": "PriorityLevel",
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "type": "string",
              "enum": ["p0_critical", "p1_high", "p2_low"],
              "description": "Enum class with SerialName overrides on the class and its entries."
            }
            """.trimIndent()
    }
}
