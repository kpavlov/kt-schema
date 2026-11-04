package me.kpavlov.kt.schema.generator.core.ir

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import me.kpavlov.kt.schema.generator.core.ExperimentalSchemaGeneratorApi
import me.kpavlov.kt.schema.generator.core.InternalSchemaGeneratorApi
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

@OptIn(InternalSchemaGeneratorApi::class, ExperimentalSchemaGeneratorApi::class)
class TypeRefTest {
    private val node = PrimitiveNode(PrimitiveKind.INT)
    private val id = TypeId("pkg.Thing")
    private val bounds = listOf(Constraint.Minimum(Literal.Integer(1)), Constraint.Maximum(Literal.Integer(9)))

    @Test
    fun `constraints default to empty`() {
        TypeRef.Inline(node).constraints.shouldBeEmpty()
        TypeRef.Ref(id).constraints.shouldBeEmpty()
    }

    @Test
    fun `withConstraints keeps node and nullability of an inline ref`() {
        val ref = TypeRef.Inline(node, nullable = true).withConstraints(bounds)

        assertSoftly(ref as TypeRef.Inline) {
            this.node shouldBe this@TypeRefTest.node
            nullable shouldBe true
            constraints shouldBe bounds
        }
    }

    @Test
    fun `withConstraints keeps id and nullability of a named ref`() {
        val ref = TypeRef.Ref(id, nullable = true).withConstraints(bounds)

        assertSoftly(ref as TypeRef.Ref) {
            this.id shouldBe this@TypeRefTest.id
            nullable shouldBe true
            constraints shouldBe bounds
        }
    }

    @Test
    fun `withNullable keeps constraints`() {
        val ref = TypeRef.Inline(node, constraints = bounds).withNullable(true)

        ref.constraints shouldBe bounds
    }

    @ParameterizedTest
    @CsvSource(
        ", ",
        "-1, ",
        ", -1",
        "5, 2",
    )
    fun `Size rejects empty, negative and inverted bounds`(
        min: Int?,
        max: Int?,
    ) {
        shouldThrow<IllegalArgumentException> { Constraint.Size(min, max) }
    }
}
