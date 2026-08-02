package me.kpavlov.kt.schema.generator.json

import io.kotest.assertions.json.shouldEqualJson
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import me.kpavlov.kt.schema.generator.core.ir.Discriminator
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
                required = setOf("x"),
            )
        // Bad references a type that doesn't exist in the graph
        val badNode =
            ObjectNode(
                name = "Bad",
                properties = listOf(Property(name = "missing", type = TypeRef.Ref(danglingId))),
                required = setOf("missing"),
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
                required = setOf("base"),
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

        val error = shouldThrow<IllegalStateException> {
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
                required = setOf("radius"),
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
                required = setOf("side"),
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
                required = setOf("primaryCircle", "shape"),
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
                required = setOf("compensation"),
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
                required = setOf("baseSalary"),
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
                required = setOf("radius"),
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
                required = setOf("side"),
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
              "additionalProperties": false,
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
    fun `colliding override names fall back to fully qualified ids`() {
        // ResultA.Success and ResultB.Success are both annotated e.g. @JsonTypeName("Success") —
        // the fully qualified id must be used to keep $defs keys unambiguous.
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
                required = setOf("value"),
            )
        val successBNode =
            ObjectNode(
                name = "Success",
                properties = listOf(Property(name = "code", type = TypeRef.Inline(PrimitiveNode(PrimitiveKind.INT)))),
                required = setOf("code"),
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
                required = setOf("resultA", "resultB"),
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

        val schema = transformer.transform(graph, "com.example.ApiResponse")
        val schemaJson = schema.encodeToString(json)

        schemaJson shouldEqualJson
            // language=JSON
            $$"""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "$id": "com.example.ApiResponse",
              "type": "object",
              "properties": {
                "resultA": { "$ref": "#/$defs/com.example.ResultA" },
                "resultB": { "$ref": "#/$defs/com.example.ResultB" }
              },
              "required": ["resultA", "resultB"],
              "additionalProperties": false,
              "$defs": {
                "com.example.ResultA": {
                  "oneOf": [
                    { "$ref": "#/$defs/com.example.ResultA.Success" }
                  ]
                },
                "com.example.ResultB": {
                  "oneOf": [
                    { "$ref": "#/$defs/com.example.ResultB.Success" }
                  ]
                },
                "com.example.ResultA.Success": {
                  "type": "object",
                  "properties": {
                    "type": {
                      "type": "string",
                      "const": "com.example.ResultA.Success"
                    },
                    "value": { "type": "string" }
                  },
                  "required": ["type", "value"],
                  "additionalProperties": false
                },
                "com.example.ResultB.Success": {
                  "type": "object",
                  "properties": {
                    "type": {
                      "type": "string",
                      "const": "com.example.ResultB.Success"
                    },
                    "code": { "type": "integer" }
                  },
                  "required": ["type", "code"],
                  "additionalProperties": false
                }
              }
            }
            """.trimIndent()
    }

    @Test
    fun `jsonTypeNames falls back names that collide with another node's id after a prior fallback`() {
        // X and Y both override to "Bar" and must fall back to their own ids.
        // W's own name happens to equal X's id, which only becomes a problem once X
        // falls back to it - a second resolution pass is required to catch it.
        val xId = TypeId("com.example.X")
        val yId = TypeId("com.example.Y")
        val wId = TypeId("com.example.W")

        fun node(name: String) =
            ObjectNode(name = name, properties = emptyList(), required = emptySet())

        val graph =
            TypeGraph(
                root = TypeRef.Ref(wId),
                nodes =
                    mapOf(
                        xId to node("Bar"),
                        yId to node("Bar"),
                        wId to node("com.example.X"),
                    ),
            )

        val names = graph.jsonTypeNames()

        names[xId] shouldBe "com.example.X"
        names[yId] shouldBe "com.example.Y"
        names[wId] shouldBe "com.example.W"
    }
}

private infix fun String.shouldContainAny(candidates: List<String>) {
    require(candidates.any { this.contains(it) }) {
        "Expected string to contain any of $candidates, but was: $this"
    }
}
