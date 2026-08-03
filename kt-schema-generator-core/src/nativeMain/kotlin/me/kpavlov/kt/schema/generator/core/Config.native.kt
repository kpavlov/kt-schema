package me.kpavlov.kt.schema.generator.core

internal actual object Config {
    actual val descriptionAnnotationNames: List<String>
        get() = listOf("Description")
    actual val descriptionValueAttributes: List<String>
        get() = listOf("value", "description")
    actual val ignoreAnnotationNames: List<String>
        get() = listOf("schemaignore")
    actual val nameAnnotationNames: List<String>
        get() = listOf("kotlinx.serialization.SerialName")
    actual val nameValueAttributes: List<String>
        get() = listOf("value")
    actual val opaqueTypeNames: Set<String>
        get() = DEFAULT_OPAQUE_TYPE_NAMES
    actual val nullableAnnotationNames: List<String>
        get() = listOf("Nullable")
    actual val nullableTypeNamePatterns: List<String>
        get() = listOf("*Opt")
    actual val optionalAnnotationNames: List<String>
        get() = emptyList()
    actual val optionalTypeNamePatterns: List<String>
        get() = emptyList()
    actual val enumDefaultAnnotationNames: List<String>
        get() = listOf("com.fasterxml.jackson.annotation.JsonEnumDefaultValue")
    actual val defaultValueAnnotationNames: List<String>
        get() = listOf("com.fasterxml.jackson.annotation.JsonProperty")
    actual val defaultValueAttributes: List<String>
        get() = listOf("defaultValue")
}
