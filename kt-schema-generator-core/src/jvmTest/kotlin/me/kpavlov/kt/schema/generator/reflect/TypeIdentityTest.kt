package me.kpavlov.kt.schema.generator.reflect

import com.fasterxml.jackson.annotation.JsonTypeName
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import me.kpavlov.kt.schema.generator.core.ir.NamedTypeNode
import me.kpavlov.kt.schema.generator.core.ir.TypeId
import kotlin.test.Test

class TypeIdentityTest {
    @JsonTypeName("Shared")
    data class TextValue(
        val text: String,
    )

    @JsonTypeName("Shared")
    data class CountValue(
        val count: Int,
    )

    data class Payload(
        val textValue: TextValue,
        val countValue: CountValue,
    )

    @Test
    fun `should keep distinct nodes keyed by FQN when classes share a name override`() {
        // Given
        val textId = TypeId(TextValue::class.qualifiedName!!)
        val countId = TypeId(CountValue::class.qualifiedName!!)

        // When
        val graph = ReflectionClassIntrospector.introspect(Payload::class)

        // Then
        graph.nodes shouldContainKey textId
        graph.nodes shouldContainKey countId
        graph.nodes
            .getValue(textId)
            .shouldBeInstanceOf<NamedTypeNode>()
            .name shouldBe "Shared"
        graph.nodes
            .getValue(countId)
            .shouldBeInstanceOf<NamedTypeNode>()
            .name shouldBe "Shared"
    }
}
