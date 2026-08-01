package me.kpavlov.kt.schema.generator.reflect

import me.kpavlov.kt.schema.generator.core.ir.Introspections
import kotlin.reflect.KClass

/**
 * Checks whether the given list of annotations contains a recognized nullable marker
 * (e.g., `@Nullable`).
 *
 * @see [Introspections.isNullableAnnotation]
 */
internal fun isNullableAnnotated(annotations: List<Annotation>): Boolean =
    annotations.any { annotation ->
        val javaClass = annotation.annotationClass.java
        Introspections.isNullableAnnotation(javaClass.simpleName, javaClass.name)
    }

/**
 * Checks whether the given list of annotations contains a recognized optional marker
 * (e.g., `@Optional`).
 *
 * @see [Introspections.isOptionalAnnotation]
 */
internal fun isOptionalAnnotated(annotations: List<Annotation>): Boolean =
    annotations.any { annotation ->
        val javaClass = annotation.annotationClass.java
        Introspections.isOptionalAnnotation(javaClass.simpleName, javaClass.name)
    }

/**
 * Checks whether [klass]'s simple name matches a configured optional-type-name glob pattern
 * (e.g. `*Opt`).
 *
 * @see [Introspections.isOptionalTypeName]
 */
internal fun isOptionalTypeName(klass: KClass<*>): Boolean = Introspections.isOptionalTypeName(klass.simpleName)
