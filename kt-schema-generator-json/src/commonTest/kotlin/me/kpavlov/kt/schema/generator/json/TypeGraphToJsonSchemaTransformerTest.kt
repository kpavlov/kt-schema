package me.kpavlov.kt.schema.generator.json

import io.kotest.assertions.json.shouldEqualJson
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import me.kpavlov.kt.schema.generator.core.ir.Discriminator
import me.kpavlov.kt.schema.generator.core.ir.EnumNode
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
import me.kpavlov.kt.schema.json.encodeToString
import kotlin.test.Test

class TypeGraphToJsonSchemaTransformerTest {
    private val transformer = TypeGraphToJsonSchemaTransformer(config = JsonSchemaConfig.Default)

    @Test
    fun `failed node conversion does not leave placeholder in definitions`() {
        // A polymorphic type where one subtype references a TypeId not in the graph.
        // convertNode for the object will fail when it tries to resolve the dangling ref.
        val baseId = TypeId("Base")
        val goodId = TypeId("Good")
        val badId = TypeId("Bad")
        val danglingId = TypeId("Dangling")

        val goodNode =
            ObjectNode(
                name = "Good",
                properties = listOf(Property(name = "x", type = TypeRef.Inline(PrimitiveNode(PrimitiveKind.STRING)))),
            )
        // Bad references a type that doesn't exist in the graph
        val badNode =
            ObjectNode(
                name = "Bad",
                properties = listOf(Property(name = "missing", type = TypeRef.Ref(danglingId))),
            )
        val polyNode =
            PolymorphicNode(
                name = "Base",
                subtypes = listOf(SubtypeRef(goodId), SubtypeRef(badId)),
                discriminator = Discriminator(name = "type"),
            )

        val rootNode =
            ObjectNode(
                name = "Root",
                properties = listOf(Property(name = "base", type = TypeRef.Ref(baseId))),
            )
        val rootId = TypeId("Root")

        val graph =
            TypeGraph(
                root = TypeRef.Ref(rootId),
                nodes =
                    mapOf(
                        rootId to rootNode,
                        baseId to polyNode,
                        goodId to goodNode,
                        badId to badNode,
                        // danglingId intentionally missing
                    ),
            )

        val error =
            shouldThrow<IllegalStateException> {
                transformer.transform(graph, "Root")
            }

        // The error should mention the dangling reference
        error.message.toString() shouldContainAny listOf("Dangling", "not found")
    }

    @Test
    fun `subtype used both in polymorphic hierarchy and as property gets discriminator exactly once`() {
        // Shape sealed hierarchy: Circle, Square
        // Container has both a `shape: Shape` (polymorphic) and `primaryCircle: Circle` (direct ref)
        // This exercises the path where Circle is registered via ensureNodeInDefinitions from
        // the direct ref, then convertPolymorphic also encounters it.
        val shapeId = TypeId("Shape")
        val circleId = TypeId("Circle")
        val squareId = TypeId("Square")

        val circleNode =
            ObjectNode(
                name = "Circle",
                properties =
                    listOf(
                        Property(
                            name = "radius",
                            type = TypeRef.Inline(PrimitiveNode(PrimitiveKind.DOUBLE)),
                        ),
                    ),
            )
        val squareNode =
            ObjectNode(
                name = "Square",
                properties =
                    listOf(
                        Property(
                            name = "side",
                            type = TypeRef.Inline(PrimitiveNode(PrimitiveKind.DOUBLE)),
                        ),
                    ),
            )
        val shapeNode =
            PolymorphicNode(
                name = "Shape",
                subtypes = listOf(SubtypeRef(circleId), SubtypeRef(squareId)),
                discriminator = Discriminator(name = "type"),
            )

        // Container references Circle directly AND Shape (which includes Circle)
        val containerId = TypeId("Container")
        val containerNode =
            ObjectNode(
                name = "Container",
                properties =
                    listOf(
                        Property(name = "primaryCircle", type = TypeRef.Ref(circleId)),
                        Property(name = "shape", type = TypeRef.Ref(shapeId)),
                    ),
            )

        val graph =
            TypeGraph(
                root = TypeRef.Ref(containerId),
                nodes =
                    mapOf(
                        containerId to containerNode,
                        shapeId to shapeNode,
                        circleId to circleNode,
                        squareId to squareNode,
                    ),
            )

        val schema = transformer.transform(graph, "Container")
        val schemaJson = schema.encodeToString(json)

        // Circle should have discriminator "type" exactly once in required
        schemaJson shouldEqualJson
            // language=JSON
            $$"""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "$id": "Container",
              "type": "object",
              "properties": {
                "primaryCircle": {
                  "$ref": "#/$defs/Circle"
                },
                "shape": {
                  "$ref": "#/$defs/Shape"
                }
              },
              "required": ["primaryCircle", "shape"],
              "additionalProperties": false,
              "$defs": {
                "Circle": {
                  "type": "object",
                  "properties": {
                    "type": {
                      "type": "string",
                      "const": "Circle"
                    },
                    "radius": { "type": "number" }
                  },
                  "required": ["type", "radius"],
                  "additionalProperties": false
                },
                "Shape": {
                  "oneOf": [
                    { "$ref": "#/$defs/Circle" },
                    { "$ref": "#/$defs/Square" }
                  ]
                },
                "Square": {
                  "type": "object",
                  "properties": {
                    "type": {
                      "type": "string",
                      "const": "Square"
                    },
                    "side": { "type": "number" }
                  },
                  "required": ["type", "side"],
                  "additionalProperties": false
                }
              }
            }
            """.trimIndent()
    }

