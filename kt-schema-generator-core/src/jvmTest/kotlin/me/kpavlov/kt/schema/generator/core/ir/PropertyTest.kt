package me.kpavlov.kt.schema.generator.core.ir

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import me.kpavlov.kt.schema.generator.core.InternalSchemaGeneratorApi
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.math.BigDecimal
import java.math.BigInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.stream.Stream

@OptIn(InternalSchemaGeneratorApi::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PropertyTest {
    private val stringType = TypeRef.Inline(PrimitiveNode(PrimitiveKind.STRING))

    enum class Color { RED, BLUE }

    //region isPresenceRequired

    data class PresenceCase(
        val optional: Boolean,
        val value: PropertyValue,
        val expected: Boolean,
    )

    fun presenceCases(): Stream<PresenceCase> =
        Stream.of(
            PresenceCase(optional = false, PropertyValue.None, expected = true),
            PresenceCase(optional = true, PropertyValue.None, expected = false),
            PresenceCase(optional = false, PropertyValue.Default(Literal.Str("a")), expected = false),
            PresenceCase(optional = true, PropertyValue.UnknownDefault, expected = false),
            PresenceCase(optional = true, PropertyValue.Const(Literal.Str("x")), expected = true),
            PresenceCase(optional = false, PropertyValue.Const(Literal.Str("x")), expected = true),
        )

    @ParameterizedTest
    @MethodSource("presenceCases")
    fun `isPresenceRequired should follow the base presence rule`(case: PresenceCase) {
        // Arrange
        val property = Property(name = "x", type = stringType, optional = case.optional, value = case.value)

        // Act & Assert
        property.isPresenceRequired() shouldBe case.expected
    }

    //endregion

    //region toLiteral

    fun literalCases(): Stream<Pair<Any?, Literal?>> =
        Stream.of(
            null to Literal.Null,
            true to Literal.Bool(true),
            42 to Literal.Integer(42),
            Long.MAX_VALUE to Literal.Integer(Long.MAX_VALUE),
            1.5 to Literal.Decimal(1.5),
            0.1f to Literal.Decimal(0.1),
            "s" to Literal.Str("s"),
            'c' to Literal.Str("c"),
            Color.RED to Literal.Str("RED"),
            listOf(Color.BLUE) to Literal.ListOf(listOf(Literal.Str("BLUE"))),
            listOf(1, "a") to Literal.ListOf(listOf(Literal.Integer(1), Literal.Str("a"))),
            mapOf("k" to 1) to Literal.MapOf(mapOf("k" to Literal.Integer(1))),
            Double.NaN to null,
            Float.POSITIVE_INFINITY to null,
            BigInteger("9007199254740993") to Literal.Integer(9007199254740993),
            BigInteger("99999999999999999999") to null,
            BigInteger("-99999999999999999999") to null,
            BigDecimal("1.5") to Literal.Decimal(1.5),
            BigDecimal("1E+3") to Literal.Decimal(1000.0),
            AtomicLong(7) to Literal.Integer(7),
            Any() to null,
        )

    @ParameterizedTest
    @MethodSource("literalCases")
    fun `toLiteral should convert Kotlin values`(case: Pair<Any?, Literal?>) {
        case.first.toLiteral() shouldBe case.second
    }

    @Test
    fun `toLiteral should use the supplied enum entry name`() {
        // Act
        val literal = listOf(Color.RED).toLiteral { "entry-${it.name.lowercase()}" }

        // Assert
        literal shouldBe Literal.ListOf(listOf(Literal.Str("entry-red")))
    }

    @Test
    fun `toLiteral should use the supplied enum entry name for map keys`() {
        // Act
        val literal = mapOf(Color.RED to 1).toLiteral { "red" }

        // Assert
        literal shouldBe Literal.MapOf(mapOf("red" to Literal.Integer(1)))
    }

    @Test
    fun `toLiteral should drop unconvertible list elements`() {
        listOf("a", Any()).toLiteral() shouldBe Literal.ListOf(listOf(Literal.Str("a")))
    }

    @Test
    fun `toKotlinValue should restore the Kotlin value`() {
        // Arrange
        val literal =
            Literal.MapOf(
                mapOf(
                    "n" to Literal.Null,
                    "l" to Literal.ListOf(listOf(Literal.Integer(1), Literal.Decimal(2.5))),
                ),
            )

        // Act & Assert
        literal.toKotlinValue() shouldBe mapOf("n" to null, "l" to listOf(1L, 2.5))
    }

    //endregion

    @Test
    fun `Property can be created from name and type only`() {
        val property = Property(name = "x", type = stringType)

        assertSoftly(property) {
            optional shouldBe false
            value shouldBe PropertyValue.None
        }
    }
}
