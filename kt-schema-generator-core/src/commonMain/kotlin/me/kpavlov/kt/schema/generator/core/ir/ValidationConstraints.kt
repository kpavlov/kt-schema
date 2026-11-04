package me.kpavlov.kt.schema.generator.core.ir

import io.github.oshai.kotlinlogging.KotlinLogging
import me.kpavlov.kt.schema.generator.core.InternalSchemaGeneratorApi

private val logger = KotlinLogging.logger {}

/**
 * Returns the constraints a Jakarta Bean Validation annotation named [simpleName] expresses, or an empty list
 * for an unknown name. Which annotations qualify is decided by the caller, see
 * [Introspections.getConstraintsFromAnnotation]; how the result combines with constraints already present
 * is defined by [withAddedConstraints].
 *
 * [arguments] are the annotation's element values; an element missing from the list takes the annotation's
 * documented default. Supported: `Min`, `Max`, `DecimalMin`, `DecimalMax` (a value that is not a finite
 * decimal is ignored), `Positive`, `PositiveOrZero`, `Negative`, `NegativeOrZero`, `Size`, `NotEmpty`,
 * `NotBlank` (approximated as a minimum length of 1), `Pattern` and `Email`. A `Pattern` with `flags` is
 * skipped with a warning, since JSON Schema has no portable way to express them.
 *
 * @throws IllegalArgumentException if a `Size` has `min` greater than `max`
 */
@InternalSchemaGeneratorApi
public fun validationConstraintsFor(
    simpleName: String,
    arguments: List<Pair<String, Any?>>,
): List<Constraint> = VALIDATION_MAPPERS[simpleName.lowercase()]?.invoke(arguments.toMap()).orEmpty()

private val ZERO = Literal.Integer(0)

/** Keyed by lowercase simple name, matching the case-insensitive simple-name patterns of [Introspections]. */
private val VALIDATION_MAPPERS: Map<String, (Map<String, Any?>) -> List<Constraint>> =
    mapOf<String, (Map<String, Any?>) -> List<Constraint>>(
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
        "Pattern" to ::pattern,
        "Email" to { _ -> listOf(Constraint.Format("email")) },
    ).mapKeys { it.key.lowercase() }

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
    return if (min == null && max == null) emptyList() else listOf(Constraint.Size(min, max))
}

/** Flags (`CASE_INSENSITIVE`, ...) have no portable ECMA-262 form, so a flagged pattern is not emitted. */
private fun pattern(args: Map<String, Any?>): List<Constraint> {
    val regexp = args["regexp"] as? String ?: return emptyList()
    val flags = args["flags"]
    val flagged = (flags as? Array<*>)?.isNotEmpty() == true || (flags as? Collection<*>)?.isNotEmpty() == true
    if (flagged) {
        logger.warn { "@Pattern(regexp = \"$regexp\") has flags, which JSON Schema cannot express; pattern skipped" }
    }
    return if (flagged) emptyList() else listOf(Constraint.Pattern(regexp))
}
