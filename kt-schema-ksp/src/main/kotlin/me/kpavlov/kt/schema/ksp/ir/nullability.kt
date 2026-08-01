package me.kpavlov.kt.schema.ksp.ir

import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSType
import me.kpavlov.kt.schema.generator.core.ir.Introspections

/**
 * Checks whether the symbol is annotated with a recognized nullable marker (e.g. `@Nullable`).
 *
 * Recognition is delegated to [Introspections.isNullableAnnotation], which performs
 * matching against a configurable set loaded from `kt-schema.properties`.
 *
 * @return `true` if any annotation on this symbol is recognized as a nullable marker
 */
internal fun KSAnnotated.isNullableAnnotated(): Boolean =
    annotations.any { annotation ->
        val declaration = annotation.annotationType.resolve().declaration
        val simpleName = declaration.simpleName.asString()
        val qualifiedName = declaration.qualifiedName?.asString()
        Introspections.isNullableAnnotation(simpleName, qualifiedName)
    }

/**
 * Checks whether the symbol is annotated with a recognized optional marker (e.g. `@Nullable`).
 *
 * Recognition is delegated to [Introspections.isOptionalAnnotation], which performs
 * matching against a configurable set loaded from `kt-schema.properties`.
 *
 * @return `true` if any annotation on this symbol is recognized as an optional marker
 */
internal fun KSAnnotated.isOptionalAnnotated(): Boolean =
    annotations.any { annotation ->
        val declaration = annotation.annotationType.resolve().declaration
        val simpleName = declaration.simpleName.asString()
        val qualifiedName = declaration.qualifiedName?.asString()
        Introspections.isOptionalAnnotation(simpleName, qualifiedName)
    }

/**
 * Checks whether this type's declaration simple name matches a configured nullable-type-name
 * glob pattern (e.g. `*Opt`).
 *
 * @see [Introspections.isNullableTypeName]
 */
internal fun KSType.isNullableByTypeName(): Boolean =
    Introspections.isNullableTypeName(declaration.simpleName.asString())

/**
 * Checks whether this type's declaration simple name matches a configured optional-type-name
 * glob pattern (e.g. `*Opt`).
 *
 * @see [Introspections.isOptionalTypeName]
 */
internal fun KSType.isOptionalByTypeName(): Boolean =
    Introspections.isOptionalTypeName(declaration.simpleName.asString())
