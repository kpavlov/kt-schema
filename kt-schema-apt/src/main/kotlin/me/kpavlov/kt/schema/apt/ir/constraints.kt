// Copyright (c) 2026 Konstantin Pavlov and contributors
package me.kpavlov.kt.schema.apt.ir

import me.kpavlov.kt.schema.generator.core.ir.Constraint
import me.kpavlov.kt.schema.generator.core.ir.Introspections
import javax.lang.model.element.Element
import javax.lang.model.element.TypeElement

/**
 * Collects the constraints of recognized constraint annotations across [targets]. A record component's
 * annotations propagate to its field and accessor, so duplicates are dropped.
 */
internal fun constraintsOf(targets: List<Element>): List<Constraint> =
    targets
        .flatMap { element ->
            element.annotationMirrors.flatMap { mirror ->
                val annotationElement = mirror.annotationType.asElement() as TypeElement
                val args =
                    mirror.elementValues.entries.map { (attribute, value) ->
                        attribute.simpleName.toString() to value.value
                    }
                Introspections.getConstraintsFromAnnotation(
                    simpleName = annotationElement.simpleName.toString(),
                    qualifiedName = annotationElement.qualifiedName.toString(),
                    annotationArguments = args,
                )
            }
        }.distinct()
