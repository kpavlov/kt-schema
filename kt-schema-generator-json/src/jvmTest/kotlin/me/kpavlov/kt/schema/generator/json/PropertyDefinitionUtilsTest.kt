package me.kpavlov.kt.schema.generator.json

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import me.kpavlov.kt.schema.generator.core.ExperimentalSchemaGeneratorApi
import me.kpavlov.kt.schema.generator.core.ir.Constraint
import me.kpavlov.kt.schema.generator.core.ir.Literal
import me.kpavlov.kt.schema.generator.core.ir.PrimitiveKind
import me.kpavlov.kt.schema.generator.core.ir.PrimitiveNode
import me.kpavlov.kt.schema.generator.core.ir.Property
import me.kpavlov.kt.schema.generator.core.ir.PropertyValue
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
import java.util.stream.Stream

@OptIn(ExperimentalSchemaGeneratorApi::class)
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

    private fun stringProperty(value: PropertyValue): Property =
        Property(
            name = "x",
            type = TypeRef.Inline(PrimitiveNode(PrimitiveKind.STRING)),
            value = value,
        )

    @Test
    fun `applyDefaultOrConst should set const value for constant properties`() {
        val def = StringPropertyDefinition()
        val property = stringProperty(PropertyValue.Const(Literal.Str("FIXED")))
        applyDefaultOrConst(def, property, isRequired = true, omitsNullMarker = false) shouldBe
            def.copy(constValue = JsonPrimitive("FIXED"))
    }

    @Test
    fun `applyDefaultOrConst should set default value for optional properties with a default`() {
        val def = StringPropertyDefinition()
        val property = stringProperty(PropertyValue.Default(Literal.Str("FALLBACK")))
        applyDefaultOrConst(def, property, isRequired = false, omitsNullMarker = false) shouldBe
            def.copy(default = JsonPrimitive("FALLBACK"))
    }

    @Test
    fun `applyDefaultOrConst should leave required properties without applying their default`() {
        val def = StringPropertyDefinition()
        val property = stringProperty(PropertyValue.Default(Literal.Str("IGNORED")))
        applyDefaultOrConst(def, property, isRequired = true, omitsNullMarker = false) shouldBe def
    }

    @ParameterizedTest
    @MethodSource("descriptionPropertyDefinitionProvider")
    fun `setDescription should update description`(propertyDef: PropertyDefinition) {
        val updated = setDescription(propertyDef, "desc") as? CommonSchemaAttributes
        if (updated != null) {
            updated.description shouldBe "desc"
        }
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

    //region applyConstraints

    data class ConstraintCase(
        val definition: PropertyDefinition,
        val constraint: Constraint,
        val expected: PropertyDefinition,
    )

    fun constraintCases(): Stream<ConstraintCase> {
        val string = StringPropertyDefinition()
        val integer = NumericPropertyDefinition(type = listOf("integer"))
        val array = ArrayPropertyDefinition()
        val obj = ObjectPropertyDefinition()
        val reference = ReferencePropertyDefinition(ref = "#/\$defs/Other")
        return Stream.of(
            ConstraintCase(string, Constraint.Size(min = 2, max = 5), string.copy(minLength = 2, maxLength = 5)),
            ConstraintCase(string, Constraint.Pattern("^[a-z]+$"), string.copy(pattern = "^[a-z]+$")),
            ConstraintCase(string, Constraint.Format("email"), string.copy(format = "email")),
            ConstraintCase(string, Constraint.Minimum(Literal.Integer(1)), string),
            ConstraintCase(integer, Constraint.Minimum(Literal.Integer(1)), integer.copy(minimum = 1.0)),
            ConstraintCase(
                integer,
                Constraint.Minimum(Literal.Decimal(0.5), exclusive = true),
                integer.copy(exclusiveMinimum = 0.5),
            ),
            ConstraintCase(integer, Constraint.Maximum(Literal.Integer(9)), integer.copy(maximum = 9.0)),
            ConstraintCase(
                integer,
                Constraint.Maximum(Literal.Integer(9), exclusive = true),
                integer.copy(exclusiveMaximum = 9.0),
            ),
            ConstraintCase(integer, Constraint.Size(max = 3), integer),
            ConstraintCase(array, Constraint.Size(min = 1, max = 3), array.copy(minItems = 1, maxItems = 3)),
            ConstraintCase(array, Constraint.UniqueItems, array.copy(uniqueItems = true)),
            ConstraintCase(array, Constraint.Pattern("x"), array),
            ConstraintCase(string, Constraint.UniqueItems, string),
            ConstraintCase(obj, Constraint.Size(min = 1), obj.copy(minProperties = 1)),
            ConstraintCase(reference, Constraint.Size(min = 1), reference),
            ConstraintCase(BooleanPropertyDefinition(), Constraint.Format("x"), BooleanPropertyDefinition()),
        )
    }

    @ParameterizedTest
    @MethodSource("constraintCases")
    fun `applyConstraints maps a constraint onto the matching definition`(case: ConstraintCase) {
        case.definition.applyConstraints(listOf(case.constraint)) shouldBe case.expected
    }

    @Test
    fun `applyConstraints applies to non-null branches of a nullable union only`() {
        // Arrange
        val nullBranch = StringPropertyDefinition(type = listOf("null"))
        val union = AnyOfPropertyDefinition(anyOf = listOf(StringPropertyDefinition(), nullBranch))

        // Act
        val result = union.applyConstraints(listOf(Constraint.Size(max = 4)))

        // Assert
        result shouldBe union.copy(anyOf = listOf(StringPropertyDefinition(maxLength = 4), nullBranch))
    }

    @Test
    fun `applyConstraints returns the same instance for an empty list`() {
        val string = StringPropertyDefinition()

        string.applyConstraints(emptyList()) shouldBeSameInstanceAs string
    }

    //endregion
}
