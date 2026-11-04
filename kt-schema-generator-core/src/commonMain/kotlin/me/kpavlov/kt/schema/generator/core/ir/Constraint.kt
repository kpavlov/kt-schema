package me.kpavlov.kt.schema.generator.core.ir

import me.kpavlov.kt.schema.generator.core.ExperimentalSchemaGeneratorApi

/**
 * A validation constraint on a value, independent of any schema dialect.
 *
 * Constraints are attached to a [TypeRef] and so apply at a usage site. Emitters map each constraint to
 * the keyword their dialect provides for the referenced type and ignore constraints the type cannot carry.
 *
 * The set of constraints is experimental and may grow or change; see [TypeRef.constraints].
 */
public sealed interface Constraint {
    /**
     * Lower numeric bound; [exclusive] means the value must be strictly greater than [value].
     * JSON Schema emits the bound as a number, so an integer above 2^53 loses precision.
     */
    public data class Minimum(
        val value: Literal.Numeric,
        val exclusive: Boolean = false,
    ) : Constraint

    /**
     * Upper numeric bound; [exclusive] means the value must be strictly less than [value].
     * JSON Schema emits the bound as a number, so an integer above 2^53 loses precision.
     */
    public data class Maximum(
        val value: Literal.Numeric,
        val exclusive: Boolean = false,
    ) : Constraint

    /**
     * Inclusive cardinality bounds: string length, list size or map entry count, decided by the
     * referenced type. A `null` bound is unbounded on that side.
     *
     * @throws IllegalArgumentException if both bounds are `null`, a bound is negative, or [min] exceeds [max].
     */
    public data class Size(
        val min: Int? = null,
        val max: Int? = null,
    ) : Constraint {
        init {
            require(min != null || max != null) { "Size needs at least one bound" }
            require((min ?: 0) >= 0 && (max ?: 0) >= 0) { "Size bounds must be non-negative: min=$min, max=$max" }
            require(min == null || max == null || min <= max) { "Size min $min exceeds max $max" }
        }
    }

    /** Regular expression the string value must match. */
    public data class Pattern(
        val regex: String,
    ) : Constraint

    /** Semantic format name of a string value, such as `email` or `uuid`. */
    public data class Format(
        val name: String,
    ) : Constraint

    /** All elements of a list value are distinct, as for a `Set`. */
    public data object UniqueItems : Constraint
}

/**
 * Returns a copy of this [TypeRef] with the specified [constraints], replacing any it already had.
 * Front ends call it after resolving a possibly cached reference, the same way as [withNullable].
 */
@ExperimentalSchemaGeneratorApi
public fun TypeRef.withConstraints(constraints: List<Constraint>): TypeRef =
    when (this) {
        is TypeRef.Inline -> copy(constraints = constraints)
        is TypeRef.Ref -> copy(constraints = constraints)
    }
