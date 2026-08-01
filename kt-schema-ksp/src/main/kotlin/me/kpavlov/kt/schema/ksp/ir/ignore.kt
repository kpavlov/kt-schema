package me.kpavlov.kt.schema.ksp.ir

import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import me.kpavlov.kt.schema.generator.core.ir.Introspections

/**
 * Checks whether the symbol is annotated with a recognized ignore annotation
 * (e.g., `@SchemaIgnore`, `@SerialSchemaIgnore`, `@JsonIgnoreType`).
 *
 * Recognition is delegated to [Introspections.isIgnoreAnnotation], which performs
 * matching against a configurable set loaded from `kt-schema.properties`.
 * Simple names are matched case-insensitively; fully qualified names are matched
 * case-sensitively.
 *
 * @return `true` if any annotation on this symbol is recognized as an ignore marker
 */
internal fun KSAnnotated.isSchemaIgnored(): Boolean =
    annotations.any { annotation ->
        val declaration = annotation.annotationType.resolve().declaration
        val simpleName = declaration.simpleName.asString()
        val qualifiedName = declaration.qualifiedName?.asString()
        Introspections.isIgnoreAnnotation(simpleName, qualifiedName)
    }

/**
 * Checks whether the property or its getter carries a recognized ignore annotation.
 *
 * Covers `@get:` use-site targets (e.g. `@get:JsonIgnore`), the idiomatic
 * Jackson-Kotlin placement for ignored properties.
 *
 * @return `true` if the property or its getter is recognized as an ignore marker
 */
internal fun KSPropertyDeclaration.isIgnoredForSchema(): Boolean =
    isSchemaIgnored() || getter?.isSchemaIgnored() == true
