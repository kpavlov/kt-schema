package kotlinx.schema.generator.json

import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.equals.shouldNotBeEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.schema.generator.core.ir.ListNode
import kotlinx.schema.generator.core.ir.MapNode
import kotlinx.schema.generator.core.ir.ObjectNode
import kotlinx.schema.generator.core.ir.PrimitiveKind
import kotlinx.schema.generator.core.ir.PrimitiveNode
import kotlinx.schema.generator.core.ir.Property
import kotlinx.schema.generator.core.ir.TypeGraph
import kotlinx.schema.generator.core.ir.TypeId
import kotlinx.schema.generator.core.ir.TypeRef
import kotlinx.schema.json.ArrayPropertyDefinition
import kotlinx.schema.json.NumericPropertyDefinition
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import kotlin.test.Test

/**
 * Tests for JsonSchemaConfig options.
 * Focuses on configuration-specific behavior.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JsonSchemaConfigTest {
    @Test
    fun `respectDefaultPresence true should use default presence for required fields`() {
        val config =
            JsonSchemaConfig(
                respectDefaultPresence = true,
                requireNullableFields = true, // not ignored even if respectDefaultPresence=true
                useUnionTypes = true,
                useNullableField = false,
            )
        val transformer = TypeGraphToJsonSchemaTransformer(config)
        val typeGraph =
            kotlinx.schema.generator.reflect.ReflectionClassIntrospector
                .introspect(PersonWithOptionals::class)
        val schema = transformer.transform(typeGraph, "PersonWithOptionals")

        val required = schema.required

        // Only properties without defaults should be required
        required.size shouldBe 5
        required shouldContainAll listOf("name")
    }

    @Test
    fun `requireNullableFields true should include all fields in required`() {
        val config =
            JsonSchemaConfig(
                respectDefaultPresence = false,
                requireNullableFields = true,
                useUnionTypes = true,
                useNullableField = false,
            )
        val transformer = TypeGraphToJsonSchemaTransformer(config)
        val typeGraph =
            kotlinx.schema.generator.reflect.ReflectionClassIntrospector
                .introspect(PersonWithOptionals::class)
        val schema = transformer.transform(typeGraph, "PersonWithOptionals")

        val required = schema.required

        // All properties should be in required array
        required.size shouldBe 5
        required shouldContainAll listOf("name", "age", "email", "score", "active")
    }

    @Test
    fun `requireNullableFields false should only include non-nullable fields in required`() {
        val config =
            JsonSchemaConfig(
                respectDefaultPresence = false,
                requireNullableFields = false,
                useUnionTypes = true,
                useNullableField = false,
            )
        val transformer = TypeGraphToJsonSchemaTransformer(config)
        val typeGraph =
            kotlinx.schema.generator.reflect.ReflectionClassIntrospector
                .introspect(PersonWithOptionals::class)
        val schema = transformer.transform(typeGraph, "PersonWithOptionals")

        val required = schema.required

        // Only non-nullable properties should be required
        // PersonWithOptionals has only 'name' as non-nullable
        required.size shouldBe 1
        required shouldContainAll listOf("name")
    }

    @Test
    fun `equals and hashCode should work correctly`() {
        val config1 = JsonSchemaConfig.Default
        val config1a = JsonSchemaConfig()
        val config2 = JsonSchemaConfig.Strict

        config1 shouldBeEqual config1
        config1.hashCode() shouldBe config1.hashCode()

        config1 shouldBeEqual config1a
        config1.hashCode() shouldBe config1a.hashCode()

        config1 shouldNotBeEqual config2
        config1.hashCode() shouldNotBe config2.hashCode()
    }

    @Test
    fun `toString should provide meaningful representation`() {
        val config = JsonSchemaConfig.Default
        config.toString() shouldBe
            "JsonSchemaConfig(" +
            "respectDefaultPresence=${config.respectDefaultPresence}, " +
            "requireNullableFields=${config.requireNullableFields}, " +
            "useUnionTypes=${config.useUnionTypes}, " +
            "useNullableField=${config.useNullableField}, " +
            "includePolymorphicDiscriminator=${config.includePolymorphicDiscriminator}, " +
            "includeOpenAPIPolymorphicDiscriminator=${config.includeOpenAPIPolymorphicDiscriminator}" +
            ")"
    }

    //region Node description propagation

    fun inlineNodeDescriptionCases() =
        listOf(
            Arguments.of(
                TypeGraph(
                    root = TypeRef.Inline(PrimitiveNode(PrimitiveKind.STRING, description = "primitive description")),
                    nodes = emptyMap(),
                ),
                "primitive description",
            ),
            Arguments.of(
                TypeGraph(
                    root =
                        TypeRef.Inline(
                            ListNode(
                                element = TypeRef.Inline(PrimitiveNode(PrimitiveKind.STRING)),
                                description = "list description",
                            ),
                        ),
                    nodes = emptyMap(),
                ),
                "list description",
            ),
            Arguments.of(
                TypeGraph(
                    root =
                        TypeRef.Inline(
                            MapNode(
                                key = TypeRef.Inline(PrimitiveNode(PrimitiveKind.STRING)),
                                value = TypeRef.Inline(PrimitiveNode(PrimitiveKind.INT)),
                                description = "map description",
                            ),
                        ),
                    nodes = emptyMap(),
                ),
                "map description",
            ),
        )

    @ParameterizedTest
    @MethodSource("inlineNodeDescriptionCases")
    fun `inline node description propagates to root schema`(
        graph: TypeGraph,
        expectedDescription: String,
    ) {
        TypeGraphToJsonSchemaTransformer(JsonSchemaConfig.Default)
            .transform(graph, "Root")
            .description shouldBe expectedDescription
    }

    @Test
    fun `inline node description is used as property description when property has no description`() {
        val graph =
            TypeGraph(
                root = TypeRef.Ref(TypeId("Root")),
                nodes =
                    mapOf(
                        TypeId("Root") to
                            ObjectNode(
                                name = "Root",
                                properties =
                                    listOf(
                                        Property(
                                            name = "count",
                                            type =
                                                TypeRef.Inline(
                                                    PrimitiveNode(PrimitiveKind.INT, description = "count description"),
                                                ),
                                            description = null,
                                        ),
                                        Property(
                                            name = "items",
                                            type =
                                                TypeRef.Inline(
                                                    ListNode(
                                                        element = TypeRef.Inline(PrimitiveNode(PrimitiveKind.STRING)),
                                                        description = "items description",
                                                    ),
                                                ),
                                            description = null,
                                        ),
                                    ),
                                required = setOf("count", "items"),
                            ),
                    ),
            )
        val schema = TypeGraphToJsonSchemaTransformer(JsonSchemaConfig.Default).transform(graph, "Root")
        val properties = checkNotNull(schema.properties)

        (properties["count"] as NumericPropertyDefinition).description shouldBe "count description"
        (properties["items"] as ArrayPropertyDefinition).description shouldBe "items description"
    }

    @Test
    fun `property description takes precedence over inline node description`() {
        val graph =
            TypeGraph(
                root = TypeRef.Ref(TypeId("Root")),
                nodes =
                    mapOf(
                        TypeId("Root") to
                            ObjectNode(
                                name = "Root",
                                properties =
                                    listOf(
                                        Property(
                                            name = "items",
                                            type =
                                                TypeRef.Inline(
                                                    ListNode(
                                                        element = TypeRef.Inline(PrimitiveNode(PrimitiveKind.STRING)),
                                                        description = "node description",
                                                    ),
                                                ),
                                            description = "property description",
                                        ),
                                    ),
                                required = setOf("items"),
                            ),
                    ),
            )
        val schema = TypeGraphToJsonSchemaTransformer(JsonSchemaConfig.Default).transform(graph, "Root")
        (schema.properties["items"] as ArrayPropertyDefinition).description shouldBe "property description"
    }

    //endregion
}
