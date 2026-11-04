package me.kpavlov.kt.schema.generator.core.ir

import me.kpavlov.kt.schema.generator.core.ExperimentalSchemaGeneratorApi
import me.kpavlov.kt.schema.generator.core.InternalSchemaGeneratorApi

/**
 * Returns the constraints a Jakarta Bean Validation annotation named [simpleName] expresses, or an empty list
 * for an unknown name. Which annotations qualify is decided by the caller, see
 * [Introspections.getConstraintsFromAnnotation].
 *
 * [arguments] are the annotation's element values; an element missing from the list takes the annotation's
 * documented default. Supported: `Min`, `Max`, `DecimalMin`, `DecimalMax` (a value that is not a finite
 * decimal is ignored), `Positive`, `PositiveOrZero`, `Negative`, `NegativeOrZero`, `Size`, `NotEmpty`,
 * `NotBlank` (approximated as a minimum length of 1), `Pattern` (flags ignored) and `Email`.
 *
 * @throws IllegalArgumentException if a `Size` has `min` greater than `max`
 */
@InternalSchemaGeneratorApi
public fun validationConstraintsFor(
    simpleName: String,
    arguments: List<Pair<String, Any?>>,
): List<Constraint> = VALIDATION_MAPPERS[simpleName]?.invoke(arguments.toMap()).orEmpty()

/**
 * Returns this reference with [extra] merged into its constraints. A [Constraint.Size], [Constraint.Minimum]
 * or [Constraint.Maximum] already present is replaced by the tighter of the two bounds, whatever the order
 * (`@Size(min = 2) @NotEmpty` keeps `min = 2`; `@Min(0)` on a `Byte` keeps the built-in `max = 127`).
 * Other constraints are appended, so the emitter's last-wins rule applies to `pattern` and `format`.
 *
 * Callers need no [ExperimentalSchemaGeneratorApi] opt-in; this is the internal API's merge rule.
 */
@InternalSchemaGeneratorApi
@OptIn(ExperimentalSchemaGeneratorApi::class)
public fun TypeRef.withAddedConstraints(extra: List<Constraint>): TypeRef =
    if (extra.isEmpty()) this else withConstraints(extra.fold(constraints, ::tightened))

private fun tightened(
    acc: List<Constraint>,
    next: Constraint,
): List<Constraint> {
    val prev = acc.firstOrNull { it.sameBoundAs(next) } ?: return acc + next
    return acc - prev + tighter(prev, next)
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
            if (next.value.toDouble() > prev.value.toDouble() || next.tiesExclusively(prev)) next else prev

        prev is Constraint.Maximum && next is Constraint.Maximum ->
            if (next.value.toDouble() < prev.value.toDouble() || next.tiesExclusively(prev)) next else prev

        else -> next
    }

private fun Constraint.Minimum.tiesExclusively(other: Constraint.Minimum): Boolean =
    value == other.value && exclusive

private fun Constraint.Maximum.tiesExclusively(other: Constraint.Maximum): Boolean =
    value == other.value && exclusive

private fun maxOfNullable(
    a: Int?,
    b: Int?,
): Int? = if (a == null || b == null) a ?: b else maxOf(a, b)

private fun minOfNullable(
    a: Int?,
    b: Int?,
): Int? = if (a == null || b == null) a ?: b else minOf(a, b)

private fun Literal.Numeric.toDouble(): Double =
    when (this) {
        is Literal.Integer -> value.toDouble()
        is Literal.Decimal -> value
    }

private val ZERO = Literal.Integer(0)

private val VALIDATION_MAPPERS: Map<String, (Map<String, Any?>) -> List<Constraint>> =
    mapOf(
        "Min" to { args -> args.integer("value")?.let { listOf(Constraint.Minimum(it)) }.orEmpty() },
        "Max" to { args -> args.integer("value")?.let { listOf(Constraint.Maximum(it)) }.orEmpty() },
        "DecimalMin" to { args ->
            args.decimal("value")?.let { listOf(Constraint.Minimum(it, args.exclusive())) }.orEmpty()
        },
        "DecimalMax" to { args ->
            args.decimal("value")?.let { listOf(Constraint.Maximum(it, args.exclusive())) }.orEmpty()
        },
        "Positive" to { _ -> listOf(Constraint.Minimum(ZERO, exclusive = true)) },
        "PositiveOrZero" to { _ -> listOf(Constraint.Minimum(ZERO)) },
        "Negative" to { _ -> listOf(Constraint.Maximum(ZERO, exclusive = true)) },
        "NegativeOrZero" to { _ -> listOf(Constraint.Maximum(ZERO)) },
        "Size" to ::size,
        "NotEmpty" to { _ -> listOf(Constraint.Size(min = 1)) },
        "NotBlank" to { _ -> listOf(Constraint.Size(min = 1)) },
        "Pattern" to { args -> (args["regexp"] as? String)?.let { listOf(Constraint.Pattern(it)) }.orEmpty() },
        "Email" to { _ -> listOf(Constraint.Format("email")) },
    )

private fun Map<String, Any?>.integer(name: String): Literal.Integer? =
    (this[name] as? Number)?.let { Literal.Integer(it.toLong()) }

/** `@DecimalMin`/`@DecimalMax` take a `BigDecimal` string; a whole value stays an integer literal. */
private fun Map<String, Any?>.decimal(name: String): Literal.Numeric? {
    val text = this[name] as? String ?: return null
    return text.toLongOrNull()?.let(Literal::Integer)
        ?: text.toDoubleOrNull()?.takeIf { it.isFinite() }?.let(Literal::Decimal)
}

private fun Map<String, Any?>.exclusive(): Boolean = this["inclusive"] == false

/** `@Size` defaults (`min = 0`, `max = Integer.MAX_VALUE`) are unbounded. */
private fun size(args: Map<String, Any?>): List<Constraint> {
    val min = (args["min"] as? Number)?.toInt()?.takeIf { it > 0 }
    val max = (args["max"] as? Number)?.toInt()?.takeIf { it < Int.MAX_VALUE }
    require(min == null || max == null || min <= max) { "@Size has min $min greater than max $max" }
    return if (min == null && max == null) emptyList() else listOf(Constraint.Size(min, max))
}
