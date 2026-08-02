package me.kpavlov.kt.schema.generator.json

import io.kotest.assertions.json.shouldEqualJson
import io.kotest.assertions.throwables.shouldThrow
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
                baseName = "Base",
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
                baseName = "Shape",
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
    fun `short type name drives defs key and ref`() {
        // The short (simple) type name drives $defs and $ref; a name override stored in
        // ObjectNode.name (e.g. @JsonTypeName) is not used for these structural names.
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
                name = "compensation",
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
              "$id": "OrgChart",
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
    fun `simple name in node name uses the short type name`() {
        val rootId = TypeId("com.example.Root")
        val rootNode =
            ObjectNode(
                name = "com.example.Root",
                properties = listOf(Property(name = "circle", type = TypeRef.Ref(TypeId("com.example.Circle")))),
                required = setOf("circle"),
            )
        val circleId = TypeId("com.example.Circle")
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

        val graph =
            TypeGraph(
                root = TypeRef.Ref(rootId),
                nodes = mapOf(rootId to rootNode, circleId to circleNode),
            )

        val schema = transformer.transform(graph, "com.example.Root")
        val schemaJson = schema.encodeToString(json)

        schemaJson shouldEqualJson
            // language=JSON
            $$"""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "$id": "Root",
              "type": "object",
              "properties": {
                "circle": {
                  "$ref": "#/$defs/Circle"
                }
              },
              "required": ["circle"],
              "additionalProperties": false,
              "$defs": {
                "Circle": {
                  "type": "object",
                  "properties": {
                    "radius": { "type": "number" }
                  },
                  "required": ["radius"],
                  "additionalProperties": false
                }
              }
            }
            """.trimIndent()
    }

    @Test
    fun `root type uses short name in id`() {
        val paymentId = TypeId("com.example.Payment")
        val paymentNode =
            ObjectNode(
                name = "payment",
                properties = listOf(Property(name = "amount", type = TypeRef.Inline(PrimitiveNode(PrimitiveKind.INT)))),
                required = setOf("amount"),
            )

        val graph =
            TypeGraph(
                root = TypeRef.Ref(paymentId),
                nodes = mapOf(paymentId to paymentNode),
            )

        val schema = transformer.transform(graph, "com.example.Payment")
        val schemaJson = schema.encodeToString(json)

        schemaJson shouldEqualJson
            // language=JSON
            $$"""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "$id": "Payment",
              "type": "object",
              "properties": {
                "amount": { "type": "integer" }
              },
              "required": ["amount"],
              "additionalProperties": false
            }
            """.trimIndent()
    }

    @Test
    fun `polymorphic subtype uses short name for defs key ref and discriminator const`() {
        val shapeId = TypeId("com.example.Shape")
        val circleId = TypeId("com.example.Circle")
        val squareId = TypeId("com.example.Square")

        val circleNode =
            ObjectNode(
                name = "circle",
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
                name = "square",
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
                baseName = "com.example.Shape",
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
              "$id": "Shape",
              "type": "object",
              "additionalProperties": false,
              "oneOf": [
                { "$ref": "#/$defs/Circle" },
                { "$ref": "#/$defs/Square" }
              ],
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
    fun `colliding short names fall back to fully qualified ids`() {
        // ResultA.Success and ResultB.Success share the short name "Success" — the fully
        // qualified id must be used to keep $defs keys unambiguous.
        val rootId = TypeId("me.ApiResponse")
        val resultAId = TypeId("me.ResultA")
        val resultBId = TypeId("me.ResultB")
        val successAId = TypeId("me.ResultA.Success")
        val successBId = TypeId("me.ResultB.Success")

        val successANode =
            ObjectNode(
                name = "me.ResultA.Success",
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
                name = "me.ResultB.Success",
                properties = listOf(Property(name = "code", type = TypeRef.Inline(PrimitiveNode(PrimitiveKind.INT)))),
                required = setOf("code"),
            )
        val resultANode =
            PolymorphicNode(
                baseName = "me.ResultA",
                subtypes = listOf(SubtypeRef(successAId)),
                discriminator = Discriminator(name = "type"),
            )
        val resultBNode =
            PolymorphicNode(
                baseName = "me.ResultB",
                subtypes = listOf(SubtypeRef(successBId)),
                discriminator = Discriminator(name = "type"),
            )
        val rootNode =
            ObjectNode(
                name = "me.ApiResponse",
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

        val schema = transformer.transform(graph, "me.ApiResponse")
        val schemaJson = schema.encodeToString(json)

        schemaJson shouldEqualJson
            // language=JSON
            $$"""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "$id": "ApiResponse",
              "type": "object",
              "properties": {
                "resultA": { "$ref": "#/$defs/ResultA" },
                "resultB": { "$ref": "#/$defs/ResultB" }
              },
              "required": ["resultA", "resultB"],
              "additionalProperties": false,
              "$defs": {
                "ResultA": {
                  "oneOf": [
                    { "$ref": "#/$defs/me.ResultA.Success" }
                  ]
                },
                "ResultB": {
                  "oneOf": [
                    { "$ref": "#/$defs/me.ResultB.Success" }
                  ]
                },
                "me.ResultA.Success": {
                  "type": "object",
                  "properties": {
                    "type": {
                      "type": "string",
                      "const": "me.ResultA.Success"
                    },
                    "value": { "type": "string" }
                  },
                  "required": ["type", "value"],
                  "additionalProperties": false
                },
                "me.ResultB.Success": {
                  "type": "object",
                  "properties": {
                    "type": {
                      "type": "string",
                      "const": "me.ResultB.Success"
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
}

private infix fun String.shouldContainAny(candidates: List<String>) {
    require(candidates.any { this.contains(it) }) {
        "Expected string to contain any of $candidates, but was: $this"
    }
}
