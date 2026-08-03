package me.kpavlov.kt.schema.generator.core.ir

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class IntrospectionsTest {

    //region Description extraction — simple name matching

    @ParameterizedTest
    @CsvSource(
        "Description, value",
        "SerialDescription, value",
        "LLMDescription, value",
        "JsonPropertyDescription, description",
        "JsonClassDescription, value",
        "P, description",
    )
    fun `extracts description from single-element annotation`(
        name: String,
        attribute: String,
    ) {
        Introspections.getDescriptionFromAnnotation(
            simpleName = name,
            qualifiedName = null,
            listOf(attribute to "My Description"),
        ) shouldBe "My Description"
    }

    //endregion

    //region Multi-element LLMDescription regression

    @Test
    fun `extracts description from LLMDescription with description= style when value is empty`() {
        val result =
            Introspections.getDescriptionFromAnnotation(
                simpleName = "LLMDescription",
                qualifiedName = null,
                annotationArguments = listOf("value" to "", "description" to "Product identifier"),
            )
        result shouldBe "Product identifier"
    }

    @Test
    fun `extracts description from LLMDescription with value= shorthand style`() {
        val result =
            Introspections.getDescriptionFromAnnotation(
                simpleName = "LLMDescription",
                qualifiedName = null,
                annotationArguments = listOf("value" to "Product name", "description" to ""),
            )
        result shouldBe "Product name"
    }

    @Test
    fun `attribute priority follows config order regardless of annotation argument order`() {
        // Config attribute order is: value, description
        // Annotation argument order has "description" first, but "value" should win because
        // it has higher priority in the config
        val result =
            Introspections.getDescriptionFromAnnotation(
                simpleName = "LLMDescription",
                qualifiedName = null,
                annotationArguments = listOf("description" to "lower priority", "value" to "higher priority"),
            )
        result shouldBe "higher priority"
    }

    //endregion

    //region Description negative cases

    @ParameterizedTest
    @CsvSource(
        "UnknownAnnotation, value, Some text",
        "Description, unknownAttr, Some text",
        "Description, value, ''",
    )
    fun `getDescriptionFromAnnotation returns null for non-matching cases`(
        name: String,
        attribute: String,
        text: String,
    ) {
        Introspections.getDescriptionFromAnnotation(
            simpleName = name,
            qualifiedName = null,
            annotationArguments = listOf(attribute to text),
        ) shouldBe null
    }

    //endregion

    //region Ignore annotation recognition

    @ParameterizedTest
    @CsvSource(
        "SchemaIgnore",
        "SerialSchemaIgnore",
        "JsonIgnoreType",
        "JsonIgnore",
    )
    fun `recognizes ignore annotations by simple name`(name: String) {
        Introspections.isIgnoreAnnotation(name) shouldBe true
    }

    @ParameterizedTest
    @CsvSource(
        "schemaignore",
        "SCHEMAIGNORE",
        "SchemaIgnore",
        "jsonignoretype",
        "JSONIGNORETYPE",
    )
    fun `ignore annotation matching is case-insensitive`(name: String) {
        Introspections.isIgnoreAnnotation(name) shouldBe true
    }

    @ParameterizedTest
    @CsvSource(
        "Ignore",
        "JsonIgnoreProperties",
        "Transient",
        "Description",
        "UnknownAnnotation",
    )
    fun `does not match unrecognized annotation names as ignore`(name: String) {
        Introspections.isIgnoreAnnotation(name) shouldBe false
    }

    //endregion

    //region FQN matching — backward compatibility

    @Test
    fun `simple-name description annotation still matches when qualifiedName is provided`() {
        Introspections.getDescriptionFromAnnotation(
            simpleName = "Description",
            qualifiedName = "me.kpavlov.kt.schema.Description",
            annotationArguments = listOf("value" to "test"),
        ) shouldBe "test"
    }

    @Test
    fun `simple-name ignore annotation still matches when qualifiedName is provided`() {
        Introspections.isIgnoreAnnotation(
            simpleName = "SchemaIgnore",
            qualifiedName = "me.kpavlov.kt.schema.SchemaIgnore",
        ) shouldBe true
    }

    //endregion

    //region Name override extraction

    @ParameterizedTest
    @CsvSource(
        "SerialName, kotlinx.serialization.SerialName, custom_name, custom_name",
        "SerialName, kotlinx.serialization.SerialName, user_email, user_email",
    )
    fun `getNameOverride extracts value when FQN matches`(
        simpleName: String,
        qualifiedName: String,
        inputValue: String,
        expectedResult: String,
    ) {
        Introspections.getNameOverride(
            simpleName = simpleName,
            qualifiedName = qualifiedName,
            annotationArguments = listOf("value" to inputValue),
        ) shouldBe expectedResult
    }

    @ParameterizedTest
    @CsvSource(
        "SomeOther, com.example.SomeOther, custom_name",
        "SerialName, , custom_name",
        "serialname, kotlinx.serialization.serialname, custom_name",
        "SerialName, kotlinx.serialization.SerialName, ''",
        "JsonProperty, com.fasterxml.jackson.annotation.JsonProperty, ''",
    )
    fun `getNameOverride returns null for non-matching cases`(
        simpleName: String,
        qualifiedName: String?,
        inputValue: String,
    ) {
        Introspections.getNameOverride(
            simpleName = simpleName,
            qualifiedName = qualifiedName?.takeIf { it.isNotEmpty() },
            annotationArguments = listOf("value" to inputValue),
        ) shouldBe null
    }

    //endregion

    //region Jackson name override extraction

    @ParameterizedTest
    @CsvSource(
        "JsonProperty, com.fasterxml.jackson.annotation.JsonProperty, user_email, user_email",
        "JsonProperty, com.fasterxml.jackson.annotation.JsonProperty, productId, productId",
        "JsonTypeName, com.fasterxml.jackson.annotation.JsonTypeName, cat, cat",
    )
    fun `getNameOverride extracts value from Jackson annotations when FQN matches`(
        simpleName: String,
        qualifiedName: String,
        inputValue: String,
        expectedResult: String,
    ) {
        Introspections.getNameOverride(
            simpleName = simpleName,
            qualifiedName = qualifiedName,
            annotationArguments = listOf("value" to inputValue),
        ) shouldBe expectedResult
    }

    @ParameterizedTest
    @CsvSource(
        "JsonProperty, JsonProperty, custom_name",
        "JsonTypeName, JsonTypeName, custom_name",
        "JsonProperty, com.fasterxml.jackson.annotation.JsonPropertyDescription, custom_name",
    )
    fun `getNameOverride matches Jackson annotations only by FQN`(
        simpleName: String,
        qualifiedName: String?,
        inputValue: String,
    ) {
        Introspections.getNameOverride(
            simpleName = simpleName,
            qualifiedName = qualifiedName,
            annotationArguments = listOf("value" to inputValue),
        ) shouldBe null
    }

    //endregion

    //region Nullable / optional annotation recognition

    @ParameterizedTest
    @CsvSource(
        "Nullable",
        "nullable",
        "NULLABLE",
    )
    fun `recognizes nullable but not optional annotations by default`(name: String) {
        Introspections.isNullableAnnotation(name) shouldBe true
        Introspections.isOptionalAnnotation(name) shouldBe false
    }

    @ParameterizedTest
    @CsvSource(
        "NotNull",
        "Nonnull",
        "Description",
        "UnknownAnnotation",
    )
    fun `does not match unrecognized annotation names as nullable or optional`(name: String) {
        Introspections.isNullableAnnotation(name) shouldBe false
        Introspections.isOptionalAnnotation(name) shouldBe false
    }

    @Test
    fun `simple-name nullable annotation still matches when qualifiedName is provided`() {
        Introspections.isNullableAnnotation(
            simpleName = "Nullable",
            qualifiedName = "javax.annotation.Nullable",
        ) shouldBe true
    }

    //endregion

    //region Nullable / optional type-name pattern matching

    @ParameterizedTest
    @CsvSource(
        "EmailOpt",
        "JsonNodeOpt",
        "Opt",
    )
    fun `recognizes type names matching the default nullable Opt glob pattern`(name: String) {
        Introspections.isNullableTypeName(name) shouldBe true
    }

    @ParameterizedTest
    @CsvSource(
        "Email",
        "Optional",
        "emailopt",
        "EMAILOPT",
    )
    fun `does not match type names that don't fit the Opt glob pattern`(name: String) {
        Introspections.isNullableTypeName(name) shouldBe false
    }

    @Test
    fun `type-name pattern matching returns false for null simpleName`() {
        Introspections.isNullableTypeName(null) shouldBe false
        Introspections.isOptionalTypeName(null) shouldBe false
    }

    //endregion

    //region Enum default annotation recognition

    @Test
    fun `recognizes JsonEnumDefaultValue by FQN as enum default marker`() {
        Introspections.isEnumDefaultAnnotation(
            simpleName = "JsonEnumDefaultValue",
            qualifiedName = "com.fasterxml.jackson.annotation.JsonEnumDefaultValue",
        ) shouldBe true
    }

    @ParameterizedTest
    @CsvSource(
        "JsonEnumDefaultValue, com.example.JsonEnumDefaultValue",
        "SomeOtherAnnotation, com.fasterxml.jackson.annotation.SomeOtherAnnotation",
        "JsonEnumDefaultValue, ",
    )
    fun `does not match unrecognized annotations as enum default marker`(
        simpleName: String,
        qualifiedName: String?,
    ) {
        Introspections.isEnumDefaultAnnotation(
            simpleName = simpleName,
            qualifiedName = qualifiedName?.takeIf { it.isNotEmpty() },
        ) shouldBe false
    }

    //endregion

    //region Default value annotation extraction

    @ParameterizedTest
    @CsvSource(
        "JsonProperty, com.fasterxml.jackson.annotation.JsonProperty, ACTIVE, ACTIVE",
        "JsonProperty, com.fasterxml.jackson.annotation.JsonProperty, 30, 30",
    )
    fun `getDefaultValueFromAnnotation extracts defaultValue attribute when FQN matches`(
        simpleName: String,
        qualifiedName: String,
        inputValue: String,
        expectedResult: String,
    ) {
        Introspections.getDefaultValueFromAnnotation(
            simpleName = simpleName,
            qualifiedName = qualifiedName,
            annotationArguments = listOf("defaultValue" to inputValue),
        ) shouldBe expectedResult
    }

    @ParameterizedTest
    @CsvSource(
        "JsonProperty, JsonProperty, ACTIVE",
        "JsonProperty, com.fasterxml.jackson.annotation.JsonPropertyDescription, ACTIVE",
        "SomeOther, com.example.SomeOther, ACTIVE",
    )
    fun `getDefaultValueFromAnnotation returns null for non-matching cases`(
        simpleName: String,
        qualifiedName: String,
        inputValue: String,
    ) {
        Introspections.getDefaultValueFromAnnotation(
            simpleName = simpleName,
            qualifiedName = qualifiedName,
            annotationArguments = listOf("defaultValue" to inputValue),
        ) shouldBe null
    }

    @Test
    fun `getDefaultValueFromAnnotation returns null when defaultValue attribute is empty`() {
        Introspections.getDefaultValueFromAnnotation(
            simpleName = "JsonProperty",
            qualifiedName = "com.fasterxml.jackson.annotation.JsonProperty",
            annotationArguments = listOf("defaultValue" to ""),
        ) shouldBe null
    }

    //endregion
}
