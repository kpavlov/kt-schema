package me.kpavlov.kt.schema.integration.type

import me.kpavlov.kt.schema.Schema

/**
 * Local marker annotation matching the default `nullableAnnotationNames`/`optionalAnnotationNames`
 * config ("Nullable") by simple name — mirrors javax.annotation.Nullable, jakarta.annotation.Nullable, etc.
 */
@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY, AnnotationTarget.VALUE_PARAMETER)
annotation class Nullable

// Type name matches the default `*Opt` glob pattern.
data class EmailOpt(
    val value: String,
)

@Schema
data class NullableConvention(
    val name: String,
    val email: EmailOpt,
    @Nullable
    val phone: String,
)
