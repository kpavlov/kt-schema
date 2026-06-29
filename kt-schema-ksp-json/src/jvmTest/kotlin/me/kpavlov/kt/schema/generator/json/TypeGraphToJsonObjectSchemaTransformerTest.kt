package me.kpavlov.kt.schema.generator.json

import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import me.kpavlov.kt.schema.generator.core.ir.TypeGraph
import me.kpavlov.kt.schema.json.JsonSchema
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test

class TypeGraphToJsonObjectSchemaTransformerTest {
    @Test
    fun `should delegate to TypeGraphToJsonSchemaTransformer and return JsonObject`() {
        // Arrange
        val mockTransformer = mockk<TypeGraphToJsonSchemaTransformer>()
        val config = JsonSchemaConfig.Default
        val transformer = TypeGraphToJsonObjectSchemaTransformer(config, jsonSchemaTransformer = mockTransformer)

        val mockGraph = mockk<TypeGraph>()
        val rootName = "TestRoot"

        val expectedSchema =
            JsonSchema(
                description = "Test Description",
                type = listOf("object"),
            )

        every { mockTransformer.transform(mockGraph, rootName) } returns expectedSchema

        // Act
        val result = transformer.transform(mockGraph, rootName)

        // Assert
        verify { mockTransformer.transform(mockGraph, rootName) }

        result["description"] shouldBe JsonPrimitive("Test Description")
    }
}
