package me.kpavlov.kt.schema.generator.core.ir

import me.kpavlov.kt.schema.generator.core.InternalSchemaGeneratorApi

/**
 * Returns the constraints intrinsic to a built-in type, by its fully qualified Kotlin or Java name;
 * empty for any other name. Covers the value ranges of `Byte`, `Short`, `Char` and the unsigned types,
 * and the JSON Schema `format` of UUID and date/time types.
 */
@InternalSchemaGeneratorApi
public fun builtInConstraintsFor(qualifiedName: String?): List<Constraint> =
    BUILT_IN_CONSTRAINTS[qualifiedName].orEmpty()

private fun range(
    min: Long,
    max: Long,
): List<Constraint> =
    listOf(
        Constraint.Minimum(Literal.Integer(min)),
        Constraint.Maximum(Literal.Integer(max)),
    )

private val BUILT_IN_CONSTRAINTS: Map<String, List<Constraint>> =
    buildMap {
        val byte = range(Byte.MIN_VALUE.toLong(), Byte.MAX_VALUE.toLong())
        val short = range(Short.MIN_VALUE.toLong(), Short.MAX_VALUE.toLong())
        val char = listOf(Constraint.Size(min = 1, max = 1))
        val uuid = listOf(Constraint.Format("uuid"))
        val dateTime = listOf(Constraint.Format("date-time"))
        val date = listOf(Constraint.Format("date"))
        val time = listOf(Constraint.Format("time"))

        put("kotlin.Byte", byte)
        put("java.lang.Byte", byte)
        put("kotlin.Short", short)
        put("java.lang.Short", short)
        put("kotlin.Char", char)
        put("java.lang.Character", char)
        put("kotlin.UByte", range(0, UByte.MAX_VALUE.toLong()))
        put("kotlin.UShort", range(0, UShort.MAX_VALUE.toLong()))
        put("kotlin.UInt", range(0, UInt.MAX_VALUE.toLong()))
        // ULong.MAX_VALUE does not fit Literal.Integer, so only the lower bound is stated.
        put("kotlin.ULong", listOf(Constraint.Minimum(Literal.Integer(0))))

        put("java.util.UUID", uuid)
        put("kotlin.uuid.Uuid", uuid)
        put("kotlin.time.Instant", dateTime)
        put("kotlinx.datetime.Instant", dateTime)
        put("java.time.Instant", dateTime)
        put("java.time.OffsetDateTime", dateTime)
        put("java.time.ZonedDateTime", dateTime)
        put("java.util.Date", dateTime)
        put("kotlinx.datetime.LocalDate", date)
        put("java.time.LocalDate", date)
        put("kotlinx.datetime.LocalTime", time)
        put("java.time.LocalTime", time)
        put("java.time.OffsetTime", time)
    }
