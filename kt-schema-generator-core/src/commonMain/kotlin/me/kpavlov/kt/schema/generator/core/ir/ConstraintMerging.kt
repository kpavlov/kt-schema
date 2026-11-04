package me.kpavlov.kt.schema.generator.core.ir

import me.kpavlov.kt.schema.generator.core.ExperimentalSchemaGeneratorApi
import me.kpavlov.kt.schema.generator.core.InternalSchemaGeneratorApi

/**
 * Returns this reference with [extra] merged into its constraints. A [Constraint.Size], [Constraint.Minimum]
 * or [Constraint.Maximum] already present is replaced by the tighter of the two bounds, whatever the order
 * (`@Size(min = 2) @NotEmpty` keeps `min = 2`; `@Min(0)` on a `Byte` keeps the built-in `max = 127`).
 * A constraint already present is not added twice. Other constraints are appended, so the emitter's
 * last-wins rule applies to `pattern` and `format`.
 *
 * Callers need no [ExperimentalSchemaGeneratorApi] opt-in; this is the internal API's merge rule.
 *
 * @throws IllegalArgumentException if the merged bounds contradict each other (`@Size(min = 2)` on a `Char`)
 */
@InternalSchemaGeneratorApi
@OptIn(ExperimentalSchemaGeneratorApi::class)
public fun TypeRef.withAddedConstraints(extra: List<Constraint>): TypeRef =
    if (extra.isEmpty()) this else withConstraints(extra.fold(constraints, ::tightened))

/**
 * Like [withAddedConstraints], but prefixes the message of a contradictory-bounds
 * [IllegalArgumentException] with [member] (e.g. `com.acme.Order.quantity`) so build-time
 * front ends report where the conflict is.
 */
@InternalSchemaGeneratorApi
public inline fun TypeRef.withAddedConstraints(
    member: String,
    extra: () -> List<Constraint>,
): TypeRef =
    try {
        withAddedConstraints(extra())
    } catch (e: IllegalArgumentException) {
        throw IllegalArgumentException("$member: ${e.message}", e)
    }

private fun tightened(
    acc: List<Constraint>,
    next: Constraint,
): List<Constraint> {
    val prev = acc.firstOrNull { it.sameBoundAs(next) }
    return when {
        next in acc -> acc
        prev == null -> acc + next
        else -> acc - prev + tighter(prev, next)
    }
}

private fun Constraint.sameBoundAs(other: Constraint): Boolean =
    when (this) {
        is Constraint.Size -> other is Constraint.Size
        is Constraint.Minimum -> other is Constraint.Minimum
        is Constraint.Maximum -> other is Constraint.Maximum
        is Constraint.Pattern, is Constraint.Format, Constraint.UniqueItems -> false
    }

private fun tighter(
    prev: Constraint,
    next: Constraint,
): Constraint =
    when {
        prev is Constraint.Size && next is Constraint.Size ->
            Constraint.Size(maxOfNullable(prev.min, next.min), minOfNullable(prev.max, next.max))

        prev is Constraint.Minimum && next is Constraint.Minimum ->
            if (next.value.compareTo(prev.value) > 0 || next.tiesExclusively(prev)) next else prev

        prev is Constraint.Maximum && next is Constraint.Maximum ->
            if (next.value.compareTo(prev.value) < 0 || next.tiesExclusively(prev)) next else prev

        else -> next
    }

private fun Constraint.Minimum.tiesExclusively(other: Constraint.Minimum): Boolean =
    exclusive && value.compareTo(other.value) == 0

private fun Constraint.Maximum.tiesExclusively(other: Constraint.Maximum): Boolean =
    exclusive && value.compareTo(other.value) == 0

private fun maxOfNullable(
    a: Int?,
    b: Int?,
): Int? = if (a == null || b == null) a ?: b else maxOf(a, b)

private fun minOfNullable(
    a: Int?,
    b: Int?,
): Int? = if (a == null || b == null) a ?: b else minOf(a, b)

/** Integers compare exactly; a decimal on either side compares as a `Double`. */
private fun Literal.Numeric.compareTo(other: Literal.Numeric): Int =
    if (this is Literal.Integer && other is Literal.Integer) {
        value.compareTo(other.value)
    } else {
        toDouble().compareTo(other.toDouble())
    }

private fun Literal.Numeric.toDouble(): Double =
    when (this) {
        is Literal.Integer -> value.toDouble()
        is Literal.Decimal -> value
    }
