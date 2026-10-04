package me.kpavlov.kt.schema.generator.json

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.matchers.string.shouldContain
import kotlinx.serialization.json.Json
import kotlin.test.Test

class FunctionCallingSchemaConfigTest {
    data class Address(
        val city: String,
    )

    object Fixtures {
        @Suppress("UNUSED_PARAMETER", "FunctionOnlyReturningConstant")
        fun sample(
            nickname: String? = null,
            retries: Int = 3,
            address: Address,
        ): String = ""
    }

    @Test
    @Suppress("DEPRECATION")
    fun `Default should be the Strict instance`() {
        FunctionCallingSchemaConfig.Default shouldBeSameInstanceAs FunctionCallingSchemaConfig.Strict
    }

    @Test
    fun `Strict should produce the same schema as the implicit strict-mode config`() {
        val json = Json { encodeDefaults = false }
        val implicit =
            ReflectionFunctionCallingSchemaGenerator(json, FunctionCallingSchemaConfig(strictMode = true))
        val strict = ReflectionFunctionCallingSchemaGenerator(json, FunctionCallingSchemaConfig.Strict)

        strict.generateSchemaString(Fixtures::sample) shouldBe implicit.generateSchemaString(Fixtures::sample)
    }

    @Test
    fun `should deny additional properties by default`() {
        FunctionCallingSchemaConfig().allowAdditionalProperties shouldBe false
    }

    @Test
    fun `should keep the six-argument constructor for binary compatibility`() {
        val constructor = FunctionCallingSchemaConfig::class.java.getConstructor(*Array(6) { Boolean::class.java })

        val config = constructor.newInstance(true, false, true, false, true, false)

        assertSoftly(config) {
            respectDefaultPresence shouldBe true
            requireNullableFields shouldBe false
            useUnionTypes shouldBe true
            useNullableField shouldBe false
            includePolymorphicDiscriminator shouldBe true
            strictMode shouldBe false
            allowAdditionalProperties shouldBe false
        }
    }

    @Test
    fun `should reject strictMode combined with allowAdditionalProperties`() {
        val exception =
            shouldThrow<IllegalArgumentException> {
                FunctionCallingSchemaConfig(strictMode = true, allowAdditionalProperties = true)
            }

        exception.message shouldContain "strictMode requires allowAdditionalProperties = false"
    }

    @Test
    fun `should accept allowAdditionalProperties when strictMode is off`() {
        val config = FunctionCallingSchemaConfig(strictMode = false, allowAdditionalProperties = true)

        config.allowAdditionalProperties shouldBe true
    }

    @Test
    fun `Lenient preset should mirror JsonSchemaConfig Lenient without strict mode`() {
        val lenient = JsonSchemaConfig.Lenient

        assertSoftly(FunctionCallingSchemaConfig.Lenient) {
            respectDefaultPresence shouldBe lenient.respectDefaultPresence
            requireNullableFields shouldBe lenient.requireNullableFields
            useUnionTypes shouldBe lenient.useUnionTypes
            useNullableField shouldBe lenient.useNullableField
            includePolymorphicDiscriminator shouldBe lenient.includePolymorphicDiscriminator
            allowAdditionalProperties shouldBe lenient.allowAdditionalProperties
            strictMode shouldBe false
        }
    }

    @Test
    fun `OpenAPI preset should mirror JsonSchemaConfig OpenAPI without strict mode`() {
        val openApi = JsonSchemaConfig.OpenAPI

        assertSoftly(FunctionCallingSchemaConfig.OpenAPI) {
            respectDefaultPresence shouldBe openApi.respectDefaultPresence
            requireNullableFields shouldBe openApi.requireNullableFields
            useUnionTypes shouldBe openApi.useUnionTypes
            useNullableField shouldBe openApi.useNullableField
            includePolymorphicDiscriminator shouldBe openApi.includePolymorphicDiscriminator
            allowAdditionalProperties shouldBe openApi.allowAdditionalProperties
            strictMode shouldBe false
        }
    }
}
