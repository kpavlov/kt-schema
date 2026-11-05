package me.kpavlov.kt.schema.generator.core.ir

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
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
        val args = listOf<Pair<String, Any?>>("value" to 3L)

        Introspections.getConstraintsFromAnnotation("Min", "jakarta.validation.constraints.Min") { args }
            .shouldContainExactly(expected)
        Introspections.getConstraintsFromAnnotation("Min", "javax.validation.constraints.Min") { args }
            .shouldContainExactly(expected)
    }

    @Test
    fun `should map a simple name regardless of case`() {
        validationConstraintsFor("min", listOf("value" to 3L)) shouldContainExactly
            listOf(Constraint.Minimum(Literal.Integer(3)))
    }

    @Test
    fun `should let an exclusive bound win a tie against an equal decimal bound`() {
        val inclusiveZero = Constraint.Minimum(Literal.Decimal(0.0))
        val positive = Constraint.Minimum(Literal.Integer(0), exclusive = true)
        val ref = TypeRef.Inline(PrimitiveNode(PrimitiveKind.DOUBLE))

        ref.withAddedConstraints(listOf(inclusiveZero, positive)).constraints shouldContainExactly listOf(positive)
        ref.withAddedConstraints(listOf(positive, inclusiveZero)).constraints shouldContainExactly listOf(positive)
    }

    @Test
    fun `should ignore a non-finite decimal bound`() {
        validationConstraintsFor("DecimalMin", listOf("value" to "1e400")).shouldBeEmpty()
    }

    @Test
    fun `should keep an inverted Size as two one-sided bounds`() {
        validationConstraintsFor("Size", listOf("min" to 5, "max" to 2))
            .shouldContainExactly(Constraint.Size(min = 5), Constraint.Size(max = 2))
    }

    @Test
    fun `should ignore a negative Size bound`() {
        validationConstraintsFor("Size", listOf("min" to -1, "max" to -1)).shouldBeEmpty()
        validationConstraintsFor("Size", listOf("min" to 2, "max" to -1)).shouldContainExactly(Constraint.Size(min = 2))
    }

    @Test
    fun `should skip a Pattern with flags`() {
        validationConstraintsFor("Pattern", listOf("regexp" to "[a-z]+", "flags" to listOf("CASE_INSENSITIVE")))
            .shouldBeEmpty()
        validationConstraintsFor("Pattern", listOf("regexp" to "[a-z]+", "flags" to arrayOf("CASE_INSENSITIVE")))
            .shouldBeEmpty()
        validationConstraintsFor("Pattern", listOf("regexp" to "[a-z]+", "flags" to emptyList<String>()))
            .shouldContainExactly(Constraint.Pattern("[a-z]+"))
    }

    @Test
    fun `should keep contradictory sizes as one-sided bounds and describe them`() {
        val char =
            TypeRef.Inline(PrimitiveNode(PrimitiveKind.STRING), constraints = builtInConstraintsFor("kotlin.Char"))

        val merged = char.withAddedConstraints("Grade.letter") { listOf(Constraint.Size(min = 2)) }.constraints

        merged shouldContainExactly listOf(Constraint.Size(min = 2), Constraint.Size(max = 1))
        merged.contradiction(integer = false) shouldBe "Size min 2 exceeds max 1"
    }

    fun unsatisfiableBounds(): List<Arguments> =
        listOf(
            Arguments.of(
                Constraint.Minimum(Literal.Integer(200)),
                Constraint.Maximum(Literal.Integer(127)),
                "minimum 200, maximum 127",
            ),
            Arguments.of(
                Constraint.Minimum(Literal.Decimal(1.5)),
                Constraint.Maximum(Literal.Integer(1)),
                "minimum 1.5, maximum 1",
            ),
            Arguments.of(
                Constraint.Minimum(Literal.Integer(0), exclusive = true),
                Constraint.Maximum(Literal.Integer(0)),
                "exclusive minimum 0, maximum 0",
            ),
            Arguments.of(
                Constraint.Minimum(Literal.Integer(0)),
                Constraint.Maximum(Literal.Decimal(0.0), exclusive = true),
                "minimum 0, exclusive maximum 0.0",
            ),
        )

    @ParameterizedTest(name = "{2}")
    @MethodSource("unsatisfiableBounds")
    fun `should keep numeric bounds that exclude every value and describe them`(
        minimum: Constraint,
        maximum: Constraint,
        bounds: String,
    ) {
        val ref = TypeRef.Inline(PrimitiveNode(PrimitiveKind.INT), constraints = listOf(maximum))

        val merged = ref.withAddedConstraints("Item.level") { listOf(minimum) }.constraints

        merged shouldContainExactly listOf(maximum, minimum)
        merged.contradiction(integer = true) shouldBe "Bounds exclude every value: $bounds"
    }

    fun integerFreeBounds(): List<Arguments> =
        listOf(
            Arguments.of(
                Constraint.Minimum(Literal.Integer(0), exclusive = true),
                Constraint.Maximum(Literal.Integer(1), exclusive = true),
            ),
            Arguments.of(Constraint.Minimum(Literal.Decimal(0.5)), Constraint.Maximum(Literal.Decimal(0.9))),
            Arguments.of(
                Constraint.Minimum(Literal.Decimal(1.0), exclusive = true),
                Constraint.Maximum(Literal.Decimal(2.0), exclusive = true),
            ),
        )

    @ParameterizedTest(name = "{0} .. {1}")
    @MethodSource("integerFreeBounds")
    fun `should flag bounds that contain no integer on an integer type only`(
        minimum: Constraint,
        maximum: Constraint,
    ) {
        val bounds = listOf(maximum, minimum)

        bounds.contradiction(integer = true).shouldNotBeNull()
        bounds.contradiction(integer = false).shouldBeNull()
    }

    @Test
    fun `should accept an integer range with one whole value`() {
        val bounds =
            listOf(Constraint.Minimum(Literal.Decimal(0.5)), Constraint.Maximum(Literal.Integer(2), exclusive = true))

        bounds.contradiction(integer = true).shouldBeNull()
    }

    @Test
    fun `should treat signed zeros as equal bounds`() {
        val inclusive = listOf(Constraint.Minimum(Literal.Decimal(0.0)), Constraint.Maximum(Literal.Decimal(-0.0)))
        val exclusive = inclusive.map { if (it is Constraint.Minimum) it.copy(exclusive = true) else it }

        inclusive.contradiction(integer = false).shouldBeNull()
        exclusive.contradiction(integer = false).shouldNotBeNull()
    }

    @Test
    fun `should compare a whole decimal bound with an integer bound exactly`() {
        val bounds =
            listOf(
                Constraint.Minimum(Literal.Decimal(9007199254740992.0), exclusive = true),
                Constraint.Maximum(Literal.Integer(9007199254740993)),
            )

        bounds.contradiction(integer = true).shouldBeNull()
    }

    fun decimalIntegerBounds(): List<Arguments> =
        listOf(
            Arguments.of(9007199254740992.0, true, 9007199254740992L, true, false),
            Arguments.of(9007199254740992.0, true, 9007199254740993L, true, true),
            Arguments.of(-9007199254740992.0, true, -9007199254740992L, false, false),
            Arguments.of(-9007199254740992.0, true, -9007199254740993L, false, true),
            Arguments.of(-9223372036854775808.0, true, Long.MIN_VALUE, true, false),
            Arguments.of(-9223372036854775808.0, true, Long.MIN_VALUE + 1, true, true),
            Arguments.of(-9223372036854775808.0, true, Long.MIN_VALUE, false, false),
            Arguments.of(9223372036854775808.0, true, Long.MAX_VALUE, true, false),
            Arguments.of(9223372036854775808.0, true, Long.MAX_VALUE, false, true),
            Arguments.of(-1e20, true, Long.MIN_VALUE, true, true),
            Arguments.of(-1e20, true, Long.MIN_VALUE, false, false),
            Arguments.of(1e20, true, Long.MAX_VALUE, true, false),
            Arguments.of(1e20, true, Long.MAX_VALUE, false, true),
            Arguments.of(1.5, true, 2L, true, true),
            Arguments.of(1.5, true, 1L, false, true),
            Arguments.of(2.0, false, 2L, true, true),
            Arguments.of(2.0, false, 2L, false, true),
        )

    @ParameterizedTest(name = "decimal {0}, exclusive {1}, integer {2}, minimum {3}, satisfiable {4}")
    @MethodSource("decimalIntegerBounds")
    fun `should find integer values admitted by decimal bounds`(
        decimal: Double,
        exclusive: Boolean,
        integer: Long,
        decimalIsMinimum: Boolean,
        satisfiable: Boolean,
    ) {
        val bounds =
            if (decimalIsMinimum) {
                listOf(
                    Constraint.Minimum(Literal.Decimal(decimal), exclusive),
                    Constraint.Maximum(Literal.Integer(integer)),
                )
            } else {
                listOf(
                    Constraint.Minimum(Literal.Integer(integer)),
                    Constraint.Maximum(Literal.Decimal(decimal), exclusive),
                )
            }

        (bounds.contradiction(integer = true) == null) shouldBe satisfiable
    }

    @Test
    fun `should keep contradictory bounds on a nullable type without a warning`() {
        val byte = nullableRef(PrimitiveKind.INT, "kotlin.Byte")
        val char = nullableRef(PrimitiveKind.STRING, "kotlin.Char")
        val min200 = Constraint.Minimum(Literal.Integer(200))

        byte.withAddedConstraints(listOf(min200)).constraints shouldContainExactly
            listOf(Constraint.Maximum(Literal.Integer(127)), min200)
        char.withAddedConstraints(listOf(Constraint.Size(min = 2, max = 3))).constraints shouldContainExactly
            listOf(Constraint.Size(min = 2), Constraint.Size(max = 1))
    }

    @Test
    fun `should keep intersecting sizes on a nullable type as one-sided bounds`() {
        val char = nullableRef(PrimitiveKind.STRING, "kotlin.Char")

        val twice =
            char
                .withAddedConstraints(listOf(Constraint.Size(min = 2)))
                .withAddedConstraints(listOf(Constraint.Size(max = 5)))

        twice.constraints shouldContainExactly listOf(Constraint.Size(min = 2), Constraint.Size(max = 1))
    }

    @Test
    fun `should flag exclusive integer bounds at the Long limits`() {
        val aboveMax = Constraint.Minimum(Literal.Integer(Long.MAX_VALUE), exclusive = true)
        val belowMin = Constraint.Maximum(Literal.Integer(Long.MIN_VALUE), exclusive = true)
        val hugeDecimal = Constraint.Minimum(Literal.Decimal(1e20))
        val atMostMax = Constraint.Maximum(Literal.Integer(Long.MAX_VALUE))
        val atLeastMin = Constraint.Minimum(Literal.Integer(Long.MIN_VALUE))

        listOf(aboveMax, atMostMax).contradiction(integer = true).shouldNotBeNull()
        listOf(atLeastMin, belowMin).contradiction(integer = true).shouldNotBeNull()
        listOf(hugeDecimal, atMostMax).contradiction(integer = true).shouldNotBeNull()
    }

    @Test
    fun `should accept equal inclusive numeric bounds`() {
        val bounds = listOf(Constraint.Minimum(Literal.Integer(5)), Constraint.Maximum(Literal.Integer(5)))

        bounds.contradiction(integer = true).shouldBeNull()
    }

    @Test
    fun `should not read the arguments of an unrecognized annotation`() {
        val constraints =
            Introspections.getConstraintsFromAnnotation("Min", "com.example.Min") {
                error("arguments must stay unread")
            }

        constraints.shouldBeEmpty()
    }

    @Test
    fun `should drop a duplicate constraint`() {
        val pattern = Constraint.Pattern("[a-z]+")
        val ref = TypeRef.Inline(PrimitiveNode(PrimitiveKind.STRING), constraints = listOf(pattern))

        ref.withAddedConstraints(listOf(pattern, pattern)).constraints shouldContainExactly listOf(pattern)
    }

    @Test
    fun `should compare integer bounds exactly above 2^53`() {
        val lower = Constraint.Minimum(Literal.Integer(9007199254740992))
        val higher = Constraint.Minimum(Literal.Integer(9007199254740993))
        val ref = TypeRef.Inline(PrimitiveNode(PrimitiveKind.LONG), constraints = listOf(lower))

        ref.withAddedConstraints(listOf(higher)).constraints shouldContainExactly listOf(higher)
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
        Introspections.getConstraintsFromAnnotation("Min", "com.example.Min") { listOf("value" to 3L) }.shouldBeEmpty()
        Introspections.getConstraintsFromAnnotation("Min", null) { listOf("value" to 3L) }.shouldBeEmpty()
    }

    private fun nullableRef(
        kind: PrimitiveKind,
        typeName: String,
    ) = TypeRef.Inline(PrimitiveNode(kind), nullable = true, builtInConstraintsFor(typeName))
}
