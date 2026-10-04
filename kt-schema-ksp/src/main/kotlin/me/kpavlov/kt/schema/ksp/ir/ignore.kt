package me.kpavlov.kt.schema.ksp.ir

import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import me.kpavlov.kt.schema.generator.core.ir.Introspections

/**
 * Whether the symbol has an ignore annotation (e.g. `@SchemaIgnore`, `@JsonIgnoreType`), per
 * [Introspections.isIgnoreAnnotation].
 */
internal fun KSAnnotated.isSchemaIgnored(): Boolean =
    annotations.any { it.withClassNames(Introspections::isIgnoreAnnotation) }

/** Whether the property or its getter has an ignore annotation, which covers `@get:JsonIgnore`. */
internal fun KSPropertyDeclaration.isIgnoredForSchema(): Boolean =
    isSchemaIgnored() || getter?.isSchemaIgnored() == true
