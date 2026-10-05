package me.kpavlov.kt.schema.generator.json

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import me.kpavlov.kt.schema.generator.core.ir.ObjectNode
import me.kpavlov.kt.schema.generator.core.ir.TypeGraph
import me.kpavlov.kt.schema.generator.core.ir.TypeId
import me.kpavlov.kt.schema.generator.core.ir.TypeNode
import me.kpavlov.kt.schema.generator.core.ir.TypeRef
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JsonTypeNamesTest {
    /** (id, requested name); a name equal to the id means FQN-derived. */
    private fun graphOf(vararg entries: Pair<String, String>): TypeGraph {
        val nodes: Map<TypeId, TypeNode> =
            entries.associate { (id, name) -> TypeId(id) to ObjectNode(name = name, properties = emptyList()) }
        return TypeGraph(root = TypeRef.Ref(nodes.keys.first()), nodes = nodes)
    }

    fun cases(): List<Arguments> =
        listOf(
            Arguments.of(
                "short off keeps FQNs",
                false,
                listOf("com.acme.Payload" to "com.acme.Payload"),
                mapOf("com.acme.Payload" to "com.acme.Payload"),
            ),
            Arguments.of(
                "short off keeps a unique override",
                false,
                listOf("a.X" to "Thing", "b.Y" to "b.Y"),
                mapOf("a.X" to "Thing", "b.Y" to "b.Y"),
            ),
            Arguments.of(
                "simple name",
                true,
                listOf("com.acme.Payload" to "com.acme.Payload"),
                mapOf("com.acme.Payload" to "Payload"),
            ),
            Arguments.of(
                "suffix expanded on collision",
                true,
                listOf("a.ResultA.Success" to "a.ResultA.Success", "b.ResultB.Success" to "b.ResultB.Success"),
                mapOf("a.ResultA.Success" to "ResultA.Success", "b.ResultB.Success" to "ResultB.Success"),
            ),
            Arguments.of(
                "full id fallback",
                true,
                listOf("a.x.Item" to "a.x.Item", "b.x.Item" to "b.x.Item"),
                mapOf("a.x.Item" to "a.x.Item", "b.x.Item" to "b.x.Item"),
            ),
            Arguments.of(
                "unique override kept as is",
                true,
                listOf("c.Z" to "Thing", "a.X" to "a.X"),
                mapOf("c.Z" to "Thing", "a.X" to "X"),
            ),
            Arguments.of(
                "override colliding with a short name",
                true,
                listOf("com.acme.Payload" to "com.acme.Payload", "x.Other" to "Payload"),
                mapOf("com.acme.Payload" to "acme.Payload", "x.Other" to "Payload"),
            ),
            Arguments.of(
                "override containing dots is not shortened",
                true,
                listOf("a.X" to "my.Name"),
                mapOf("a.X" to "my.Name"),
            ),
        )

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    fun `should resolve names independently of insertion order`(
        @Suppress("UNUSED_PARAMETER") title: String,
        shortNames: Boolean,
        entries: List<Pair<String, String>>,
        expected: Map<String, String>,
    ) {
        // When
        val forward = graphOf(*entries.toTypedArray()).jsonTypeNames(shortNames)
        val reversed = graphOf(*entries.reversed().toTypedArray()).jsonTypeNames(shortNames)

        // Then
        forward.mapKeys { it.key.value } shouldBe expected
        reversed.mapKeys { it.key.value } shouldBe expected
    }

    fun clashes(): List<Arguments> =
        listOf(
            Arguments.of(
                "duplicate overrides",
                false,
                listOf("a.TextValue" to "Shared", "a.CountValue" to "Shared"),
                "Type name 'Shared' is used by multiple declarations: a.CountValue, a.TextValue.",
            ),
            Arguments.of(
                "duplicate overrides with short names",
                true,
                listOf("a.TextValue" to "Shared", "a.CountValue" to "Shared"),
                "Type name 'Shared' is used by multiple declarations: a.CountValue, a.TextValue.",
            ),
            Arguments.of(
                "override equal to another node's full id",
                false,
                listOf("com.example.X" to "com.example.X", "com.example.W" to "com.example.X"),
                "Type name 'com.example.X' is used by multiple declarations: com.example.W, com.example.X.",
            ),
        )

    @ParameterizedTest(name = "{0}")
    @MethodSource("clashes")
    fun `should fail with the same message regardless of insertion order`(
        @Suppress("UNUSED_PARAMETER") title: String,
        shortNames: Boolean,
        entries: List<Pair<String, String>>,
        expectedMessage: String,
    ) {
        val forward = shouldThrow<IllegalStateException> { graphOf(*entries.toTypedArray()).jsonTypeNames(shortNames) }
        val reversed =
            shouldThrow<IllegalStateException> { graphOf(*entries.reversed().toTypedArray()).jsonTypeNames(shortNames) }

        forward.message shouldStartWith expectedMessage
        reversed.message shouldBe forward.message
    }
}