    @Test
    fun `node name is used verbatim for id defs key and ref`() {
        // NamedTypeNode.name drives $id/$defs/$ref directly: the FQN when the front end found no
        // override annotation, or the override value (e.g. from @JsonTypeName) when it did.
        val orgChartId = TypeId("com.example.orgchart.OrgChart")
        val orgChartNode =
            ObjectNode(
                name = "com.example.orgchart.OrgChart",
                properties =
                    listOf(
                        Property(
                            name = "compensation",
                            type = TypeRef.Ref(TypeId("com.example.orgchart.Compensation")),
                        ),
                    ),
            )
        val compensationId = TypeId("com.example.orgchart.Compensation")
        val compensationNode =
            ObjectNode(
                // Simulates a `@JsonTypeName("Compensation")` override.
                name = "Compensation",
                properties =
                    listOf(
                        Property(
                            name = "baseSalary",
                            type = TypeRef.Inline(PrimitiveNode(PrimitiveKind.INT)),
                        ),
                    ),
            )

        val graph =
            TypeGraph(
                root = TypeRef.Ref(orgChartId),
                nodes = mapOf(orgChartId to orgChartNode, compensationId to compensationNode),
            )

        val schema = transformer.transform(graph, "com.example.orgchart.OrgChart")
        val schemaJson = schema.encodeToString(json)

        schemaJson shouldEqualJson
            // language=JSON
            $$"""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "$id": "com.example.orgchart.OrgChart",
              "type": "object",
              "properties": {
                "compensation": {
                  "$ref": "#/$defs/Compensation"
                }
              },
              "required": ["compensation"],
              "additionalProperties": false,
              "$defs": {
                "Compensation": {
                  "type": "object",
                  "properties": {
                    "baseSalary": { "type": "integer" }
                  },
                  "required": ["baseSalary"],
                  "additionalProperties": false
                }
              }
            }
            """.trimIndent()
    }

    @Test
    fun `polymorphic subtype name is used verbatim for defs key ref and discriminator const`() {
        val shapeId = TypeId("com.example.Shape")
        val circleId = TypeId("com.example.Circle")
        val squareId = TypeId("com.example.Square")

        val circleNode =
            ObjectNode(
                name = "com.example.Circle",
                properties =
                    listOf(
                        Property(
                            name = "radius",
                            type = TypeRef.Inline(PrimitiveNode(PrimitiveKind.DOUBLE)),
                        ),
                    ),
            )
        val squareNode =
            ObjectNode(
                name = "com.example.Square",
                properties =
                    listOf(
                        Property(
                            name = "side",
                            type = TypeRef.Inline(PrimitiveNode(PrimitiveKind.DOUBLE)),
                        ),
                    ),
            )
        val shapeNode =
            PolymorphicNode(
                name = "com.example.Shape",
                subtypes = listOf(SubtypeRef(circleId), SubtypeRef(squareId)),
                discriminator = Discriminator(name = "type"),
            )

        val graph =
            TypeGraph(
                root = TypeRef.Ref(shapeId),
                nodes =
                    mapOf(
                        shapeId to shapeNode,
                        circleId to circleNode,
                        squareId to squareNode,
                    ),
            )

        val schema = transformer.transform(graph, "com.example.Shape")
        val schemaJson = schema.encodeToString(json)

        schemaJson shouldEqualJson
            // language=JSON
            $$"""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "$id": "com.example.Shape",
              "type": "object",
              "oneOf": [
                { "$ref": "#/$defs/com.example.Circle" },
                { "$ref": "#/$defs/com.example.Square" }
              ],
              "$defs": {
                "com.example.Circle": {
                  "type": "object",
                  "properties": {
                    "type": {
                      "type": "string",
                      "const": "com.example.Circle"
                    },
                    "radius": { "type": "number" }
                  },
                  "required": ["type", "radius"],
                  "additionalProperties": false
                },
                "com.example.Square": {
                  "type": "object",
                  "properties": {
                    "type": {
                      "type": "string",
                      "const": "com.example.Square"
                    },
                    "side": { "type": "number" }
                  },
                  "required": ["type", "side"],
                  "additionalProperties": false
                }
              }
            }
            """.trimIndent()
    }

