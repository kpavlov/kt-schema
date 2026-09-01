package me.kpavlov.kt.schema.ksp.ir

import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import me.kpavlov.kt.schema.generator.core.ir.Introspections

/**
 * Whether the symbol has an enum-default-value marker (e.g. `@JsonEnumDefaultValue`) on a single enum constant, per
 * [Introspections.isEnumDefaultAnnotation].
 */
internal fun KSAnnotated.isEnumDefaultAnnotated(): Boolean =
    annotations.any { it.withClassNames(Introspections::isEnumDefaultAnnotation) }

/** The default value from the annotation (e.g. `@JsonProperty(defaultValue = "...")`), or null. */
internal fun KSAnnotation.defaultValueOrNull(): String? = extractWith(Introspections::getDefaultValueFromAnnotation)

/** The default-value override from the element's own annotations, see [defaultValueOrNull]. */
internal fun extractDefaultValueOverride(annotated: KSAnnotated): String? =
    annotated.annotations.firstNotNullOfOrNull { it.defaultValueOrNull() }
