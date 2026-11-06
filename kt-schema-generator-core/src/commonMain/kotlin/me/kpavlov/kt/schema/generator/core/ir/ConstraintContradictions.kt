package me.kpavlov.kt.schema.generator.core.ir

import kotlin.math.ceil
import kotlin.math.floor

/**
 * Describes bounds that exclude every value, or returns `null` when some value satisfies them all. With [integer],
 * the numeric bounds must leave a whole number; otherwise they must leave a real number.
 */
internal fun List<Constraint>.contradiction(integer: Boolean): String? = invertedSize() ?: emptyNumericRange(integer)

private fun List<Constraint>.invertedSize(): String? {
    val min = filterIsInstance<Constraint.Size>().mapNotNull { it.min }.maxOrNull()
    val max = filterIsInstance<Constraint.Size>().mapNotNull { it.max }.minOrNull()
    return if (min != null && max != null && min > max) "Size min $min exceeds max $max" else null
}

private fun List<Constraint>.emptyNumericRange(integer: Boolean): String? {
    val min = firstNotNullOfOrNull { it as? Constraint.Minimum }
    val max = firstNotNullOfOrNull { it as? Constraint.Maximum }
    if (min == null || max == null || satisfiable(min, max, integer)) return null
    return "Bounds exclude every value: ${describe("minimum", min.value, min.exclusive)}, " +
        describe("maximum", max.value, max.exclusive)
}

private fun satisfiable(
    min: Constraint.Minimum,
    max: Constraint.Maximum,
    integer: Boolean,
): Boolean =
    if (integer) {
        val lowest = min.value.lowestInteger(min.exclusive)
        val highest = max.value.highestInteger(max.exclusive)
        lowest != null && highest != null && lowest <= highest
    } else {
        val order = min.value.compareTo(max.value)
        order < 0 || (order == 0 && !min.exclusive && !max.exclusive)
    }

/** The smallest `Long` the bound admits, or `null` if that integer lies above `Long.MAX_VALUE`. */
private fun Literal.Numeric.lowestInteger(exclusive: Boolean): Long? =
    when (this) {
        is Literal.Integer -> if (exclusive) value.takeIf { it < Long.MAX_VALUE }?.inc() else value
        is Literal.Decimal ->
            when {
                value >= LONG_LIMIT -> null
                value < -LONG_LIMIT -> Long.MIN_VALUE
                exclusive -> floor(value).toLong().inc()
                else -> ceil(value).toLong()
            }
    }

/** The largest `Long` the bound admits, or `null` if that integer lies below `Long.MIN_VALUE`. */
private fun Literal.Numeric.highestInteger(exclusive: Boolean): Long? =
    when (this) {
        is Literal.Integer -> if (exclusive) value.takeIf { it > Long.MIN_VALUE }?.dec() else value
        is Literal.Decimal ->
            when {
                value < -LONG_LIMIT -> null
                value >= LONG_LIMIT -> Long.MAX_VALUE
                exclusive -> ceil(value).toLong().takeIf { it > Long.MIN_VALUE }?.dec()
                else -> floor(value).toLong()
            }
    }

/** 2^63: the first double above `Long.MAX_VALUE`; its negation is `Long.MIN_VALUE`. */
private const val LONG_LIMIT: Double = 9.223372036854775807E18

private fun describe(
    bound: String,
    value: Literal.Numeric,
    exclusive: Boolean,
): String = "${if (exclusive) "exclusive " else ""}$bound ${value.number}"
