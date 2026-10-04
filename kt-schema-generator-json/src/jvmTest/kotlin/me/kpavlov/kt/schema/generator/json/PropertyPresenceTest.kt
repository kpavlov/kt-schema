package me.kpavlov.kt.schema.generator.json

import io.kotest.assertions.json.shouldEqualJson
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import me.kpavlov.kt.schema.generator.core.ir.Literal
import me.kpavlov.kt.schema.generator.core.ir.ObjectNode
import me.kpavlov.kt.schema.generator.core.ir.PrimitiveKind
import me.kpavlov.kt.schema.generator.core.ir.PrimitiveNode
import me.kpavlov.kt.schema.generator.core.ir.Property
import me.kpavlov.kt.schema.generator.core.ir.PropertyValue
import me.kpavlov.kt.schema.generator.core.ir.TypeGraph
import me.kpavlov.kt.schema.generator.core.ir.TypeId
import me.kpavlov.kt.schema.generator.core.ir.TypeRef
import me.kpavlov.kt.schema.json.FunctionCallingSchema
import me.kpavlov.kt.schema.json.JsonSchema
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

/** How the same IR is emitted by both transformers, depending on `optional` and `value`. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PropertyPresenceTest {
    private val json = Json { encodeDefaults = false }
    private val rootId = TypeId("Root")

    private val string = TypeRef.Inline(PrimitiveNode(PrimitiveKind.STRING))
    private val nullableString = TypeRef.Inline(PrimitiveNode(PrimitiveKind.STRING), nullable = true)

    private val graph =
        TypeGraph(
            root = TypeRef.Ref(rootId),
            nodes =
                mapOf(
                    rootId to
                        ObjectNode(
                            name = "Root",
                            properties =
                                listOf(
                                    Property("plain", string),
                                    Property("nullablePlain", nullableString),
                                    Property(
                                        "nullDefault",
                                        nullableString,
                                        optional = true,
                                        value = PropertyValue.Default(Literal.Null),
                                    ),
                                    Property(
                                        "strDefault",
                                        string,
                                        optional = true,
                                        value = PropertyValue.Default(Literal.Str("a")),
                                    ),
                                    Property(
                                        "unknownDefault",
                                        string,
                                        optional = true,
                                        value = PropertyValue.UnknownDefault,
                                    ),
                                    Property("annotated", string, value = PropertyValue.Default(Literal.Str("a"))),
                                    Property("const", string, value = PropertyValue.Const(Literal.Str("x"))),
                                ),
                        ),
                ),
        )

    /** Emits [graph] with a transformer for [JsonSchemaConfig] and returns the object's JSON. */
    fun interface Emitter {
        fun emit(config: JsonSchemaConfig): JsonObject
    }

    private val jsonSchemaEmitter =
        Emitter { config ->
            val schema = TypeGraphToJsonSchemaTransformer(config).transform(graph, "Root")
            json.encodeToJsonElement(JsonSchema.serializer(), schema).jsonObject
        }

    private val functionCallingEmitter =
        Emitter { config ->
            val functionConfig =
                FunctionCallingSchemaConfig(
                    respectDefaultPresence = config.respectDefaultPresence,
                    requireNullableFields = config.requireNullableFields,
                    useUnionTypes = config.useUnionTypes,
                    useNullableField = config.useNullableField,
                    strictMode = config.respectDefaultPresence.not(),
                )
            val schema = TypeGraphToFunctionCallingSchemaTransformer(functionConfig).transform(graph, "Root")
            json
                .encodeToJsonElement(FunctionCallingSchema.serializer(), schema)
                .jsonObject
                .getValue("parameters")
                .jsonObject
        }

    fun emitters(): Stream<Pair<String, Emitter>> =
        Stream.of("JSON Schema" to jsonSchemaEmitter, "function calling" to functionCallingEmitter)

    private fun JsonObject.presence(): String =
        JsonObject(filterKeys { it == "required" || it == "properties" }).toString()

    @ParameterizedTest(name = "{0}")
    @MethodSource("emitters")
    fun `default config derives requiredness and default from optional and value`(case: Pair<String, Emitter>) {
        // Act
        val emitted = case.second.emit(JsonSchemaConfig.Default)

        // Assert
        emitted.presence() shouldEqualJson
            // language=json
            """
            {
              "properties": {
                "plain": { "type": "string" },
                "nullablePlain": { "type": ["string", "null"] },
                "nullDefault": { "type": ["string", "null"], "default": null },
                "strDefault": { "type": "string", "default": "a" },
                "unknownDefault": { "type": "string" },
                "annotated": { "type": "string", "default": "a" },
                "const": { "type": "string", "const": "x" }
              },
              "required": ["plain", "nullablePlain", "const"]
            }
            """.trimIndent()
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("emitters")
    fun `strict config requires everything and emits no default`(case: Pair<String, Emitter>) {
        // Act
        val emitted = case.second.emit(JsonSchemaConfig.Strict)

        // Assert
        emitted.presence() shouldEqualJson
            // language=json
            """
            {
              "properties": {
                "plain": { "type": "string" },
                "nullablePlain": { "type": ["string", "null"] },
                "nullDefault": { "type": ["string", "null"] },
                "strDefault": { "type": "string" },
                "unknownDefault": { "type": "string" },
                "annotated": { "type": "string" },
                "const": { "type": "string", "const": "x" }
              },
              "required": ["plain", "nullablePlain", "nullDefault", "strDefault", "unknownDefault", "annotated", "const"]
            }
            """.trimIndent()
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("emitters")
    fun `legacy nullable field config keeps nullable on a required nullable property`(case: Pair<String, Emitter>) {
        // Act
        val emitted = case.second.emit(JsonSchemaConfig.OpenAPI)

        // Assert
        emitted
            .getValue("properties")
            .jsonObject
            .getValue("nullablePlain")
            .toString() shouldEqualJson
            // language=json
            """{ "type": "string", "nullable": true }"""
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("emitters")
    fun `lenient config omits a null default because its types do not admit null`(case: Pair<String, Emitter>) {
        // Act
        val emitted = case.second.emit(JsonSchemaConfig.Lenient)

        // Assert
        emitted
            .getValue("properties")
            .jsonObject
            .getValue("nullDefault")
            .toString() shouldEqualJson
            // language=json
            """{ "type": "string" }"""
    }
}
