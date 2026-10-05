package me.kpavlov.kt.schema.generator.json

import io.kotest.assertions.json.shouldEqualJson
import me.kpavlov.kt.schema.test.fixtures.WorkspacePath
import me.kpavlov.kt.schema.test.fixtures.Wrapper
import kotlin.test.Test

class CompiledValueClassReflectionTest {
    //region Test models

    data class Holder(
        val workspace: WorkspacePath,
        val count: Wrapper<Int>,
    )

    //endregion

    @Test
    fun `should flatten inline value classes declared in another module`() {
        val schema = ReflectionClassJsonSchemaGenerator().generateSchemaString(Holder::class)

        schema shouldEqualJson
            // language=JSON
            $$"""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "$id": "me.kpavlov.kt.schema.generator.json.CompiledValueClassReflectionTest.Holder",
              "type": "object",
              "properties": {
                "workspace": { "type": "string" },
                "count": { "type": "integer" }
              },
              "required": ["workspace", "count"],
              "additionalProperties": false
            }
            """.trimIndent()
    }
}
