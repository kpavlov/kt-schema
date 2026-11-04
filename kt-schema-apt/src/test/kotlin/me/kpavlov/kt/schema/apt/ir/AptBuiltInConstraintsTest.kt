package me.kpavlov.kt.schema.apt.ir

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
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

@OptIn(ExperimentalSchemaGeneratorApi::class)
class AptBuiltInConstraintsTest {
    @ParameterizedTest(name = "{0} is {1} bounded to {2}..{3}")
    @CsvSource(
        "byte, INT, -128, 127",
        "java.lang.Byte, INT, -128, 127",
        "short, INT, -32768, 32767",
        "java.lang.Short, INT, -32768, 32767",
    )
    fun `should bound byte and short`(
        javaType: String,
        kind: PrimitiveKind,
        min: Long,
        max: Long,
    ) {
        assertSoftly(scalarRef(javaType)) {
            node shouldBe PrimitiveNode(kind)
            constraints shouldContainExactly
                listOf(Constraint.Minimum(Literal.Integer(min)), Constraint.Maximum(Literal.Integer(max)))
        }
    }

    @ParameterizedTest(name = "{0} is a single character")
    @CsvSource("char", "java.lang.Character")
    fun `should bound char to one character`(javaType: String) {
        assertSoftly(scalarRef(javaType)) {
            node shouldBe PrimitiveNode(PrimitiveKind.STRING)
            constraints shouldContainExactly listOf(Constraint.Size(min = 1, max = 1))
        }
    }

    @ParameterizedTest(name = "{0} is a string with format {1}")
    @CsvSource(
        "java.util.UUID, uuid",
        "java.time.Instant, date-time",
        "java.time.OffsetDateTime, date-time",
        "java.time.ZonedDateTime, date-time",
        "java.util.Date, date-time",
        "java.time.LocalDate, date",
        "java.time.LocalTime, time",
        "java.time.OffsetTime, time",
    )
    fun `should map UUID and date types to formatted strings`(
        javaType: String,
        format: String,
    ) {
        assertSoftly(scalarRef(javaType)) {
            node shouldBe PrimitiveNode(PrimitiveKind.STRING)
            constraints shouldContainExactly listOf(Constraint.Format(format))
        }
    }

    @ParameterizedTest(name = "{0} is an unconstrained {1}")
    @CsvSource("int, INT", "java.lang.Integer, INT", "java.time.LocalDateTime, STRING")
    fun `should leave other types unconstrained`(
        javaType: String,
        kind: PrimitiveKind,
    ) {
        assertSoftly(scalarRef(javaType)) {
            node shouldBe PrimitiveNode(kind)
            constraints shouldBe emptyList()
        }
    }

    private fun scalarRef(javaType: String): TypeRef.Inline =
        graph(
            root = "com.example.Scalars",
            // language=java
            """
            package com.example;

            public record Scalars($javaType value) {}
            """.trimIndent(),
        ).let { graph ->
            graph.nodes
                .getValue(graph.root.shouldBeInstanceOf<TypeRef.Ref>().id)
                .shouldBeInstanceOf<ObjectNode>()
                .properties
                .single()
                .type
                .shouldBeInstanceOf<TypeRef.Inline>()
        }
}
