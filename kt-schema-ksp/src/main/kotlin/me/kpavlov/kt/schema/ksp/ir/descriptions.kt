package me.kpavlov.kt.schema.ksp.ir

import com.google.devtools.ksp.symbol.KSAnnotation
import me.kpavlov.kt.schema.generator.core.ir.Introspections

/** The description from the annotation (e.g. `@Description`), or null. */
internal fun KSAnnotation.descriptionOrNull(): String? = extractWith(Introspections::getDescriptionFromAnnotation)

/** The name override from the annotation (e.g. `@SerialName`), or null. */
internal fun KSAnnotation.nameOverrideOrNull(): String? = extractWith(Introspections::getNameOverride)

/** The discriminator property name from the annotation (e.g. `@JsonClassDiscriminator`), or null. */
internal fun KSAnnotation.discriminatorNameOrNull(): String? = extractWith(Introspections::getDiscriminatorPropertyName)
