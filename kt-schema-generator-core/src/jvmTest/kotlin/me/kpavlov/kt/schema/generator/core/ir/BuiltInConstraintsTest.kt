package me.kpavlov.kt.schema.generator.core.ir

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import me.kpavlov.kt.schema.generator.core.InternalSchemaGeneratorApi
import me.kpavlov.kt.schema.generator.core.defaultPrimitiveTypeKinds
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import kotlin.test.Test

@OptIn(InternalSchemaGeneratorApi::class)
class BuiltInConstraintsTest {
    @ParameterizedTest(name = "{0} is bounded to {1}..{2}")
    @CsvSource(
        "kotlin.Byte, -128, 127",
        "java.lang.Byte, -128, 127",
        "kotlin.Short, -32768, 32767",
        "java.lang.Short, -32768, 32767",
        "kotlin.UByte, 0, 255",
        "kotlin.UShort, 0, 65535",
        "kotlin.UInt, 0, 4294967295",
    )
    fun `should bound small and unsigned integer types`(
        name: String,
        min: Long,
        max: Long,
    ) {
        builtInConstraintsFor(name) shouldContainExactly
            listOf(Constraint.Minimum(Literal.Integer(min)), Constraint.Maximum(Literal.Integer(max)))
    }

    @Test
    fun `should bound ULong from below only`() {
        builtInConstraintsFor("kotlin.ULong") shouldContainExactly listOf(Constraint.Minimum(Literal.Integer(0)))
    }

    @ParameterizedTest(name = "{0} is a single character")
    @CsvSource("kotlin.Char", "java.lang.Character")
    fun `should bound Char to one character`(name: String) {
        builtInConstraintsFor(name) shouldContainExactly listOf(Constraint.Size(min = 1, max = 1))
    }

    @ParameterizedTest(name = "{0} has format {1}")
    @CsvSource(
        "java.util.UUID, uuid",
        "kotlin.uuid.Uuid, uuid",
        "kotlin.time.Instant, date-time",
        "kotlinx.datetime.Instant, date-time",
        "java.time.Instant, date-time",
        "java.time.OffsetDateTime, date-time",
        "java.time.ZonedDateTime, date-time",
        "java.util.Date, date-time",
        "kotlinx.datetime.LocalDate, date",
        "java.time.LocalDate, date",
        "kotlinx.datetime.LocalTime, time",
        "java.time.LocalTime, time",
        "java.time.OffsetTime, time",
    )
    fun `should give UUID and date types a format`(
        name: String,
        format: String,
    ) {
        builtInConstraintsFor(name) shouldContainExactly listOf(Constraint.Format(format))
    }

    @ParameterizedTest(name = "{0} is unconstrained")
    @CsvSource("kotlin.Int", "kotlin.Long", "kotlin.String", "java.time.LocalDateTime", "com.example.Custom")
    fun `should leave other types unconstrained`(name: String) {
        builtInConstraintsFor(name).shouldBeEmpty()
    }

    @Test
    fun `should leave a missing name unconstrained`() {
        builtInConstraintsFor(null).shouldBeEmpty()
    }

    @ParameterizedTest(name = "{0} resolves to a string primitive")
    @CsvSource(
        "java.util.UUID",
        "kotlin.uuid.Uuid",
        "kotlin.time.Instant",
        "kotlinx.datetime.Instant",
        "java.time.Instant",
        "java.time.OffsetDateTime",
        "java.time.ZonedDateTime",
        "java.util.Date",
        "kotlinx.datetime.LocalDate",
        "java.time.LocalDate",
        "kotlinx.datetime.LocalTime",
        "java.time.LocalTime",
        "java.time.OffsetTime",
    )
    fun `every formatted type is a known string primitive`(name: String) {
        defaultPrimitiveTypeKinds()[name] shouldBe PrimitiveKind.STRING
    }
}