    @Test
    fun `colliding override names fail with all conflicting ids`() {
        // ResultA.Success and ResultB.Success are both annotated e.g. @JsonTypeName("Success") —
        // explicit names are honored exactly, so the clash is reported instead of silently renamed.
        val rootId = TypeId("com.example.ApiResponse")
        val resultAId = TypeId("com.example.ResultA")
        val resultBId = TypeId("com.example.ResultB")
        val successAId = TypeId("com.example.ResultA.Success")
        val successBId = TypeId("com.example.ResultB.Success")

        val successANode =
            ObjectNode(
                name = "Success",
                properties =
                    listOf(
                        Property(
                            name = "value",
                            type = TypeRef.Inline(PrimitiveNode(PrimitiveKind.STRING)),
                        ),
                    ),
            )
        val successBNode =
            ObjectNode(
                name = "Success",
                properties = listOf(Property(name = "code", type = TypeRef.Inline(PrimitiveNode(PrimitiveKind.INT)))),
            )
        val resultANode =
            PolymorphicNode(
                name = "com.example.ResultA",
                subtypes = listOf(SubtypeRef(successAId)),
                discriminator = Discriminator(name = "type"),
            )
        val resultBNode =
            PolymorphicNode(
                name = "com.example.ResultB",
                subtypes = listOf(SubtypeRef(successBId)),
                discriminator = Discriminator(name = "type"),
            )
        val rootNode =
            ObjectNode(
                name = "com.example.ApiResponse",
                properties =
                    listOf(
                        Property(name = "resultA", type = TypeRef.Ref(resultAId)),
                        Property(name = "resultB", type = TypeRef.Ref(resultBId)),
                    ),
            )

        val graph =
            TypeGraph(
                root = TypeRef.Ref(rootId),
                nodes =
                    mapOf(
                        rootId to rootNode,
                        resultAId to resultANode,
                        resultBId to resultBNode,
                        successAId to successANode,
                        successBId to successBNode,
                    ),
            )

        val error = shouldThrow<IllegalStateException> { transformer.transform(graph, "com.example.ApiResponse") }

        error.message shouldBe
            "Type name 'Success' is used by multiple declarations: " +
            "com.example.ResultA.Success, com.example.ResultB.Success. " +
            "Give them distinct @JsonTypeName/@SerialName values."
    }

    @Test
    fun `enum node default value is emitted as default on its own defs schema`() {
        val statusId = TypeId("Status")
        val statusNode = EnumNode(name = "Status", entries = listOf("ACTIVE", "INACTIVE"), defaultValue = "ACTIVE")
        val rootId = TypeId("Root")
        val rootNode =
            ObjectNode(
                name = "Root",
                properties = listOf(Property(name = "status", type = TypeRef.Ref(statusId))),
            )
        val graph =
            TypeGraph(
                root = TypeRef.Ref(rootId),
                nodes = mapOf(rootId to rootNode, statusId to statusNode),
            )

        val schema = transformer.transform(graph, "Root")
        val schemaJson = schema.encodeToString(json)

        schemaJson shouldEqualJson
            // language=JSON
            $$"""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "$id": "Root",
              "type": "object",
              "properties": {
                "status": { "$ref": "#/$defs/Status" }
              },
              "required": ["status"],
              "additionalProperties": false,
              "$defs": {
                "Status": {
                  "type": "string",
                  "enum": ["ACTIVE", "INACTIVE"],
                  "default": "ACTIVE"
                }
              }
            }
            """.trimIndent()
    }

    @Test
    fun `root enum node default value is emitted as default on the root schema`() {
        val statusId = TypeId("Status")
        val statusNode = EnumNode(name = "Status", entries = listOf("ACTIVE", "INACTIVE"), defaultValue = "ACTIVE")
        val graph =
            TypeGraph(
                root = TypeRef.Ref(statusId),
                nodes = mapOf(statusId to statusNode),
            )

        val schema = transformer.transform(graph, "Status")
        val schemaJson = schema.encodeToString(json)

        schemaJson shouldEqualJson
            // language=JSON
            $$"""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "$id": "Status",
              "type": "string",
              "enum": ["ACTIVE", "INACTIVE"],
              "default": "ACTIVE"
            }
            """.trimIndent()
    }

