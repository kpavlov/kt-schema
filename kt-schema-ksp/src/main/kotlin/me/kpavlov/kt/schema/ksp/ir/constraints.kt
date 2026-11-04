package me.kpavlov.kt.schema.ksp.ir

import com.google.devtools.ksp.symbol.KSAnnotated
import me.kpavlov.kt.schema.generator.core.ir.Constraint
import me.kpavlov.kt.schema.generator.core.ir.Introspections

/** The constraints expressed by the symbol's annotations, see [Introspections.getConstraintsFromAnnotation]. */
internal fun KSAnnotated.validationConstraints(): List<Constraint> =
    annotations
        .flatMap { annotation ->
            annotation.withClassNames { simpleName, qualifiedName ->
                Introspections.getConstraintsFromAnnotation(simpleName, qualifiedName, annotation.namedArguments())
            }
        }.toList()
