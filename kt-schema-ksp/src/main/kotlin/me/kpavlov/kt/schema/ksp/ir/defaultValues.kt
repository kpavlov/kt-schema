package me.kpavlov.kt.schema.ksp.ir

import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import me.kpavlov.kt.schema.generator.core.ir.Introspections

/**
 * Checks whether the symbol is annotated with a recognized enum-default-value marker
 * (e.g. `@JsonEnumDefaultValue`), placed on a single enum constant.
 *
 * Recognition is delegated to [Introspections.isEnumDefaultAnnotation], which performs
 * matching against a configurable set loaded from `kt-schema.properties`.
 *
 * @return `true` if any annotation on this symbol is recognized as an enum-default-value marker
 */
internal fun KSAnnotated.isEnumDefaultAnnotated(): Boolean =
    annotations.any { annotation ->
        val declaration = annotation.annotationType.resolve().declaration
        val simpleName = declaration.simpleName.asString()
        val qualifiedName = declaration.qualifiedName?.asString()
        Introspections.isEnumDefaultAnnotation(simpleName, qualifiedName)
    }

/**
 * Retrieves the default-value override from the annotation, if available (e.g. from
 * `@JsonProperty(defaultValue = "...")`).
 *
 * @return The default value extracted from the annotation or null if not a default-value annotation.
 */
internal fun KSAnnotation.defaultValueOrNull(): String? {
    val declaration = annotationType.resolve().declaration
    val simpleName = declaration.simpleName.asString()
    val qualifiedName = declaration.qualifiedName?.asString()

    val args: List<Pair<String, Any?>> =
        arguments.mapNotNull {
            val name = it.name?.asString() ?: return@mapNotNull null
            name to it.value
        }
    return Introspections.getDefaultValueFromAnnotation(
        simpleName = simpleName,
        qualifiedName = qualifiedName,
        annotationArguments = args,
    )
}

/**
 * Extracts a default-value override from an annotated element's own annotations.
 *
 * @see [defaultValueOrNull]
 */
internal fun extractDefaultValueOverride(annotated: KSAnnotated): String? =
    annotated.annotations.firstNotNullOfOrNull { it.defaultValueOrNull() }
