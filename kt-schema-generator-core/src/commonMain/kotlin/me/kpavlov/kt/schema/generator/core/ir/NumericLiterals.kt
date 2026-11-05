package me.kpavlov.kt.schema.generator.core.ir

/** Whole values compare exactly as `Long`; otherwise as `Double`, with `-0.0` equal to `0.0`. */
internal fun Literal.Numeric.compareTo(other: Literal.Numeric): Int {
    val a = wholeValue()
    val b = other.wholeValue()
    if (a != null && b != null) return a.compareTo(b)
    val x = number.toDouble()
    val y = other.number.toDouble()
    return when {
        x < y -> -1
        x > y -> 1
        else -> 0
    }
}

private fun Literal.Numeric.wholeValue(): Long? =
    when (this) {
        is Literal.Integer -> value
        is Literal.Decimal ->
            value.toLong().takeIf { it.toDouble() == value && value < Long.MAX_VALUE.toDouble() }
    }

internal val Literal.Numeric.number: Number
    get() =
        when (this) {
            is Literal.Integer -> value
            is Literal.Decimal -> value
        }
