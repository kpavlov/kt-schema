package me.kpavlov.kt.schema.generator.core.ir

import io.github.oshai.kotlinlogging.KotlinLogging
import me.kpavlov.kt.schema.generator.core.ExperimentalSchemaGeneratorApi
import me.kpavlov.kt.schema.generator.core.InternalSchemaGeneratorApi

private val logger = KotlinLogging.logger {}

/**
 * Returns this reference with [extra] merged into its constraints. A [Constraint.Size], [Constraint.Minimum]
 * or [Constraint.Maximum] already present is replaced by the tighter of the two bounds, whatever the order
 * (`@Size(min = 2) @NotEmpty` keeps `min = 2`; `@Min(0)` on a `Byte` keeps the built-in `max = 127`).
 * A constraint already present is not added twice. Other constraints are appended, so the emitter's
 * last-wins rule applies to `pattern` and `format`.
 *
 * Bounds that exclude every value are kept as written rather than failing generation: the annotated source
 * may not be the caller's to fix (a library or generated class), and a hard failure would block the whole
 * schema. Numeric bounds stay as given, and an inverted size becomes two one-sided [Constraint.Size]s
 * (`@Size(min = 2)` on a `Char` yields `min = 2` and `max = 1`). On a non-null reference they are logged as
 * a warning; on a nullable one `null` still satisfies them, so nothing is logged.
 *
 * Callers need no [ExperimentalSchemaGeneratorApi] opt-in; this is the internal API's merge rule.
 */
@InternalSchemaGeneratorApi
public fun TypeRef.withAddedConstraints(extra: List<Constraint>): TypeRef = merged(extra, member = null)

/**
 * Like [withAddedConstraints], but prefixes the contradictory-bounds warning with [member]
 * (e.g. `com.acme.Order.quantity`) so build-time front ends report where the conflict is.
 */
@InternalSchemaGeneratorApi
public fun TypeRef.withAddedConstraints(
    member: String,
    extra: () -> List<Constraint>,
): TypeRef = merged(extra(), member)

@OptIn(ExperimentalSchemaGeneratorApi::class)
private fun TypeRef.merged(
    extra: List<Constraint>,
    member: String?,
): TypeRef {
    if (extra.isEmpty()) return this
    val merged = extra.fold(constraints, ::tightened)
    if (!nullable) {
        merged.contradiction(isInteger())?.let { logger.warn { "${member?.let { "$it: " }.orEmpty()}$it" } }
    }
    return withConstraints(merged)
}

/** An inverted `Size` intersection is kept as two one-sided bounds. */
private fun tightened(
    acc: List<Constraint>,
    next: Constraint,
): List<Constraint> {
    val prev = acc.firstOrNull { it.sameBoundAs(next) }
    return when {
        next in acc -> acc
        next is Constraint.Size -> {
            val sizes = acc.filterIsInstance<Constraint.Size>() + next
            acc - sizes.toSet() + intersection(sizes)
        }
        prev == null -> acc + next
        else -> acc - prev + tighter(prev, next)
    }
}

private fun intersection(sizes: List<Constraint.Size>): List<Constraint.Size> {
    val min = sizes.mapNotNull { it.min }.maxOrNull()
    val max = sizes.mapNotNull { it.max }.minOrNull()
    return if (min != null && max != null && min > max) {
        listOf(Constraint.Size(min = min), Constraint.Size(max = max))
    } else {
        listOf(Constraint.Size(min, max))
    }
}

@OptIn(ExperimentalSchemaGeneratorApi::class)
private fun TypeRef.isInteger(): Boolean =
    this is TypeRef.Inline && (node as? PrimitiveNode)?.kind.let { it == PrimitiveKind.INT || it == PrimitiveKind.LONG }

private fun Constraint.sameBoundAs(other: Constraint): Boolean =
    when (this) {
        is Constraint.Minimum -> other is Constraint.Minimum
        is Constraint.Maximum -> other is Constraint.Maximum
        is Constraint.Size, is Constraint.Pattern, is Constraint.Format, Constraint.UniqueItems -> false
    }

private fun tighter(
    prev: Constraint,
    next: Constraint,
): Constraint =
    when {
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
