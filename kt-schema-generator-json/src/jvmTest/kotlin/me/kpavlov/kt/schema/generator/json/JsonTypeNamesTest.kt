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

    fun cases(): List<Arguments> = plainCases() + genericNameCases() + genericCollisionCases()

    private fun plainCases(): List<Arguments> =
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

    private fun genericNameCases(): List<Arguments> =
        listOf(
            Arguments.of(
                "generic arguments are appended to the full id",
                false,
                listOf("com.acme.Box<kotlin.String>" to "com.acme.Box<kotlin.String>"),
                mapOf("com.acme.Box<kotlin.String>" to "com.acme.Box_of_String"),
            ),
            Arguments.of(
                "generic arguments are appended to the short name",
                true,
                listOf("com.acme.Box<kotlin.String>" to "com.acme.Box<kotlin.String>"),
                mapOf("com.acme.Box<kotlin.String>" to "Box_of_String"),
            ),
            Arguments.of(
                "nested, several and nullable arguments",
                true,
                listOf(
                    "a.Pair<kotlin.String,a.Box<kotlin.collections.List<kotlin.Int>>>" to
                        "a.Pair<kotlin.String,a.Box<kotlin.collections.List<kotlin.Int>>>",
                    "a.Box<kotlin.String?>" to "a.Box<kotlin.String?>",
                ),
                mapOf(
                    "a.Pair<kotlin.String,a.Box<kotlin.collections.List<kotlin.Int>>>" to
                        "Pair_of_String_and_Box_of_List_of_Int",
                    "a.Box<kotlin.String?>" to "Box_of_nullable_String",
                ),
            ),
            Arguments.of(
                "explicit name on an applied id is kept as is",
                true,
                listOf("a.Box<kotlin.String>" to "Thing"),
                mapOf("a.Box<kotlin.String>" to "Thing"),
            ),
        )

    private fun genericCollisionCases(): List<Arguments> =
        listOf(
            Arguments.of(
                "same argument name in different packages expands every name, short off",
                false,
                listOf("p.Box<a.User>" to "p.Box<a.User>", "p.Box<b.User>" to "p.Box<b.User>"),
                mapOf("p.Box<a.User>" to "p.Box_of_a.User", "p.Box<b.User>" to "p.Box_of_b.User"),
            ),
            Arguments.of(
                "same argument name in different packages expands every name, short on",
                true,
                listOf("p.Box<a.User>" to "p.Box<a.User>", "p.Box<b.User>" to "p.Box<b.User>"),
                mapOf("p.Box<a.User>" to "p.Box_of_a.User", "p.Box<b.User>" to "p.Box_of_b.User"),
            ),
            Arguments.of(
                "generic without a clash keeps its short name next to a clashing one",
                true,
                listOf(
                    "p.Box<a.User>" to "p.Box<a.User>",
                    "p.Box<b.User>" to "p.Box<b.User>",
                    "p.Box<kotlin.String>" to "p.Box<kotlin.String>",
                ),
                mapOf(
                    "p.Box<a.User>" to "p.Box_of_a.User",
                    "p.Box<b.User>" to "p.Box_of_b.User",
                    "p.Box<kotlin.String>" to "Box_of_String",
                ),
            ),
            Arguments.of(
                "same-named classes of different arity are told apart by package",
                true,
                listOf(
                    "p.Pair<x.Box<k.A>,k.B>" to "p.Pair<x.Box<k.A>,k.B>",
                    "p.Pair<y.Box<k.A,k.B>>" to "p.Pair<y.Box<k.A,k.B>>",
                ),
                mapOf(
                    "p.Pair<x.Box<k.A>,k.B>" to "p.Pair_of_x.Box_of_k.A_and_k.B",
                    "p.Pair<y.Box<k.A,k.B>>" to "p.Pair_of_y.Box_of_k.A_and_k.B",
                ),
            ),
            Arguments.of(
                "generic name colliding with a plain class escalates the generic one",
                false,
                listOf("a.Box<kotlin.String>" to "a.Box<kotlin.String>", "a.Box_of_String" to "a.Box_of_String"),
                mapOf("a.Box<kotlin.String>" to "a.Box_of_kotlin.String", "a.Box_of_String" to "a.Box_of_String"),
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
                "one explicit name on two applications of a generic class",
                true,
                listOf("a.Box<kotlin.String>" to "Shared", "a.Box<kotlin.Int>" to "Shared"),
                "Type name 'Shared' is used by multiple declarations: a.Box<kotlin.Int>, a.Box<kotlin.String>.",
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
