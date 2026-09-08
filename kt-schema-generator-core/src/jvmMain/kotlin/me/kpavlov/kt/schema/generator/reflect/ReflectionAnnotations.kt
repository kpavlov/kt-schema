@file:JvmName("ReflectionAnnotations")

package me.kpavlov.kt.schema.generator.reflect

import me.kpavlov.kt.schema.generator.core.InternalSchemaGeneratorApi
import me.kpavlov.kt.schema.generator.core.ir.Introspections

/**
 * Checks whether the given list of annotations contains a recognized ignore marker
 * (e.g., `@SchemaIgnore`, `@SerialSchemaIgnore`, `@JsonIgnoreType`).
 *
 * @see [Introspections.isIgnoreAnnotation]
 */
internal fun isSchemaIgnored(annotations: List<Annotation>): Boolean =
    annotations.any { annotation ->
        val javaClass = annotation.annotationClass.java
        Introspections.isIgnoreAnnotation(javaClass.simpleName, javaClass.name)
    }

/**
 * Extracts description from annotations.
 *
 * @see [Introspections.getDescriptionFromAnnotation]
 */
internal fun extractDescription(annotations: List<Annotation>): String? =
    annotations.firstNotNullOfOrNull { annotation ->
        val javaClass = annotation.annotationClass.java
        Introspections.getDescriptionFromAnnotation(
            javaClass.simpleName,
            javaClass.name,
            buildAnnotationArgs(annotation),
        )
    }

/**
 * Extracts a name override from annotations (e.g., from `@SerialName`).
 *
 * @see [Introspections.getNameOverride]
 */
@InternalSchemaGeneratorApi
public fun extractNameOverride(annotations: List<Annotation>): String? =
    annotations.firstNotNullOfOrNull { annotation ->
        val javaClass = annotation.annotationClass.java
        Introspections.getNameOverride(javaClass.simpleName, javaClass.name, buildAnnotationArgs(annotation))
    }

/**
 * Extracts a discriminator property-name override from annotations (e.g., from
 * `@JsonClassDiscriminator`), falling back to `"type"` when none is present.
 *
 * @see [Introspections.getDiscriminatorPropertyName]
 */
internal fun extractDiscriminatorPropertyName(annotations: List<Annotation>): String? =
    annotations.firstNotNullOfOrNull { annotation ->
        val javaClass = annotation.annotationClass.java
        Introspections.getDiscriminatorPropertyName(
            javaClass.simpleName,
            javaClass.name,
            buildAnnotationArgs(annotation),
        )
    }

/**
 * Checks whether the given list of annotations contains a recognized enum-default-value marker
 * (e.g., `@JsonEnumDefaultValue`), placed on a single enum constant.
 *
 * @see [Introspections.isEnumDefaultAnnotation]
 */
internal fun isEnumDefaultAnnotated(annotations: List<Annotation>): Boolean =
    annotations.any { annotation ->
        val javaClass = annotation.annotationClass.java
        Introspections.isEnumDefaultAnnotation(javaClass.simpleName, javaClass.name)
    }

/**
 * Extracts a default-value override from annotations (e.g., from `@JsonProperty(defaultValue = "...")`).
 *
 * Mainly useful for front ends without native default-value support; for reflection, a real
 * Kotlin default value always takes precedence when both are present.
 *
 * @see [Introspections.getDefaultValueFromAnnotation]
 */
internal fun extractDefaultValueOverride(annotations: List<Annotation>): String? =
    annotations.firstNotNullOfOrNull { annotation ->
        val javaClass = annotation.annotationClass.java
        Introspections.getDefaultValueFromAnnotation(
            javaClass.simpleName,
            javaClass.name,
            buildAnnotationArgs(annotation),
        )
    }

/**
 * Builds key-value pairs from an annotation's elements for use with [Introspections] methods.
 *
 * Uses Java reflection via [Class.getDeclaredMethods] to reliably access annotation elements,
 * including annotations from external modules where Kotlin reflection metadata may be unavailable.
 */
private fun buildAnnotationArgs(annotation: Annotation): List<Pair<String, Any?>> =
    buildList {
        annotation.annotationClass.java.declaredMethods
            .filter { it.parameterCount == 0 }
            .forEach { method ->
                val value =
                    try {
                        method.isAccessible = true
                        method.invoke(annotation)
                    } catch (_: ReflectiveOperationException) {
                        return@forEach
                    } catch (_: SecurityException) {
                        return@forEach
                    }
                add(method.name to value)
            }
    }
