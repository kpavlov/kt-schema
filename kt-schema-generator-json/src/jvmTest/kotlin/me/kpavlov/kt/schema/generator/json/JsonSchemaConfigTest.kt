package me.kpavlov.kt.schema.generator.json

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.equals.shouldNotBeEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import me.kpavlov.kt.schema.generator.core.ir.ListNode
import me.kpavlov.kt.schema.generator.core.ir.MapNode
import me.kpavlov.kt.schema.generator.core.ir.ObjectNode
import me.kpavlov.kt.schema.generator.core.ir.PrimitiveKind
import me.kpavlov.kt.schema.generator.core.ir.PrimitiveNode
import me.kpavlov.kt.schema.generator.core.ir.Property
import me.kpavlov.kt.schema.generator.core.ir.TypeGraph
import me.kpavlov.kt.schema.generator.core.ir.TypeId
import me.kpavlov.kt.schema.generator.core.ir.TypeRef
import me.kpavlov.kt.schema.json.ArrayPropertyDefinition
import me.kpavlov.kt.schema.json.NumericPropertyDefinition
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
            me.kpavlov.kt.schema.generator.reflect.ReflectionClassIntrospector
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
            me.kpavlov.kt.schema.generator.reflect.ReflectionClassIntrospector
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
            me.kpavlov.kt.schema.generator.reflect.ReflectionClassIntrospector
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
            "includeOpenAPIPolymorphicDiscriminator=${config.includeOpenAPIPolymorphicDiscriminator}, " +
            "allowAdditionalProperties=${config.allowAdditionalProperties}" +
            ")"
    }

    @Test
    fun `allowAdditionalProperties should take part in equals and hashCode`() {
        val denying = JsonSchemaConfig(allowAdditionalProperties = false)
        val allowing = JsonSchemaConfig(allowAdditionalProperties = true)

        denying shouldBe JsonSchemaConfig.Default
        denying shouldNotBeEqual allowing
        denying.hashCode() shouldNotBe allowing.hashCode()
    }

    @Test
    fun `should allow disabling both useUnionTypes and useNullableField`() {
        val config = JsonSchemaConfig(useUnionTypes = false, useNullableField = false)

        assertSoftly(config) {
            useUnionTypes shouldBe false
            useNullableField shouldBe false
        }
    }

    @Test
    fun `should reject enabling both useUnionTypes and useNullableField`() {
        shouldThrow<IllegalArgumentException> {
            JsonSchemaConfig(useUnionTypes = true, useNullableField = true)
        }
    }

    @Test
    fun `Lenient preset should be compact and permissive`() {
        assertSoftly(JsonSchemaConfig.Lenient) {
            respectDefaultPresence shouldBe true
            requireNullableFields shouldBe false
            useUnionTypes shouldBe false
            useNullableField shouldBe false
            includePolymorphicDiscriminator shouldBe true
            includeOpenAPIPolymorphicDiscriminator shouldBe false
            allowAdditionalProperties shouldBe true
        }
    }

    @Test
    fun `existing presets should keep denying additional properties`() {
        listOf(JsonSchemaConfig.Default, JsonSchemaConfig.Strict, JsonSchemaConfig.OpenAPI)
            .map { it.allowAdditionalProperties } shouldBe listOf(false, false, false)
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
                            ),
                    ),
            )
        val schema = TypeGraphToJsonSchemaTransformer(JsonSchemaConfig.Default).transform(graph, "Root")
        (schema.properties["items"] as ArrayPropertyDefinition).description shouldBe "property description"
    }

    //endregion

    @Test
    fun `should keep the six-argument constructor for binary compatibility`() {
        val constructor = JsonSchemaConfig::class.java.getConstructor(*Array(6) { Boolean::class.java })

        val config = constructor.newInstance(false, true, false, true, true, true)

        assertSoftly(config) {
            respectDefaultPresence shouldBe false
            requireNullableFields shouldBe true
            useUnionTypes shouldBe false
            useNullableField shouldBe true
            includePolymorphicDiscriminator shouldBe true
            includeOpenAPIPolymorphicDiscriminator shouldBe true
            allowAdditionalProperties shouldBe false
        }
    }
}
