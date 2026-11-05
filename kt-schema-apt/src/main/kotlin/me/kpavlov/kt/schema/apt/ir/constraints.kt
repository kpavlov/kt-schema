// Copyright (c) 2026 Konstantin Pavlov and contributors
package me.kpavlov.kt.schema.apt.ir

import me.kpavlov.kt.schema.generator.core.ir.Constraint
import me.kpavlov.kt.schema.generator.core.ir.Introspections
import javax.lang.model.AnnotatedConstruct
import javax.lang.model.element.Element
import javax.lang.model.element.TypeElement

/** `Owner.name` for error messages, from the first target's enclosing type; just [name] when there is none. */
internal fun memberName(
    name: String,
    targets: List<Element>,
): String {
    val owner = (targets.firstOrNull()?.enclosingElement as? TypeElement)?.qualifiedName?.toString()
    return listOfNotNull(owner, name).joinToString(".")
}

/**
 * Collects the constraints of recognized constraint annotations across [targets]: the declaring elements and
 * the property's type, which carries `TYPE_USE` placements such as `String @Size(min = 1) []`. A record
 * component's annotations propagate to its field and accessor; the merge in `withAddedConstraints` drops
 * the duplicates.
 */
internal fun constraintsOf(targets: List<AnnotatedConstruct>): List<Constraint> =
    targets.flatMap { target ->
        target.annotationMirrors.flatMap { mirror ->
            val annotationElement = mirror.annotationType.asElement() as TypeElement
            Introspections.getConstraintsFromAnnotation(
                annotationElement.simpleName.toString(),
                annotationElement.qualifiedName.toString(),
            ) {
                mirror.elementValues.entries.map { (attribute, value) ->
                    attribute.simpleName.toString() to value.value
                }
            }
        }
    }
