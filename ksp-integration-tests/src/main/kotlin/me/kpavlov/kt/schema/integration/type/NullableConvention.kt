package me.kpavlov.kt.schema.integration.type

import me.kpavlov.kt.schema.Schema

/**
 * Local marker annotation matching the default `nullableAnnotationNames`/`optionalAnnotationNames`
 * config ("Nullable") by simple name — mirrors javax.annotation.Nullable, jakarta.annotation.Nullable, etc.
 */
@Target(
    AnnotationTarget.FIELD,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.VALUE_PARAMETER,
    AnnotationTarget.PROPERTY_GETTER,
)
annotation class Nullable

@Schema
data class NullableConvention(
    val name: String,
    @Nullable
    val phone: String,
    @get:Nullable
    val fax: String,
)
