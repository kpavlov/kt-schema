package me.kpavlov.kt.schema.ksp.ir

import com.google.devtools.ksp.symbol.KSAnnotation
import me.kpavlov.kt.schema.generator.core.ir.Introspections

/** The description from the annotation (e.g. `@Description`), or null. */
internal fun KSAnnotation.descriptionOrNull(): String? =
    withClassNames { simpleName, qualifiedName ->
        Introspections.getDescriptionFromAnnotation(
            simpleName = simpleName,
            qualifiedName = qualifiedName,
            annotationArguments = namedArguments(),
        )
    }

/** The name override from the annotation (e.g. `@SerialName`), or null. */
internal fun KSAnnotation.nameOverrideOrNull(): String? =
    withClassNames { simpleName, qualifiedName ->
        Introspections.getNameOverride(
            simpleName = simpleName,
            qualifiedName = qualifiedName,
            annotationArguments = namedArguments(),
        )
    }
