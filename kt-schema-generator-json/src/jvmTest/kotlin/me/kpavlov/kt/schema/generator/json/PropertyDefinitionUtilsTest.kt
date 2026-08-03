package me.kpavlov.kt.schema.generator.json

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import me.kpavlov.kt.schema.generator.core.ir.PrimitiveKind
import me.kpavlov.kt.schema.generator.core.ir.PrimitiveNode
import me.kpavlov.kt.schema.generator.core.ir.Property
import me.kpavlov.kt.schema.generator.core.ir.TypeRef
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
import org.junit.jupiter.params.provider.CsvSource
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

    @Test
    fun `setDefaultValue should handle Reference and OneOf definitions`() {
        val refProp = ReferencePropertyDefinition(ref = "#/defs/Status")
        setDefaultValue(refProp, "ACTIVE") shouldBe refProp.copy(default = JsonPrimitive("ACTIVE"))

        val oneOfProp = OneOfPropertyDefinition(oneOf = emptyList())
        setDefaultValue(oneOfProp, "ACTIVE") shouldBe oneOfProp.copy(default = JsonPrimitive("ACTIVE"))
    }

    @Test
    fun `setDefaultValue coerces an integer-looking annotation string to a JSON number for numeric properties`() {
        val numProp = NumericPropertyDefinition(type = listOf("integer"))
        setDefaultValue(numProp, "30") shouldBe numProp.copy(default = JsonPrimitive(30L))
    }

    @Test
    fun `setDefaultValue coerces a decimal-looking annotation string to a JSON number for numeric properties`() {
        val numProp = NumericPropertyDefinition(type = listOf("number"))
        setDefaultValue(numProp, "3.5") shouldBe numProp.copy(default = JsonPrimitive(3.5))
    }

    @Test
    fun `setDefaultValue coerces a whole-number decimal string to a JSON integer for integer properties`() {
        val numProp = NumericPropertyDefinition(type = listOf("integer"))
        setDefaultValue(numProp, "30.0") shouldBe numProp.copy(default = JsonPrimitive(30L))
    }

    @Test
    fun `setDefaultValue throws when a non-integral annotation string is used for an integer property`() {
        val numProp = NumericPropertyDefinition(type = listOf("integer"))
        shouldThrow<IllegalArgumentException> { setDefaultValue(numProp, "3.5") }
    }

    @Test
    fun `setDefaultValue throws when an annotation string is not a valid number`() {
        val numProp = NumericPropertyDefinition(type = listOf("number"))
        shouldThrow<IllegalArgumentException> { setDefaultValue(numProp, "not-a-number") }
    }

    @Test
    fun `setDefaultValue throws when an annotation string is a non-finite number`() {
        val numProp = NumericPropertyDefinition(type = listOf("number"))
        shouldThrow<IllegalArgumentException> { setDefaultValue(numProp, "NaN") }
    }

    @Test
    fun `setDefaultValue throws when an annotation string is not a valid boolean`() {
        val boolProp = BooleanPropertyDefinition()
        shouldThrow<IllegalArgumentException> { setDefaultValue(boolProp, "yes") }
    }

    @ParameterizedTest
    @CsvSource(
        "true, true",
        "false, false",
    )
    fun `setDefaultValue coerces a boolean-looking annotation string to a JSON boolean for boolean properties`(
        rawValue: String,
        expected: Boolean,
    ) {
        val boolProp = BooleanPropertyDefinition()
        setDefaultValue(boolProp, rawValue) shouldBe boolProp.copy(default = JsonPrimitive(expected))
    }

    private fun stringProperty(defaultValue: Any? = null, isConstant: Boolean = false): Property =
        Property(
            name = "x",
            type = TypeRef.Inline(PrimitiveNode(PrimitiveKind.STRING)),
            defaultValue = defaultValue,
            isConstant = isConstant,
        )

    @Test
    fun `applyDefaultOrConst should set const value for constant properties`() {
        val def = StringPropertyDefinition()
        applyDefaultOrConst(def, stringProperty(defaultValue = "FIXED", isConstant = true), isRequired = true) shouldBe
            def.copy(constValue = JsonPrimitive("FIXED"))
    }

    @Test
    fun `applyDefaultOrConst should set default value for optional properties with a default`() {
        val def = StringPropertyDefinition()
        applyDefaultOrConst(def, stringProperty(defaultValue = "FALLBACK"), isRequired = false) shouldBe
            def.copy(default = JsonPrimitive("FALLBACK"))
    }

    @Test
    fun `applyDefaultOrConst should leave required properties without applying their default`() {
        val def = StringPropertyDefinition()
        applyDefaultOrConst(def, stringProperty(defaultValue = "IGNORED"), isRequired = true) shouldBe def
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
