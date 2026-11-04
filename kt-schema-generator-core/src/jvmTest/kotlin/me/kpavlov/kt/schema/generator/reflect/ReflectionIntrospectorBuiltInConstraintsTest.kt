package me.kpavlov.kt.schema.generator.reflect

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import me.kpavlov.kt.schema.generator.core.ExperimentalSchemaGeneratorApi
import me.kpavlov.kt.schema.generator.core.ir.Constraint
import me.kpavlov.kt.schema.generator.core.ir.Literal
import me.kpavlov.kt.schema.generator.core.ir.ObjectNode
import me.kpavlov.kt.schema.generator.core.ir.PrimitiveKind
import me.kpavlov.kt.schema.generator.core.ir.PrimitiveNode
import me.kpavlov.kt.schema.generator.core.ir.TypeRef
import me.kpavlov.kt.schema.test.fixtures.BuiltInTypes
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.util.UUID
import kotlin.reflect.KClass
import kotlin.test.Test

@OptIn(ExperimentalSchemaGeneratorApi::class)
class ReflectionIntrospectorBuiltInConstraintsTest {
    @Suppress("unused")
    data class JavaTypes(
        val id: UUID,
        val at: Instant,
        val day: LocalDate,
        val clock: LocalTime,
        val stamp: OffsetDateTime,
        val local: LocalDateTime,
    )

    private val builtIns = inlineProperties(BuiltInTypes::class)
    private val javaTypes = inlineProperties(JavaTypes::class)

    @ParameterizedTest(name = "{0} is {1} bounded to {2}..{3}")
    @CsvSource(
        "tiny, INT, -128, 127",
        "small, INT, -32768, 32767",
        "optional, INT, -32768, 32767",
        "ubyte, INT, 0, 255",
        "ushort, INT, 0, 65535",
        "uint, INT, 0, 4294967295",
    )
    fun `should bound small and unsigned integers`(
        name: String,
        kind: PrimitiveKind,
        min: Long,
        max: Long,
    ) {
        assertSoftly(builtIns.getValue(name)) {
            node shouldBe PrimitiveNode(kind)
            constraints shouldContainExactly
                listOf(Constraint.Minimum(Literal.Integer(min)), Constraint.Maximum(Literal.Integer(max)))
        }
    }

    @Test
    fun `should bound ULong from below and keep nullability`() {
        assertSoftly(builtIns.getValue("ulong")) {
            node shouldBe PrimitiveNode(PrimitiveKind.LONG)
            constraints shouldContainExactly listOf(Constraint.Minimum(Literal.Integer(0)))
        }
        builtIns.getValue("optional").nullable shouldBe true
    }

    @Test
    fun `should bound Char to one character`() {
        assertSoftly(builtIns.getValue("letter")) {
            node shouldBe PrimitiveNode(PrimitiveKind.STRING)
            constraints shouldContainExactly listOf(Constraint.Size(min = 1, max = 1))
        }
    }

    @ParameterizedTest(name = "{0} is a string with format {1}")
    @CsvSource("uid, uuid", "at, date-time")
    fun `should map Kotlin UUID and Instant to formatted strings`(
        name: String,
        format: String,
    ) {
        assertSoftly(builtIns.getValue(name)) {
            node shouldBe PrimitiveNode(PrimitiveKind.STRING)
            constraints shouldContainExactly listOf(Constraint.Format(format))
        }
    }

    @ParameterizedTest(name = "{0} is a string with format {1}")
    @CsvSource("id, uuid", "at, date-time", "stamp, date-time", "day, date", "clock, time")
    fun `should map Java UUID and date types to formatted strings`(
        name: String,
        format: String,
    ) {
        assertSoftly(javaTypes.getValue(name)) {
            node shouldBe PrimitiveNode(PrimitiveKind.STRING)
            constraints shouldContainExactly listOf(Constraint.Format(format))
        }
    }

    @Test
    fun `should leave Int and LocalDateTime unconstrained`() {
        assertSoftly(builtIns.getValue("count")) {
            node shouldBe PrimitiveNode(PrimitiveKind.INT)
            constraints shouldBe emptyList()
        }
        assertSoftly(javaTypes.getValue("local")) {
            node shouldBe PrimitiveNode(PrimitiveKind.STRING)
            constraints shouldBe emptyList()
        }
    }

    private fun inlineProperties(klass: KClass<*>): Map<String, TypeRef.Inline> =
        ReflectionClassIntrospector
            .introspect(klass)
            .let { graph -> graph.nodes.getValue(graph.root.shouldBeInstanceOf<TypeRef.Ref>().id) as ObjectNode }
            .properties
            .associate { it.name to it.type.shouldBeInstanceOf<TypeRef.Inline>() }
}
