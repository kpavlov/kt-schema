package me.kpavlov.kt.schema.generator.json

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.json.shouldContainJsonKeyValue
import io.kotest.assertions.json.shouldNotContainJsonKey
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeSameInstanceAs
import kotlinx.serialization.json.Json
import kotlin.test.Test

class FunctionCallingSchemaConfigTest {
    data class Address(
        val city: String,
    )

    data class Server(
        val port: Int = 8080,
    )

    object Fixtures {
        @Suppress("UNUSED_PARAMETER", "FunctionOnlyReturningConstant")
        fun sample(
            nickname: String? = null,
            retries: Int = 3,
            address: Address,
        ): String = ""

        @Suppress("UNUSED_PARAMETER", "FunctionOnlyReturningConstant")
        fun start(server: Server): String = ""

        @Suppress("UNUSED_PARAMETER", "FunctionOnlyReturningConstant")
        fun setLevel(level: Byte): String = ""
    }

    @Test
    fun `Strict should require nested properties and omit their defaults`() {
        val schema = ReflectionFunctionCallingSchemaGenerator.Default.generateSchemaString(Fixtures::start)

        schema.shouldContainJsonKeyValue("$.parameters.properties.server.required[0]", "port")
        schema shouldNotContainJsonKey "$.parameters.properties.server.properties.port.default"
    }

    @Test
    fun `Simple should emit nested property defaults and leave them optional`() {
        val generator =
            ReflectionFunctionCallingSchemaGenerator(
                json = Json { encodeDefaults = false },
                config = FunctionCallingSchemaConfig.Simple
            )

        val schema = generator.generateSchemaString(Fixtures::start)

        schema.shouldContainJsonKeyValue("$.parameters.properties.server.properties.port.default", 8080)
        schema.shouldContainJsonKeyValue("$.parameters.properties.server.required", emptyList<String>())
    }

    @Test
    fun `Strict should keep the built-in range of Byte`() {
        val schema = ReflectionFunctionCallingSchemaGenerator.Default.generateSchemaString(Fixtures::setLevel)

        schema.shouldContainJsonKeyValue("$.parameters.properties.level.minimum", -128)
        schema.shouldContainJsonKeyValue("$.parameters.properties.level.maximum", 127)
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
