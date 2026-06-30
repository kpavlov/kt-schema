package me.kpavlov.kt.schema.generator.json

import io.kotest.matchers.shouldBe
import me.kpavlov.kt.schema.json.AllOfPropertyDefinition
import me.kpavlov.kt.schema.json.AnyOfPropertyDefinition
import me.kpavlov.kt.schema.json.ArrayPropertyDefinition
import me.kpavlov.kt.schema.json.BooleanPropertyDefinition
import me.kpavlov.kt.schema.json.BooleanSchemaDefinition
import me.kpavlov.kt.schema.json.CommonSchemaAttributes
import me.kpavlov.kt.schema.json.GenericPropertyDefinition
import me.kpavlov.kt.schema.json.JsonSchema
import me.kpavlov.kt.schema.json.NumericPropertyDefinition
import me.kpavlov.kt.schema.json.ObjectPropertyDefinition
import me.kpavlov.kt.schema.json.OneOfPropertyDefinition
import me.kpavlov.kt.schema.json.PropertyDefinition
import me.kpavlov.kt.schema.json.ReferencePropertyDefinition
import me.kpavlov.kt.schema.json.StringPropertyDefinition
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PropertyDefinitionUtilsTest {
    @Test
    fun `setDefaultValue should handle different types`() {
        val stringProp = StringPropertyDefinition()
        setDefaultValue(stringProp, "hello") shouldBe stringProp.copy(default = JsonPrimitive("hello"))

        val numProp = NumericPropertyDefinition(type = listOf("integer"))
        setDefaultValue(numProp, 42) shouldBe numProp.copy(default = JsonPrimitive(42))

        val boolProp = BooleanPropertyDefinition()
        setDefaultValue(boolProp, true) shouldBe boolProp.copy(default = JsonPrimitive(true))

        setDefaultValue(stringProp, null) shouldBe stringProp.copy(default = JsonNull)
    }

    @ParameterizedTest
    @MethodSource("descriptionPropertyDefinitionProvider")
    fun `setDescription should update description`(propertyDef: PropertyDefinition) {
        val updated = setDescription(propertyDef, "desc") as? CommonSchemaAttributes
        if (updated != null) {
            updated.description shouldBe "desc"
        }
    }

    @Test
    fun `removeNullableFlag should set nullable to null`() {
        val stringProp = StringPropertyDefinition(nullable = true)
        removeNullableFlag(stringProp) shouldBe stringProp.copy(nullable = null)
    }

    fun descriptionPropertyDefinitionProvider(): Array<PropertyDefinition> =
        arrayOf(
            StringPropertyDefinition(description = null),
            NumericPropertyDefinition(description = null, type = listOf("integer")),
            BooleanPropertyDefinition(description = null),
            ArrayPropertyDefinition(description = null),
            ObjectPropertyDefinition(description = null),
            AnyOfPropertyDefinition(
                description = null,
                anyOf = emptyList(),
            ),
            OneOfPropertyDefinition(
                description = null,
                oneOf = emptyList(),
            ),
            GenericPropertyDefinition(),
            AllOfPropertyDefinition(
                description = null,
                allOf = emptyList(),
            ),
            ReferencePropertyDefinition(description = null),
            JsonSchema(description = null),
            BooleanSchemaDefinition(false),
        )
}
