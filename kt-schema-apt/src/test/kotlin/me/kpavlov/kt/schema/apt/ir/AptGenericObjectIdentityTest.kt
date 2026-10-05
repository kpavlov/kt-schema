package me.kpavlov.kt.schema.apt.ir

import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import me.kpavlov.kt.schema.generator.core.ir.AnyNode
import me.kpavlov.kt.schema.generator.core.ir.ListNode
import me.kpavlov.kt.schema.generator.core.ir.ObjectNode
import me.kpavlov.kt.schema.generator.core.ir.PrimitiveKind
import me.kpavlov.kt.schema.generator.core.ir.PrimitiveNode
import me.kpavlov.kt.schema.generator.core.ir.TypeGraph
import me.kpavlov.kt.schema.generator.core.ir.TypeId
import me.kpavlov.kt.schema.generator.core.ir.TypeRef
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AptGenericObjectIdentityTest {
    private val record =
        // language=java
        """
        package com.example;

        public record Box<T>(T value) {}
        """.trimIndent()

    private val plainClass =
        // language=java
        """
        package com.example;

        public class Box<T> {
            public T value;
        }
        """.trimIndent()

    private val interfaceType =
        // language=java
        """
        package com.example;

        public interface Box<T> {
            T getValue();
        }
        """.trimIndent()

    private val payload =
        // language=java
        """
        package com.example;

        import java.util.List;

        public record Payload(Box<String> text, Box<Integer> count, Box<List<Integer>> ids) {}
        """.trimIndent()

    private val reversedPayload =
        // language=java
        """
        package com.example;

        import java.util.List;

        public record ReversedPayload(Box<List<Integer>> ids, Box<Integer> count, Box<String> text) {}
        """.trimIndent()

    private val string = TypeRef.Inline(PrimitiveNode(PrimitiveKind.STRING))

    private fun boxDeclarations() =
        listOf(
            Arguments.of("record", record),
            Arguments.of("class", plainClass),
            Arguments.of("interface", interfaceType),
        )

    private fun TypeGraph.valueOf(id: String): TypeRef =
        (nodes.getValue(TypeId(id)) as ObjectNode).properties.single().type

    @ParameterizedTest(name = "{0}")
    @MethodSource("boxDeclarations")
    fun `should keep a definition per applied argument`(
        @Suppress("UNUSED_PARAMETER") kind: String,
        box: String,
    ) {
        // When
        val graph = graph("com.example.Payload", box, payload)

        // Then
        graph.nodes.keys.map { it.value } shouldContainExactlyInAnyOrder
            listOf(
                "com.example.Payload",
                "com.example.Box<java.lang.String>",
                "com.example.Box<java.lang.Integer>",
                "com.example.Box<java.util.List<java.lang.Integer>>",
            )
        graph.valueOf("com.example.Box<java.lang.String>") shouldBe string
        graph.valueOf("com.example.Box<java.lang.Integer>") shouldBe
            TypeRef.Inline(PrimitiveNode(PrimitiveKind.INT))
        graph.valueOf("com.example.Box<java.util.List<java.lang.Integer>>") shouldBe
            TypeRef.Inline(ListNode(TypeRef.Inline(PrimitiveNode(PrimitiveKind.INT))))
    }

    @Test
    fun `should name applied definitions after their id`() {
        // When
        val graph = graph("com.example.Payload", record, payload)

        // Then
        (graph.nodes.getValue(TypeId("com.example.Box<java.lang.Integer>")) as ObjectNode).name shouldBe
            "com.example.Box<java.lang.Integer>"
    }

    @Test
    fun `should not depend on component discovery order`() {
        // When
        val forward = graph("com.example.Payload", record, payload)
        val reversed = graph("com.example.ReversedPayload", record, reversedPayload)

        // Then
        reversed.nodes.filterKeys { "Box<" in it.value } shouldBe forward.nodes.filterKeys { "Box<" in it.value }
    }

    @Test
    fun `should resolve recursive generic classes against their own application`() {
        // Given
        val node =
            // language=java
            """
            package com.example;

            import java.util.List;

            public class Node<T> {
                public T value;
                public List<Node<T>> children;
            }
            """.trimIndent()
        val tree =
            // language=java
            """
            package com.example;

            public record Tree(Node<String> root) {}
            """.trimIndent()

        // When
        val graph = graph("com.example.Tree", node, tree)

        // Then
        val id = "com.example.Node<java.lang.String>"
        (graph.nodes.getValue(TypeId(id)) as ObjectNode).properties.map { it.name to it.type } shouldBe
            listOf(
                "value" to string,
                "children" to TypeRef.Inline(ListNode(TypeRef.Ref(TypeId(id)))),
            )
    }

    @Test
    fun `should treat a wildcard argument as any value`() {
        // Given
        val starred =
            // language=java
            """
            package com.example;

            public record Starred(Box<?> any) {}
            """.trimIndent()

        // When
        val graph = graph("com.example.Starred", record, starred)

        // Then
        graph.valueOf("com.example.Box") shouldBe TypeRef.Inline(AnyNode())
    }

    @Test
    fun `should resolve bounded wildcard arguments to their bound`() {
        // Given
        val wildcards =
            // language=java
            """
            package com.example;

            public record Wildcards(Box<? extends Number> upper, Box<? super Integer> lower) {}
            """.trimIndent()

        // When
        val graph = graph("com.example.Wildcards", record, wildcards)

        // Then
        graph.valueOf("com.example.Box<java.lang.Number>") shouldBe TypeRef.Ref(TypeId("java.lang.Number"))
        graph.valueOf("com.example.Box<java.lang.Integer>") shouldBe
            TypeRef.Inline(PrimitiveNode(PrimitiveKind.INT))
    }

    @Test
    fun `should stop at a bounded nesting of a polymorphically recursive class`() {
        // Given
        val skew =
            // language=java
            """
            package com.example;

            import java.util.List;

            public class Skew<T> {
                public T value;
                public Skew<List<T>> next;
            }
            """.trimIndent()
        val skewRoot =
            // language=java
            """
            package com.example;

            public record SkewRoot(Skew<Integer> skew) {}
            """.trimIndent()

        // When
        val graph = graph("com.example.SkewRoot", skew, skewRoot)

        // Then
        graph.nodes.keys.count { it.value.startsWith("com.example.Skew<") } shouldBe 8
    }
}
