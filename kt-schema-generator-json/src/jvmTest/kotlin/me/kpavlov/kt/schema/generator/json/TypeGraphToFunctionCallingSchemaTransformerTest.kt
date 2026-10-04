package me.kpavlov.kt.schema.generator.json

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.json.shouldEqualJson
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import me.kpavlov.kt.schema.generator.core.ir.Discriminator
import me.kpavlov.kt.schema.generator.core.ir.ListNode
import me.kpavlov.kt.schema.generator.core.ir.MapNode
import me.kpavlov.kt.schema.generator.core.ir.ObjectNode
import me.kpavlov.kt.schema.generator.core.ir.PolymorphicNode
import me.kpavlov.kt.schema.generator.core.ir.PrimitiveKind
import me.kpavlov.kt.schema.generator.core.ir.PrimitiveNode
import me.kpavlov.kt.schema.generator.core.ir.Property
import me.kpavlov.kt.schema.generator.core.ir.SubtypeRef
import me.kpavlov.kt.schema.generator.core.ir.TypeGraph
import me.kpavlov.kt.schema.generator.core.ir.TypeId
import me.kpavlov.kt.schema.generator.core.ir.TypeRef
import me.kpavlov.kt.schema.json.AnyOfPropertyDefinition
import me.kpavlov.kt.schema.json.DenyAdditionalProperties
import me.kpavlov.kt.schema.json.FunctionCallingSchema
import me.kpavlov.kt.schema.json.NumericPropertyDefinition
import me.kpavlov.kt.schema.json.StringPropertyDefinition
import kotlin.test.Test

class TypeGraphToFunctionCallingSchemaTransformerTest {
    private val json = Json { explicitNulls = false }

    private val otherId = TypeId("Other")
    private val innerId = TypeId("Inner")
    private val sendId = TypeId("send")

    private val otherNode =
        ObjectNode(
            name = "Other",
            properties =
                listOf(
                    Property(
                        name = "id",
                        type = TypeRef.Inline(PrimitiveNode(PrimitiveKind.STRING)),
                    ),
                ),
        )
    private val innerNode =
        ObjectNode(
            name = "Inner",
            properties = listOf(Property(name = "leaf", type = TypeRef.Ref(otherId))),
        )

    /** Function `send(text: String, count: Int?, ref: Other?, inner: Inner, tags: Map<String, Int>?)`. */
    private val sendGraph =
        TypeGraph(
            root = TypeRef.Ref(sendId),
            nodes =
                mapOf(
                    sendId to
                        ObjectNode(
                            name = "send",
                            properties =
                                listOf(
                                    Property("text", TypeRef.Inline(PrimitiveNode(PrimitiveKind.STRING))),
                                    Property(
                                        "count",
                                        TypeRef.Inline(PrimitiveNode(PrimitiveKind.INT), nullable = true),
                                    ),
                                    Property("ref", TypeRef.Ref(otherId, nullable = true)),
                                    Property("inner", TypeRef.Ref(innerId)),
                                    Property(
                                        "tags",
                                        TypeRef.Inline(
                                            MapNode(
                                                key = TypeRef.Inline(PrimitiveNode(PrimitiveKind.STRING)),
                                                value = TypeRef.Inline(PrimitiveNode(PrimitiveKind.INT)),
                                            ),
                                            nullable = true,
                                        ),
                                    ),
                                ),
                        ),
                    otherId to otherNode,
                    innerId to innerNode,
                ),
        )

    private fun FunctionCallingSchema.encode(): String = json.encodeToString(FunctionCallingSchema.serializer(), this)

    @Test
    fun `Lenient omits null markers, additionalProperties and nullable required fields`() {
        // Given
        val transformer = TypeGraphToFunctionCallingSchemaTransformer(FunctionCallingSchemaConfig.Lenient)

        // When
        val schema = transformer.transform(sendGraph, "send")

        // Then
        schema.encode() shouldEqualJson
            // language=json
            """
            {
              "type": "function",
              "name": "send",
              "description": "",
              "parameters": {
                "type": "object",
                "properties": {
                  "text": { "type": "string" },
                  "count": { "type": "integer" },
                  "ref": {
                    "type": "object",
                    "properties": { "id": { "type": "string" } },
                    "required": ["id"]
                  },
                  "inner": {
                    "type": "object",
                    "properties": {
                      "leaf": {
                        "type": "object",
                        "properties": { "id": { "type": "string" } },
                        "required": ["id"]
                      }
                    },
                    "required": ["leaf"]
                  },
                  "tags": {
                    "type": "object",
                    "additionalProperties": { "type": "integer" }
                  }
                },
                "required": ["text", "inner"]
              }
            }
            """.trimIndent()
    }