    @Test
    fun `Lenient omits null markers and additionalProperties and keeps nullable fields optional`() {
        // Given: Msg(text: String, count: Int?, ref: Other?, inner: Inner, tags: Map<String, Int>?)
        val msgId = TypeId("Msg")
        val otherId = TypeId("Other")
        val innerId = TypeId("Inner")
        val otherNode =
            ObjectNode(
                name = "Other",
                properties = listOf(Property(name = "id", type = TypeRef.Inline(PrimitiveNode(PrimitiveKind.STRING)))),
            )
        val innerNode =
            ObjectNode(
                name = "Inner",
                properties = listOf(Property(name = "leaf", type = TypeRef.Ref(otherId))),
            )
        val msgNode =
            ObjectNode(
                name = "Msg",
                properties =
                    listOf(
                        Property("text", TypeRef.Inline(PrimitiveNode(PrimitiveKind.STRING))),
                        Property("count", TypeRef.Inline(PrimitiveNode(PrimitiveKind.INT), nullable = true)),
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
            )
        val graph =
            TypeGraph(
                root = TypeRef.Ref(msgId),
                nodes = mapOf(msgId to msgNode, otherId to otherNode, innerId to innerNode),
            )

        // When
        val schema = TypeGraphToJsonSchemaTransformer(JsonSchemaConfig.Lenient).transform(graph, "Msg")

        // Then
        schema.encodeToString(json) shouldEqualJson
            // language=JSON
            $$"""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "$id": "Msg",
              "type": "object",
              "properties": {
                "text": { "type": "string" },
                "count": { "type": "integer" },
                "ref": { "$ref": "#/$defs/Other" },
                "inner": { "$ref": "#/$defs/Inner" },
                "tags": {
                  "type": "object",
                  "additionalProperties": { "type": "integer" }
                }
              },
              "required": ["text", "inner"],
              "$defs": {
                "Other": {
                  "type": "object",
                  "properties": { "id": { "type": "string" } },
                  "required": ["id"]
                },
                "Inner": {
                  "type": "object",
                  "properties": { "leaf": { "$ref": "#/$defs/Other" } },
                  "required": ["leaf"]
                }
              }
            }
            """.trimIndent()
    }

    @Test
    fun `Lenient keeps a null branch for nullable collection elements and map values`() {
        // Given: Bag(names: List<String?>, refs: List<Other?>, scores: Map<String, Int?>)
        val bagId = TypeId("Bag")
        val otherId = TypeId("Other")
        val otherNode =
            ObjectNode(
                name = "Other",
                properties = listOf(Property(name = "id", type = TypeRef.Inline(PrimitiveNode(PrimitiveKind.STRING)))),
            )
        val bagNode =
            ObjectNode(
                name = "Bag",
                properties =
                    listOf(
                        Property(
                            "names",
                            TypeRef.Inline(
                                ListNode(
                                    TypeRef.Inline(
                                        node = PrimitiveNode(kind = PrimitiveKind.STRING),
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
                                    key =
                                        TypeRef.Inline(
                                            PrimitiveNode(
                                                kind = PrimitiveKind.STRING,
                                            ),
                                        ),
                                    value =
                                        TypeRef.Inline(
                                            PrimitiveNode(
                                                kind = PrimitiveKind.INT,
                                            ),
                                            nullable = true,
                                        ),
                                ),
                            ),
                        ),
                    ),
            )
        val graph = TypeGraph(root = TypeRef.Ref(bagId), nodes = mapOf(bagId to bagNode, otherId to otherNode))

        // When
        val schema = TypeGraphToJsonSchemaTransformer(JsonSchemaConfig.Lenient).transform(graph, "Bag")

        // Then
        schema.encodeToString(json) shouldEqualJson
            // language=JSON
            $$"""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "$id": "Bag",
              "type": "object",
              "properties": {
                "names": {
                  "type": "array",
                  "items": { "anyOf": [{ "type": "string" }, { "type": "null" }] }
                },
                "refs": {
                  "type": "array",
                  "items": { "anyOf": [{ "$ref": "#/$defs/Other" }, { "type": "null" }] }
                },
                "scores": {
                  "type": "object",
                  "additionalProperties": { "anyOf": [{ "type": "integer" }, { "type": "null" }] }
                }
              },
              "required": ["names", "refs", "scores"],
              "$defs": {
                "Other": {
                  "type": "object",
                  "properties": { "id": { "type": "string" } },
                  "required": ["id"]
                }
              }
            }
            """.trimIndent()
    }
}

private infix fun String.shouldContainAny(candidates: List<String>) {
    require(candidates.any { this.contains(it) }) {
        "Expected string to contain any of $candidates, but was: $this"
    }
}
