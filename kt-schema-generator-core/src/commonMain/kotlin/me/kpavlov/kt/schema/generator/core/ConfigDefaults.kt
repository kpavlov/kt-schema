package me.kpavlov.kt.schema.generator.core

/**
 * Built-in values of every [Config] key, shared by all platforms. JVM overrides them from
 * `kt-schema.properties` and falls back here when loading fails; the other platforms use them as is.
 */
internal object ConfigDefaults {
    val descriptionAnnotationNames: List<String> =
        listOf("description", "llmdescription", "jsonpropertydescription", "jsonclassdescription", "p")
    val descriptionValueAttributes: List<String> = listOf("value", "description")
    val ignoreAnnotationNames: List<String> =
        listOf("schemaignore", "serialschemaignore", "jsonignoretype", "jsonignore")
    val nameAnnotationNames: List<String> =
        listOf(
            "kotlinx.serialization.SerialName",
            "com.fasterxml.jackson.annotation.JsonProperty",
            "com.fasterxml.jackson.annotation.JsonTypeName",
        )
    val nameValueAttributes: List<String> = listOf("value")
    val nullableAnnotationNames: List<String> = listOf("nullable")
    val nullableTypeNamePatterns: List<String> = listOf("*Opt")
    val optionalAnnotationNames: List<String> = emptyList()
    val optionalTypeNamePatterns: List<String> = emptyList()
    val enumDefaultAnnotationNames: List<String> = listOf("com.fasterxml.jackson.annotation.JsonEnumDefaultValue")
    val defaultValueAnnotationNames: List<String> = listOf("com.fasterxml.jackson.annotation.JsonProperty")
    val defaultValueAttributes: List<String> = listOf("defaultValue")
    val constraintAnnotationNames: List<String> =
        listOf("jakarta.validation.constraints.*", "javax.validation.constraints.*")
    val discriminatorAnnotationNames: List<String> =
        listOf("kotlinx.serialization.json.JsonClassDiscriminator", "com.fasterxml.jackson.annotation.JsonTypeInfo")
    val discriminatorValueAttributes: List<String> = listOf("discriminator", "property")
}
