package me.kpavlov.kt.schema.generator.reflect

import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import me.kpavlov.kt.schema.generator.core.ir.ObjectNode
import me.kpavlov.kt.schema.generator.core.ir.PrimitiveKind
import me.kpavlov.kt.schema.generator.core.ir.PrimitiveNode
import me.kpavlov.kt.schema.generator.core.ir.TypeRef
import kotlin.test.Test

class ReflectionIntrospectorNullabilityTest {
    // Local marker annotation matching the default `nullableAnnotationNames` config ("Nullable")
    // by simple name — mirrors javax.annotation.Nullable, jakarta.annotation.Nullable, etc.
    @Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY, AnnotationTarget.VALUE_PARAMETER)
    annotation class Nullable

    // Type name matches the default `*Opt` glob pattern.
    data class EmailOpt(
        val value: String,
    )

    data class WithOptTypeName(
        val name: String,
        val email: EmailOpt,
    )

    data class WithNullableAnnotation(
        val name: String,
        @property:Nullable
        val phone: String,
    )

    private val introspector = ReflectionClassIntrospector

    @Test
    fun `type name matching Opt pattern is treated as nullable but stays required by default`() {
        val graph = introspector.introspect(WithOptTypeName::class)
        val rootRef = graph.root.shouldBeInstanceOf<TypeRef.Ref>()
        val node = graph.nodes[rootRef.id].shouldBeInstanceOf<ObjectNode>()

        // No default `introspector.optional.type.names` pattern — matching a nullable-by-convention
        // type name doesn't by itself exclude the property from `required`.
        node.required.shouldContainExactlyInAnyOrder(setOf("name", "email"))

        val props = node.properties.associateBy { it.name }
        props.getValue("email").apply {
            hasDefaultValue shouldBe false
            type.nullable shouldBe true
        }
    }

    @Test
    fun `Nullable annotated property is treated as nullable but remains required`() {
        val graph = introspector.introspect(WithNullableAnnotation::class)
        val rootRef = graph.root.shouldBeInstanceOf<TypeRef.Ref>()
        val node = graph.nodes[rootRef.id].shouldBeInstanceOf<ObjectNode>()

        node.required.shouldContainExactlyInAnyOrder(setOf("name", "phone"))

        val props = node.properties.associateBy { it.name }
        props.getValue("phone").apply {
            hasDefaultValue shouldBe false
            type.nullable shouldBe true
            type.shouldBeInstanceOf<TypeRef.Inline> { inline ->
                inline.node.shouldBeInstanceOf<PrimitiveNode> { prim ->
                    prim.kind shouldBe PrimitiveKind.STRING
                }
            }
        }
    }

    @Test
    fun `plain Kotlin nullable property without default is unaffected and stays required`() {
        data class WithPlainNullable(
            val name: String,
            val nickname: String?,
        )

        val graph = introspector.introspect(WithPlainNullable::class)
        val rootRef = graph.root.shouldBeInstanceOf<TypeRef.Ref>()
        val node = graph.nodes[rootRef.id].shouldBeInstanceOf<ObjectNode>()

        node.required.shouldContainExactlyInAnyOrder(setOf("name", "nickname"))

        val props = node.properties.associateBy { it.name }
        props.getValue("nickname").apply {
            hasDefaultValue shouldBe false
            type.nullable shouldBe true
        }
    }
}
