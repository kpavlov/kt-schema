package me.kpavlov.kt.schema.ksp.generator

import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import me.kpavlov.kt.schema.generator.core.ir.ObjectNode
import me.kpavlov.kt.schema.generator.core.ir.SchemaIntrospector
import me.kpavlov.kt.schema.generator.core.ir.TypeGraph
import me.kpavlov.kt.schema.generator.core.ir.TypeId
import me.kpavlov.kt.schema.generator.core.ir.TypeRef
import me.kpavlov.kt.schema.generator.json.FunctionCallingSchemaConfig
import me.kpavlov.kt.schema.generator.json.TypeGraphToFunctionCallingSchemaTransformer
import me.kpavlov.kt.schema.json.FunctionCallingSchema
import me.kpavlov.kt.schema.ksp.ksName
import kotlin.test.Test

class UnifiedKspSchemaGeneratorTest {
    private val function =
        mockk<KSFunctionDeclaration> {
            every { qualifiedName } returns ksName("test.greet")
            every { simpleName } returns ksName("greet")
            every { packageName } returns ksName("test")
        }

    private val introspector =
        object : SchemaIntrospector<KSFunctionDeclaration, Unit> {
            override val config = Unit

            override fun introspect(root: KSFunctionDeclaration): TypeGraph =
                TypeGraph(
                    root = TypeRef.Ref(TypeId("greet")),
                    nodes =
                        mapOf(
                            TypeId("greet") to
                                ObjectNode(
                                    name = "greet",
                                    properties = emptyList(),
                                    required = emptySet(),
                                ),
                        ),
                )
        }

    private fun functionSchema(config: FunctionCallingSchemaConfig): JsonObject {
        val generator =
            UnifiedKspSchemaGenerator(
                KspSchemaGeneratorConfig(
                    introspector = introspector,
                    transformer = TypeGraphToFunctionCallingSchemaTransformer(config),
                    serializer = FunctionCallingSchema.serializer(),
                    jsonPrettyPrint = false,
                    jsonEncodeDefaults = false,
                ),
            )
        return Json.parseToJsonElement(generator.generateSchemaString(function)).jsonObject
    }

    @Test
    fun `should omit strict flag for non-strict function schema`() {
        val schema = functionSchema(FunctionCallingSchemaConfig.Lenient)

        schema.keys shouldNotContain "strict"
    }

    @Test
    fun `should emit strict flag for strict function schema`() {
        val schema = functionSchema(FunctionCallingSchemaConfig.Strict)

        schema["strict"] shouldBe JsonPrimitive(true)
    }
}
