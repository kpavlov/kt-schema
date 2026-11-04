package me.kpavlov.kt.schema.generator.json

import me.kpavlov.kt.schema.generator.core.ir.Constraint
import me.kpavlov.kt.schema.generator.core.ir.EnumNode
import me.kpavlov.kt.schema.generator.core.ir.Literal
import me.kpavlov.kt.schema.generator.core.ir.ObjectNode
import me.kpavlov.kt.schema.generator.core.ir.TypeNode
import me.kpavlov.kt.schema.json.AllOfPropertyDefinition
import me.kpavlov.kt.schema.json.AnyOfPropertyDefinition
import me.kpavlov.kt.schema.json.ArrayPropertyDefinition
import me.kpavlov.kt.schema.json.JsonSchemaConstants.Types.NULL_TYPE
import me.kpavlov.kt.schema.json.NumericPropertyDefinition
import me.kpavlov.kt.schema.json.ObjectPropertyDefinition
import me.kpavlov.kt.schema.json.OneOfPropertyDefinition
import me.kpavlov.kt.schema.json.PropertyDefinition
import me.kpavlov.kt.schema.json.ReferencePropertyDefinition
import me.kpavlov.kt.schema.json.StringPropertyDefinition

/**
 * Returns this definition with [constraints] mapped onto the validation keywords its type supports.
 *
 * Constraints the type cannot carry are ignored. A nullable union applies them to every non-null branch.
 * An empty list returns this instance.
 */
internal fun PropertyDefinition.applyConstraints(constraints: List<Constraint>): PropertyDefinition =
    if (constraints.isEmpty()) this else constraints.fold(this) { def, constraint -> def.applyConstraint(constraint) }

/**
 * Returns this `$ref` unchanged for an empty list; otherwise an `allOf` of the reference and a sibling
 * definition shaped like the referenced [node] that carries [constraints]. A polymorphic node has no
 * constrainable keyword, so it keeps the bare reference.
 */
internal fun ReferencePropertyDefinition.withConstraints(
    node: TypeNode,
    constraints: List<Constraint>,
): PropertyDefinition {
    val sibling =
        when (node) {
            is ObjectNode -> ObjectPropertyDefinition()
            is EnumNode -> StringPropertyDefinition()
            else -> null
        }
    return if (constraints.isEmpty() || sibling == null) {
        this
    } else {
        AllOfPropertyDefinition(allOf = listOf(this, sibling.applyConstraints(constraints)))
    }
}

private fun PropertyDefinition.applyConstraint(constraint: Constraint): PropertyDefinition =
    when (this) {
        is StringPropertyDefinition -> if (type == NULL_TYPE) this else applyConstraint(constraint)
        is NumericPropertyDefinition -> applyConstraint(constraint)
        is ArrayPropertyDefinition -> applyConstraint(constraint)
        is ObjectPropertyDefinition -> (constraint as? Constraint.Size)?.let(::applySize) ?: this
        is AnyOfPropertyDefinition -> copy(anyOf = anyOf.map { it.applyConstraint(constraint) })
        is OneOfPropertyDefinition -> copy(oneOf = oneOf.map { it.applyConstraint(constraint) })
        else -> this
    }

private fun StringPropertyDefinition.applyConstraint(constraint: Constraint): StringPropertyDefinition =
    when (constraint) {
        is Constraint.Size -> copy(minLength = constraint.min ?: minLength, maxLength = constraint.max ?: maxLength)
        is Constraint.Pattern -> copy(pattern = constraint.regex)
        is Constraint.Format -> copy(format = constraint.name)
        is Constraint.Minimum, is Constraint.Maximum, Constraint.UniqueItems -> this
    }

private fun NumericPropertyDefinition.applyConstraint(constraint: Constraint): NumericPropertyDefinition =
    when (constraint) {
        is Constraint.Minimum ->
            if (constraint.exclusive) {
                copy(exclusiveMinimum = constraint.value.toBound())
            } else {
                copy(minimum = constraint.value.toBound())
            }

        is Constraint.Maximum ->
            if (constraint.exclusive) {
                copy(exclusiveMaximum = constraint.value.toBound())
            } else {
                copy(maximum = constraint.value.toBound())
            }

        is Constraint.Size, is Constraint.Pattern, is Constraint.Format, Constraint.UniqueItems -> this
    }

private fun ArrayPropertyDefinition.applyConstraint(constraint: Constraint): ArrayPropertyDefinition =
    when (constraint) {
        is Constraint.Size -> copy(minItems = constraint.min ?: minItems, maxItems = constraint.max ?: maxItems)
        Constraint.UniqueItems -> copy(uniqueItems = true)
        is Constraint.Minimum, is Constraint.Maximum, is Constraint.Pattern, is Constraint.Format -> this
    }

private fun ObjectPropertyDefinition.applySize(size: Constraint.Size): ObjectPropertyDefinition =
    copy(minProperties = size.min ?: minProperties, maxProperties = size.max ?: maxProperties)

private fun Literal.Numeric.toBound(): Double =
    when (this) {
        is Literal.Integer -> value.toDouble()
        is Literal.Decimal -> value
    }