    @Test
    fun `Lenient keeps a null branch for nullable collection elements and map values`() {
        // Given: bag(names: List<String?>, refs: List<Other?>, scores: Map<String, Int?>)
        val bagId = TypeId("bag")
        val otherId = TypeId("Other")
        val otherNode =
            ObjectNode(
                name = "Other",
                properties = listOf(Property(name = "id", type = TypeRef.Inline(PrimitiveNode(PrimitiveKind.STRING)))),
            )
        val bagNode =
            ObjectNode(
                name = "bag",
                properties =
                    listOf(
                        Property(
                            "names",
                            TypeRef.Inline(
                                ListNode(
                                    TypeRef.Inline(
                                        PrimitiveNode(PrimitiveKind.STRING),
                                        nullable = true,
                                    ),
                                ),
                            ),
                        ),
                        Property("refs", TypeRef.Inline(ListNode(TypeRef.Ref(otherId, nullable = true)))),
                        Property(
                            "scores",
                            TypeRef.Inline(
                                MapNode(
                                    key = TypeRef.Inline(PrimitiveNode(PrimitiveKind.STRING)),
                                    value = TypeRef.Inline(PrimitiveNode(PrimitiveKind.INT), nullable = true),
                                ),
                            ),
                        ),
                    ),
            )
        val graph = TypeGraph(root = TypeRef.Ref(bagId), nodes = mapOf(bagId to bagNode, otherId to otherNode))
        val transformer = TypeGraphToFunctionCallingSchemaTransformer(FunctionCallingSchemaConfig.Lenient)

        // When
        val schema = transformer.transform(graph, "bag")

        // Then
        schema.encode() shouldEqualJson
            // language=json
            """
            {
              "type": "function",
              "name": "bag",
              "description": "",
              "parameters": {
                "type": "object",
                "properties": {
                  "names": {
                    "type": "array",
                    "items": { "anyOf": [{ "type": "string" }, { "type": "null" }] }
                  },
                  "refs": {
                    "type": "array",
                    "items": {
                      "anyOf": [
                        {
                          "type": "object",
                          "properties": { "id": { "type": "string" } },
                          "required": ["id"]
                        },
                        { "type": "null" }
                      ]
                    }
                  },
                  "scores": {
                    "type": "object",
                    "additionalProperties": { "anyOf": [{ "type": "integer" }, { "type": "null" }] }
                  }
                },
                "required": ["names", "refs", "scores"]
              }
            }
            """.trimIndent()
    }

    @Test
    fun `OpenAPI emits nullable true instead of null types`() {
        // Given
        val transformer = TypeGraphToFunctionCallingSchemaTransformer(FunctionCallingSchemaConfig.OpenAPI)

        // When
        val schema = transformer.transform(sendGraph, "send")

        // Then
        schema.encode() shouldEqualJson
            // language=json
            """
            {
              "type": "function",
              "name": "send",
              "description": "",
              "parameters": {
                "type": "object",
                "properties": {
                  "text": { "type": "string" },
                  "count": { "type": "integer", "nullable": true },
                  "ref": {
                    "type": "object",
                    "nullable": true,
                    "properties": { "id": { "type": "string" } },
                    "required": ["id"],
                    "additionalProperties": false
                  },
                  "inner": {
                    "type": "object",
                    "properties": {
                      "leaf": {
                        "type": "object",
                        "properties": { "id": { "type": "string" } },
                        "required": ["id"],
                        "additionalProperties": false
                      }
                    },
                    "required": ["leaf"],
                    "additionalProperties": false
                  },
                  "tags": {
                    "type": "object",
                    "nullable": true,
                    "additionalProperties": { "type": "integer" }
                  }
                },
                "required": ["text", "count", "ref", "inner", "tags"],
                "additionalProperties": false
              }
            }
            """.trimIndent()
    }

    @Test
    fun `Simple preset keeps null union types and requires nullable fields without defaults`() {
        // Given
        val transformer = TypeGraphToFunctionCallingSchemaTransformer(FunctionCallingSchemaConfig.Simple)

        // When
        val parameters = transformer.transform(sendGraph, "send").parameters

        // Then
        val count = parameters.properties.shouldNotBeNull().getValue("count") as NumericPropertyDefinition
        assertSoftly {
            count.type shouldBe listOf("integer", "null")
            parameters.required shouldBe listOf("text", "count", "ref", "inner", "tags")
            parameters.additionalProperties shouldBe DenyAdditionalProperties
        }
    }

    @Test
    fun `Lenient does not wrap a nullable polymorphic property in a null anyOf and keeps its description`() {
        // Given
        val shapeId = TypeId("Shape")
        val circleId = TypeId("Circle")
        val squareId = TypeId("Square")
        val rootId = TypeId("draw")
        val graph =
            TypeGraph(
                root = TypeRef.Ref(rootId),
                nodes =
                    mapOf(
                        rootId to
                            ObjectNode(
                                name = "draw",
                                properties =
                                    listOf(
                                        Property(
                                            name = "shape",
                                            type = TypeRef.Ref(shapeId, nullable = true),
                                            description = "A shape",
                                        ),
                                    ),
                            ),
                        shapeId to
                            PolymorphicNode(
                                name = "Shape",
                                subtypes = listOf(SubtypeRef(circleId), SubtypeRef(squareId)),
                                discriminator = Discriminator(name = "type"),
                            ),
                        circleId to ObjectNode(name = "Circle", properties = emptyList()),
                        squareId to ObjectNode(name = "Square", properties = emptyList()),
                    ),
            )
        val transformer = TypeGraphToFunctionCallingSchemaTransformer(FunctionCallingSchemaConfig.Lenient)

        // When
        val shape =
            transformer
                .transform(graph, "draw")
                .parameters.properties
                .shouldNotBeNull()["shape"]

        // Then
        val anyOf = shape.shouldNotBeNull() as AnyOfPropertyDefinition
        val nullOptions = anyOf.anyOf.filterIsInstance<StringPropertyDefinition>().filter { it.type == listOf("null") }
        assertSoftly(anyOf) {
            this.anyOf shouldHaveSize 2
            description shouldBe "A shape"
        }
        nullOptions.shouldBeEmpty()
    }
}
