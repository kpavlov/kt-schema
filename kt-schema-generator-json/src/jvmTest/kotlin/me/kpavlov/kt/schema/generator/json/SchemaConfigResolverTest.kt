package me.kpavlov.kt.schema.generator.json

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeSameInstanceAs
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.NullAndEmptySource
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.Test

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SchemaConfigResolverTest {
    private val classLoader: ClassLoader = SchemaConfigResolverTest::class.java.classLoader
    private val fallbackJsonConfig = JsonSchemaConfig.Default
    private val fallbackFunctionConfig = FunctionCallingSchemaConfig.Simple

    //region Fixtures

    object CustomObjectConfig : JsonSchemaConfig(
        useUnionTypes = false,
        useNullableField = false,
        allowAdditionalProperties = true,
    )

    class NoArgConfig :
        JsonSchemaConfig(
            requireNullableFields = true,
            useUnionTypes = false,
            useNullableField = true,
        )

    @Suppress("unused")
    class ConfigRequiringArguments(
        private val unused: Int,
    ) : JsonSchemaConfig()

    class NotAConfig

    //endregion

    //region Shortcuts

    @ParameterizedTest
    @CsvSource(
        "strict,   Strict",
        "STRICT,   Strict",
        "lenient,  Lenient",
        "LeNiEnT,  Lenient",
        "openapi,  OpenAPI",
        "OpenAPI,  OpenAPI",
    )
    fun `should resolve shortcut to JsonSchemaConfig preset`(
        value: String,
        preset: String,
    ) {
        val expected =
            when (preset) {
                "Strict" -> JsonSchemaConfig.Strict
                "Lenient" -> JsonSchemaConfig.Lenient
                else -> JsonSchemaConfig.OpenAPI
            }

        val actual = SchemaConfigResolver.resolveJsonSchemaConfig(value, fallbackJsonConfig, classLoader)

        actual shouldBeSameInstanceAs expected
    }

    @ParameterizedTest
    @CsvSource(
        "strict,   Strict",
        "STRICT,   Strict",
        "lenient,  Lenient",
        "LeNiEnT,  Lenient",
        "openapi,  OpenAPI",
        "OpenAPI,  OpenAPI",
    )
    fun `should resolve shortcut to FunctionCallingSchemaConfig preset`(
        value: String,
        preset: String,
    ) {
        val expected =
            when (preset) {
                "Strict" -> FunctionCallingSchemaConfig.Strict
                "Lenient" -> FunctionCallingSchemaConfig.Lenient
                else -> FunctionCallingSchemaConfig.OpenAPI
            }

        val actual = SchemaConfigResolver.resolveFunctionCallingConfig(value, fallbackFunctionConfig, classLoader)

        actual shouldBeSameInstanceAs expected
    }

    //endregion

    //region Absent value

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = ["   "])
    fun `should return default for null or blank value`(value: String?) {
        assertSoftly {
            SchemaConfigResolver.resolveJsonSchemaConfig(value, fallbackJsonConfig, classLoader) shouldBeSameInstanceAs
                fallbackJsonConfig
            SchemaConfigResolver.resolveFunctionCallingConfig(
                value,
                fallbackFunctionConfig,
                classLoader,
            ) shouldBeSameInstanceAs fallbackFunctionConfig
        }
    }

    //endregion

    //region Custom classes

    @Test
    fun `should resolve Kotlin object by fully qualified name`() {
        val actual =
            SchemaConfigResolver.resolveJsonSchemaConfig(
                CustomObjectConfig::class.java.name,
                fallbackJsonConfig,
                classLoader,
            )

        actual shouldBeSameInstanceAs CustomObjectConfig
    }

    @Test
    fun `should instantiate class with public no-arg constructor`() {
        val actual =
            SchemaConfigResolver.resolveJsonSchemaConfig(
                NoArgConfig::class.java.name,
                fallbackJsonConfig,
                classLoader,
            )

        actual.shouldBeInstanceOf<NoArgConfig>()
    }

    @Test
    fun `should use FunctionCallingSchemaConfig instance as-is`() {
        val actual =
            SchemaConfigResolver.resolveFunctionCallingConfig(
                FunctionCallingSchemaConfig::class.java.name,
                fallbackFunctionConfig,
                classLoader,
            )

        assertSoftly(actual) {
            strictMode shouldBe true
            allowAdditionalProperties shouldBe false
        }
    }

    @Test
    fun `should wrap plain JsonSchemaConfig for functions without strict mode`() {
        val actual =
            SchemaConfigResolver.resolveFunctionCallingConfig(
                CustomObjectConfig::class.java.name,
                fallbackFunctionConfig,
                classLoader,
            )

        assertSoftly(actual) {
            strictMode shouldBe false
            allowAdditionalProperties shouldBe CustomObjectConfig.allowAdditionalProperties
            respectDefaultPresence shouldBe CustomObjectConfig.respectDefaultPresence
            requireNullableFields shouldBe CustomObjectConfig.requireNullableFields
            useUnionTypes shouldBe CustomObjectConfig.useUnionTypes
            useNullableField shouldBe CustomObjectConfig.useNullableField
            includePolymorphicDiscriminator shouldBe CustomObjectConfig.includePolymorphicDiscriminator
        }
    }

    //endregion

    //region Invalid values

    @ParameterizedTest
    @CsvSource(
        "no.such.Config",
        "strictt",
        "default",
        "Default",
    )
    fun `should fail naming the value and the shortcuts when class is not found`(value: String) {
        val exception =
            shouldThrow<IllegalArgumentException> {
                SchemaConfigResolver.resolveJsonSchemaConfig(value, fallbackJsonConfig, classLoader)
            }

        assertSoftly(exception) {
            message shouldContain value
            message shouldContain "strict, lenient, openapi"
        }
    }

    @Test
    fun `should fail when class is not a JsonSchemaConfig`() {
        val value = NotAConfig::class.java.name

        val exception =
            shouldThrow<IllegalArgumentException> {
                SchemaConfigResolver.resolveJsonSchemaConfig(value, fallbackJsonConfig, classLoader)
            }

        assertSoftly(exception) {
            message shouldContain value
            message shouldContain "strict, lenient, openapi"
        }
    }

    @Test
    fun `should fail when config class has no public no-arg constructor`() {
        val value = ConfigRequiringArguments::class.java.name

        val exception =
            shouldThrow<IllegalArgumentException> {
                SchemaConfigResolver.resolveJsonSchemaConfig(value, fallbackJsonConfig, classLoader)
            }

        exception.message shouldContain value
    }

    @Test
    fun `should fail for functions when class is not a JsonSchemaConfig`() {
        val value = NotAConfig::class.java.name

        val exception =
            shouldThrow<IllegalArgumentException> {
                SchemaConfigResolver.resolveFunctionCallingConfig(value, fallbackFunctionConfig, classLoader)
            }

        exception.message shouldContain value
    }

    //endregion

    @Test
    fun `should expose the processor option name`() {
        SchemaConfigResolver.OPTION shouldBe "me.kpavlov.kt.schema.config"
    }
}
