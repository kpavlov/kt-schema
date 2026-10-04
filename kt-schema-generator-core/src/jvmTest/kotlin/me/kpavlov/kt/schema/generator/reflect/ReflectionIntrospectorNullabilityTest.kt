package me.kpavlov.kt.schema.generator.reflect

import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import me.kpavlov.kt.schema.generator.core.ir.Literal
import me.kpavlov.kt.schema.generator.core.ir.ObjectNode
import me.kpavlov.kt.schema.generator.core.ir.PrimitiveKind
import me.kpavlov.kt.schema.generator.core.ir.PrimitiveNode
import me.kpavlov.kt.schema.generator.core.ir.PropertyValue
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
        node.requiredNames().shouldContainExactlyInAnyOrder(setOf("name", "email"))

        val props = node.properties.associateBy { it.name }
        props.getValue("email").apply {
            optional shouldBe false
            type.nullable shouldBe true
        }
    }

    @Test
    fun `Nullable annotated property is treated as nullable but remains required`() {
        val graph = introspector.introspect(WithNullableAnnotation::class)
        val rootRef = graph.root.shouldBeInstanceOf<TypeRef.Ref>()
        val node = graph.nodes[rootRef.id].shouldBeInstanceOf<ObjectNode>()

        node.requiredNames().shouldContainExactlyInAnyOrder(setOf("name", "phone"))

        val props = node.properties.associateBy { it.name }
        props.getValue("phone").apply {
            optional shouldBe false
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

        node.requiredNames().shouldContainExactlyInAnyOrder(setOf("name", "nickname"))

        val props = node.properties.associateBy { it.name }
        props.getValue("nickname").apply {
            optional shouldBe false
            type.nullable shouldBe true
        }
    }

    //region Optional and value

    data class NullableWithNullDefault(
        val x: String? = null,
    )

    data class NullableWithoutDefault(
        val x: String?,
    )

    data class WithKotlinDefault(
        val x: String = "a",
    )

    data class WithNumberDefaults(
        val big: Long = Long.MAX_VALUE,
        val ratio: Float = 0.1f,
        val count: Int = 3,
    )

    @Test
    fun `nullable property with null default is optional with Default Null`() {
        val property = introspectProperty(NullableWithNullDefault::class, "x")

        property.optional shouldBe true
        property.value shouldBe PropertyValue.Default(Literal.Null)
    }

    @Test
    fun `nullable property without default is not optional and has no value`() {
        val property = introspectProperty(NullableWithoutDefault::class, "x")

        property.optional shouldBe false
        property.value shouldBe PropertyValue.None
    }

    @Test
    fun `property with Kotlin default is optional with Default literal`() {
        val property = introspectProperty(WithKotlinDefault::class, "x")

        property.optional shouldBe true
        property.value shouldBe PropertyValue.Default(Literal.Str("a"))
    }

    @Test
    fun `number defaults keep integer and decimal literals`() {
        val node = introspectObject(WithNumberDefaults::class)

        node.properties.associate { it.name to it.value } shouldBe
            mapOf(
                "big" to PropertyValue.Default(Literal.Integer(Long.MAX_VALUE)),
                "ratio" to PropertyValue.Default(Literal.Decimal(0.1)),
                "count" to PropertyValue.Default(Literal.Integer(3)),
            )
    }

    private fun introspectObject(klass: kotlin.reflect.KClass<*>): ObjectNode {
        val graph = introspector.introspect(klass)
        val rootRef = graph.root.shouldBeInstanceOf<TypeRef.Ref>()
        return graph.nodes[rootRef.id].shouldBeInstanceOf<ObjectNode>()
    }

    private fun introspectProperty(
        klass: kotlin.reflect.KClass<*>,
        name: String,
    ) = introspectObject(klass).properties.first { it.name == name }

    //endregion
}
