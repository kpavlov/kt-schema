package me.kpavlov.kt.schema.generator.core.ir

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import me.kpavlov.kt.schema.generator.core.ExperimentalSchemaGeneratorApi
import me.kpavlov.kt.schema.generator.core.InternalSchemaGeneratorApi
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import kotlin.test.Test

@OptIn(InternalSchemaGeneratorApi::class, ExperimentalSchemaGeneratorApi::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ValidationConstraintsTest {
    private val noArgs = emptyList<Pair<String, Any?>>()

    fun cases(): List<Arguments> =
        listOf(
            Arguments.of("Min", listOf("value" to 1L), listOf(Constraint.Minimum(Literal.Integer(1)))),
            Arguments.of("Max", listOf("value" to 100), listOf(Constraint.Maximum(Literal.Integer(100)))),
            Arguments.of(
                "DecimalMin",
                listOf("value" to "0.5", "inclusive" to true),
                listOf(Constraint.Minimum(Literal.Decimal(0.5))),
            ),
            Arguments.of(
                "DecimalMax",
                listOf("value" to "10", "inclusive" to false),
                listOf(Constraint.Maximum(Literal.Integer(10), exclusive = true)),
            ),
            Arguments.of("Positive", noArgs, listOf(Constraint.Minimum(Literal.Integer(0), true))),
            Arguments.of("PositiveOrZero", noArgs, listOf(Constraint.Minimum(Literal.Integer(0)))),
            Arguments.of("Negative", noArgs, listOf(Constraint.Maximum(Literal.Integer(0), true))),
            Arguments.of("NegativeOrZero", noArgs, listOf(Constraint.Maximum(Literal.Integer(0)))),
            Arguments.of("Size", listOf("min" to 2, "max" to 5), listOf(Constraint.Size(min = 2, max = 5))),
            Arguments.of("Size", listOf("min" to 0, "max" to Int.MAX_VALUE), emptyList<Constraint>()),
            Arguments.of("Size", listOf("max" to 5), listOf(Constraint.Size(max = 5))),
            Arguments.of("NotEmpty", noArgs, listOf(Constraint.Size(min = 1))),
            Arguments.of("NotBlank", noArgs, listOf(Constraint.Size(min = 1))),
            Arguments.of("Pattern", listOf("regexp" to "[A-Z]+"), listOf(Constraint.Pattern("[A-Z]+"))),
            Arguments.of("Email", noArgs, listOf(Constraint.Format("email"))),
            Arguments.of("NotNull", noArgs, emptyList<Constraint>()),
        )

    @ParameterizedTest(name = "@{0}({1}) -> {2}")
    @MethodSource("cases")
    fun `should map Jakarta constraint annotations by simple name`(
        name: String,
        arguments: List<Pair<String, Any?>>,
        expected: List<Constraint>,
    ) {
        validationConstraintsFor(name, arguments) shouldContainExactly expected
    }

    @Test
    fun `should recognize the jakarta and legacy javax packages by default`() {
        val expected = listOf(Constraint.Minimum(Literal.Integer(3)))

        Introspections.getConstraintsFromAnnotation("Min", "jakarta.validation.constraints.Min", listOf("value" to 3L))
            .shouldContainExactly(expected)
        Introspections.getConstraintsFromAnnotation("Min", "javax.validation.constraints.Min", listOf("value" to 3L))
            .shouldContainExactly(expected)
    }

    @Test
    fun `should ignore a non-finite decimal bound`() {
        validationConstraintsFor("DecimalMin", listOf("value" to "1e400")).shouldBeEmpty()
    }

    @Test
    fun `should reject an inverted Size`() {
        shouldThrow<IllegalArgumentException> { validationConstraintsFor("Size", listOf("min" to 5, "max" to 2)) }
    }

    @Test
    fun `should keep the tighter Size whatever the order`() {
        val ref = TypeRef.Inline(PrimitiveNode(PrimitiveKind.STRING))
        val narrow = Constraint.Size(min = 2, max = 50)
        val notEmpty = Constraint.Size(min = 1)

        ref.withAddedConstraints(listOf(narrow, notEmpty)).constraints shouldContainExactly listOf(narrow)
        ref.withAddedConstraints(listOf(notEmpty, narrow)).constraints shouldContainExactly listOf(narrow)
    }

    @Test
    fun `should keep the tighter numeric bounds over built-in ones`() {
        val byte = TypeRef.Inline(PrimitiveNode(PrimitiveKind.INT), constraints = builtInConstraintsFor("kotlin.Byte"))

        byte.withAddedConstraints(listOf(Constraint.Minimum(Literal.Integer(0)))).constraints shouldContainExactly
            listOf(Constraint.Maximum(Literal.Integer(127)), Constraint.Minimum(Literal.Integer(0)))
        byte.withAddedConstraints(listOf(Constraint.Maximum(Literal.Integer(300)))).constraints shouldContainExactly
            listOf(Constraint.Minimum(Literal.Integer(-128)), Constraint.Maximum(Literal.Integer(127)))
        val exclusiveMin = Constraint.Minimum(Literal.Integer(-128), exclusive = true)
        byte.withAddedConstraints(listOf(exclusiveMin)).constraints shouldContainExactly
            listOf(Constraint.Maximum(Literal.Integer(127)), exclusiveMin)
    }

    @Test
    fun `should ignore same-named annotations from other packages`() {
        Introspections.getConstraintsFromAnnotation("Min", "com.example.Min", listOf("value" to 3L)).shouldBeEmpty()
        Introspections.getConstraintsFromAnnotation("Min", null, listOf("value" to 3L)).shouldBeEmpty()
    }
}
