package me.kpavlov.kt.schema.generator.core

internal actual object Config {
    actual val descriptionAnnotationNames: List<String>
        get() = ConfigDefaults.descriptionAnnotationNames
    actual val descriptionValueAttributes: List<String>
        get() = ConfigDefaults.descriptionValueAttributes
    actual val ignoreAnnotationNames: List<String>
        get() = ConfigDefaults.ignoreAnnotationNames
    actual val nameAnnotationNames: List<String>
        get() = ConfigDefaults.nameAnnotationNames
    actual val nameValueAttributes: List<String>
        get() = ConfigDefaults.nameValueAttributes
    actual val opaqueTypeNames: Set<String>
        get() = DEFAULT_OPAQUE_TYPE_NAMES
    actual val nullableAnnotationNames: List<String>
        get() = ConfigDefaults.nullableAnnotationNames
    actual val nullableTypeNamePatterns: List<String>
        get() = ConfigDefaults.nullableTypeNamePatterns
    actual val optionalAnnotationNames: List<String>
        get() = ConfigDefaults.optionalAnnotationNames
    actual val optionalTypeNamePatterns: List<String>
        get() = ConfigDefaults.optionalTypeNamePatterns
    actual val enumDefaultAnnotationNames: List<String>
        get() = ConfigDefaults.enumDefaultAnnotationNames
    actual val defaultValueAnnotationNames: List<String>
        get() = ConfigDefaults.defaultValueAnnotationNames
    actual val defaultValueAttributes: List<String>
        get() = ConfigDefaults.defaultValueAttributes
    actual val constraintAnnotationNames: List<String>
        get() = ConfigDefaults.constraintAnnotationNames
    actual val discriminatorAnnotationNames: List<String>
        get() = ConfigDefaults.discriminatorAnnotationNames
    actual val discriminatorValueAttributes: List<String>
        get() = ConfigDefaults.discriminatorValueAttributes
}
