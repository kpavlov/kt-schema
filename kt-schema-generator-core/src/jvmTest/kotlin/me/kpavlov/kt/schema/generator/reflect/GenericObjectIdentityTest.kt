package me.kpavlov.kt.schema.generator.reflect

import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import me.kpavlov.kt.schema.generator.core.ir.AnyNode
import me.kpavlov.kt.schema.generator.core.ir.ListNode
import me.kpavlov.kt.schema.generator.core.ir.MapNode
import me.kpavlov.kt.schema.generator.core.ir.ObjectNode
import me.kpavlov.kt.schema.generator.core.ir.PrimitiveKind
import me.kpavlov.kt.schema.generator.core.ir.PrimitiveNode
import me.kpavlov.kt.schema.generator.core.ir.TypeGraph
import me.kpavlov.kt.schema.generator.core.ir.TypeId
import me.kpavlov.kt.schema.generator.core.ir.TypeRef
import kotlin.test.Test

class GenericObjectIdentityTest {
    data class Box<T>(
        val value: T,
    )

    data class Payload(
        val text: Box<String>,
        val count: Box<Int>,
        val nullableText: Box<String?>,
    )

    data class ReversedPayload(
        val nullableText: Box<String?>,
        val count: Box<Int>,
        val text: Box<String>,
    )

    data class Repeated(
        val first: Box<List<Int>>,
        val second: Box<List<Int>>,
    )

    data class Node<T>(
        val value: T,
        val children: List<Node<T>>,
    )

    data class Tree(
        val root: Node<String>,
    )

    data class Starred(
        val any: Box<*>,
    )

    data class Skew<T>(
        val value: T,
        val next: Skew<List<T>>?,
    )

    data class SkewRoot(
        val skew: Skew<Int>,
    )

    @JvmInline
    value class Index<T>(
        val byKey: Map<T, Index<T>>,
    )

    data class Indexes(
        val ints: Index<Int>,
        val strings: Index<String>,
    )

    private val indexName = requireNotNull(Index::class.qualifiedName)
    private val boxName = requireNotNull(Box::class.qualifiedName)
    private val nodeName = requireNotNull(Node::class.qualifiedName)
    private val skewName = requireNotNull(Skew::class.qualifiedName)

    private fun TypeGraph.objectNode(id: String): ObjectNode = nodes.getValue(TypeId(id)) as ObjectNode

    private fun TypeGraph.definitionsOf(prefix: String): Map<TypeId, Any> =
        nodes.filterKeys { it.value.startsWith("$prefix<") }

    private val string = TypeRef.Inline(PrimitiveNode(PrimitiveKind.STRING))

    @Test
    fun `should keep a definition per applied argument`() {
        // When
        val graph = ReflectionClassIntrospector.introspect(Payload::class)

        // Then
        graph.nodes.keys.map { it.value } shouldContainExactlyInAnyOrder
            listOf(
                requireNotNull(Payload::class.qualifiedName),
                "$boxName<kotlin.String>",
                "$boxName<kotlin.Int>",
                "$boxName<kotlin.String?>",
            )
        graph.objectNode("$boxName<kotlin.String>").properties.single().type shouldBe string
        graph.objectNode("$boxName<kotlin.Int>").properties.single().type shouldBe
            TypeRef.Inline(PrimitiveNode(PrimitiveKind.INT))
        graph.objectNode("$boxName<kotlin.String?>").properties.single().type shouldBe
            TypeRef.Inline(PrimitiveNode(PrimitiveKind.STRING), nullable = true)
    }

    @Test
    fun `should name applied definitions after their id`() {
        // When
        val graph = ReflectionClassIntrospector.introspect(Payload::class)

        // Then
        graph.objectNode("$boxName<kotlin.Int>").name shouldBe "$boxName<kotlin.Int>"
    }

    @Test
    fun `should not depend on property discovery order`() {
        // When
        val forward = ReflectionClassIntrospector.introspect(Payload::class)
        val reversed = ReflectionClassIntrospector.introspect(ReversedPayload::class)

        // Then
        reversed.definitionsOf(boxName) shouldBe forward.definitionsOf(boxName)
    }

    @Test
    fun `should reuse one definition for repeated applications with nested arguments`() {
        // When
        val graph = ReflectionClassIntrospector.introspect(Repeated::class)

        // Then
        val id = "$boxName<kotlin.collections.List<kotlin.Int>>"
        graph.definitionsOf(boxName).keys.map { it.value } shouldBe listOf(id)
        graph.objectNode(id).properties.single().type shouldBe
            TypeRef.Inline(ListNode(TypeRef.Inline(PrimitiveNode(PrimitiveKind.INT))))
    }

    @Test
    fun `should resolve recursive generic objects against their own application`() {
        // When
        val graph = ReflectionClassIntrospector.introspect(Tree::class)

        // Then
        val id = "$nodeName<kotlin.String>"
        graph.objectNode(id).properties.map { it.name to it.type } shouldBe
            listOf(
                "value" to string,
                "children" to TypeRef.Inline(ListNode(TypeRef.Ref(TypeId(id)))),
            )
    }

    @Test
    fun `should treat a star projection as any value`() {
        // When
        val graph = ReflectionClassIntrospector.introspect(Starred::class)

        // Then
        graph.objectNode(boxName).properties.single().type shouldBe TypeRef.Inline(AnyNode(), nullable = true)
    }

    @Test
    fun `should treat a generic root as any value`() {
        // When
        val graph = ReflectionClassIntrospector.introspect(Box::class)

        // Then
        graph.root shouldBe TypeRef.Ref(TypeId(boxName))
        graph.objectNode(boxName).properties.single().type shouldBe TypeRef.Inline(AnyNode(), nullable = true)
    }

    @Test
    fun `should stop at a bounded nesting of a polymorphically recursive class`() {
        // When
        val graph = ReflectionClassIntrospector.introspect(SkewRoot::class)

        // Then
        graph.definitionsOf(skewName).size shouldBe 8
    }

    @Test
    fun `should keep wrapped definitions of recursive generic value classes apart`() {
        // When
        val graph = ReflectionClassIntrospector.introspect(Indexes::class)

        // Then
        val intId = TypeId("$indexName<kotlin.Int>")
        val stringId = TypeId("$indexName<kotlin.String>")
        graph.nodes.getValue(intId) shouldBe
            MapNode(TypeRef.Inline(PrimitiveNode(PrimitiveKind.INT)), TypeRef.Ref(intId))
        graph.nodes.getValue(stringId) shouldBe MapNode(string, TypeRef.Ref(stringId))
    }
}
