package me.kpavlov.kt.schema.generator.reflect

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonPropertyDescription
import com.fasterxml.jackson.annotation.JsonTypeName
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import me.kpavlov.kt.schema.generator.core.ir.AnyNode
import me.kpavlov.kt.schema.generator.core.ir.EnumNode
import me.kpavlov.kt.schema.generator.core.ir.ObjectNode
import me.kpavlov.kt.schema.generator.core.ir.PolymorphicNode
import me.kpavlov.kt.schema.generator.core.ir.PrimitiveKind
import me.kpavlov.kt.schema.generator.core.ir.PrimitiveNode
import me.kpavlov.kt.schema.generator.core.ir.TypeNode
import me.kpavlov.kt.schema.generator.core.ir.TypeRef
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.ArrayNode
import tools.jackson.databind.node.BaseJsonNode
import tools.jackson.databind.node.BigIntegerNode
import tools.jackson.databind.node.BinaryNode
import tools.jackson.databind.node.BooleanNode
import tools.jackson.databind.node.ContainerNode
import tools.jackson.databind.node.DecimalNode
import tools.jackson.databind.node.DoubleNode
import tools.jackson.databind.node.FloatNode
import tools.jackson.databind.node.IntNode
import tools.jackson.databind.node.LongNode
import tools.jackson.databind.node.MissingNode
import tools.jackson.databind.node.NullNode
import tools.jackson.databind.node.NumericFPNode
import tools.jackson.databind.node.NumericIntNode
import tools.jackson.databind.node.NumericNode
import tools.jackson.databind.node.ObjectNode as JacksonObjectNode
import tools.jackson.databind.node.POJONode
import tools.jackson.databind.node.ShortNode
import tools.jackson.databind.node.StringNode
import tools.jackson.databind.node.ValueNode
import kotlin.reflect.KType
import kotlin.reflect.KTypeProjection
import kotlin.reflect.full.createType
import kotlin.test.Test

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReflectionIntrospectorJacksonTest {
    @Suppress("unused")
    data class JacksonAnnotatedUser(
        @param:JsonProperty("user_name") val userName: String,
        @param:JsonProperty("email_address") val emailAddress: String = "n/a",
        @field:JsonIgnore val password: String = "secret",
    )

    @Suppress("unused")
    sealed interface JacksonVehicle {
        @JsonTypeName("car")
        data class Car(val doors: Int) : JacksonVehicle

        @JsonTypeName("truck")
        data class Truck(val payload: Double) : JacksonVehicle
    }

    @Suppress("unused")
    sealed class SealedWithHiddenParentProperty {
        @get:JsonIgnore
        val internalId: String = "hidden-parent"

        data class Variant(val visible: Int) : SealedWithHiddenParentProperty()
    }

    @Suppress("unused")
    object SingletonWithHiddenProperty {
        const val visible: Int = 1

        @get:JsonIgnore
        val internalToken: String = "hidden-singleton"
    }

    @Suppress("unused")
    data class JacksonGetterAnnotatedUser(
        @get:JsonProperty("user_login") val userName: String,
        @get:JsonProperty("email_address")
        @get:JsonPropertyDescription("The user's email address") val emailAddress: String = "n/a",
        @get:JsonIgnore val sessionToken: String = "token",
    )

    @Suppress("unused")
    sealed interface JacksonDocument {
        val id: String
    }

    @Suppress("unused")
    class JacksonReport(
        val body: String,
    ) : JacksonDocument {
        @get:JsonProperty("document_id")
        @get:JsonPropertyDescription("The document identifier")
        override val id: String = "r-1"
    }

    @Suppress("unused")
    sealed interface JacksonMemo {
        @get:JsonProperty("memo_id")
        val id: String
    }

    @Suppress("unused")
    class JacksonNote(
        val body: String,
    ) : JacksonMemo {
        override val id: String = "n-1"
    }

    @Suppress("unused", "AbstractClassCanBeInterface")
    sealed class JacksonRecord {
        abstract val id: String
    }

    @Suppress("unused")
    data class JacksonEntry(
        @get:JsonProperty("entry_id") override val id: String,
        val label: String,
    ) : JacksonRecord()

    @Suppress("unused")
    enum class Priority {
        LOW,

        @JsonEnumDefaultValue
        MEDIUM,
        HIGH,
    }

    @Suppress("unused")
    data class WithEnumDefault(
        val priority: Priority,
    )

    @Suppress("unused")
    data class WithAnnotationDefault(
        @param:JsonProperty(defaultValue = "30") val timeout: Int,
    )

    @Suppress("unused")
    data class WithRealDefaultPrecedence(
        @param:JsonProperty(defaultValue = "IGNORED") val label: String = "REAL",
    )

    private val introspector = ReflectionClassIntrospector

    @Test
    fun `honors Jackson @JsonProperty name override in properties and required`() {
        val graph = introspector.introspect(JacksonAnnotatedUser::class)

        val root = graph.root.shouldBeInstanceOf<TypeRef.Ref>()
        val node = graph.nodes[root.id].shouldBeInstanceOf<ObjectNode>()

        node.properties.map { it.name } shouldBe listOf("user_name", "email_address")
        node.required shouldBe setOf("user_name")
    }

    @Test
    fun `excludes properties annotated with Jackson @JsonIgnore`() {
        val graph = introspector.introspect(JacksonAnnotatedUser::class)

        val root = graph.root.shouldBeInstanceOf<TypeRef.Ref>()
        val node = graph.nodes[root.id].shouldBeInstanceOf<ObjectNode>()

        node.properties.map { it.name } shouldNotContain "password"
        node.required shouldNotContain "password"
    }

    @Test
    fun `honors Jackson @JsonTypeName on sealed subtypes in defs and discriminator`() {
        val graph = introspector.introspect(JacksonVehicle::class)

        val root = graph.root.shouldBeInstanceOf<TypeRef.Ref>()
        val polyNode = graph.nodes[root.id].shouldBeInstanceOf<PolymorphicNode>()

        val subtypeIds = polyNode.subtypes.map { it.id.value }.toSet()
        subtypeIds.shouldContainExactlyInAnyOrder(setOf("car", "truck"))

        polyNode.discriminator.mapping?.keys shouldBe setOf("car", "truck")
    }

    @Test
    fun `honors getter-targeted Jackson name override description and ignore`() {
        val graph = introspector.introspect(JacksonGetterAnnotatedUser::class)

        val root = graph.root.shouldBeInstanceOf<TypeRef.Ref>()
        val node = graph.nodes[root.id].shouldBeInstanceOf<ObjectNode>()

        node.properties.map { it.name } shouldBe listOf("user_login", "email_address")
        node.properties.associateBy { it.name }.getValue("email_address").description shouldBe
            "The user's email address"
        node.required shouldBe setOf("user_login")
        node.properties.map { it.name } shouldNotContain "sessionToken"
    }

    @Test
    fun `applies Jackson annotations to inherited sealed properties`() {
        val graph = introspector.introspect(JacksonReport::class)

        val root = graph.root.shouldBeInstanceOf<TypeRef.Ref>()
        val node = graph.nodes[root.id].shouldBeInstanceOf<ObjectNode>()

        node.properties.map { it.name } shouldBe listOf("body", "document_id")
        node.properties.associateBy { it.name }.getValue("document_id").apply {
            description shouldBe "The document identifier"
            hasDefaultValue shouldBe true
        }
        node.required shouldBe setOf("body", "document_id")
    }

    @Test
    fun `honors Jackson name override declared only on the sealed parent property`() {
        val graph = introspector.introspect(JacksonNote::class)

        val root = graph.root.shouldBeInstanceOf<TypeRef.Ref>()
        val node = graph.nodes[root.id].shouldBeInstanceOf<ObjectNode>()

        node.properties.map { it.name } shouldBe listOf("body", "memo_id")
        node.required shouldBe setOf("body", "memo_id")
    }

    @Test
    fun `does not duplicate a constructor-overridden sealed property under its raw name`() {
        val graph = introspector.introspect(JacksonEntry::class)

        val root = graph.root.shouldBeInstanceOf<TypeRef.Ref>()
        val node = graph.nodes[root.id].shouldBeInstanceOf<ObjectNode>()

        node.properties.map { it.name } shouldBe listOf("entry_id", "label")
        node.properties.map { it.name } shouldNotContain "id"
        node.required shouldBe setOf("entry_id", "label")
    }

    @Test
    fun `excludes sealed-parent inherited property annotated with @get JsonIgnore`() {
        val graph = introspector.introspect(SealedWithHiddenParentProperty.Variant::class)

        val root = graph.root.shouldBeInstanceOf<TypeRef.Ref>()
        val node = graph.nodes[root.id].shouldBeInstanceOf<ObjectNode>()

        node.properties.map { it.name } shouldNotContain "internalId"
        node.required shouldNotContain "internalId"
    }

    @Test
    fun `excludes singleton object property annotated with @get JsonIgnore`() {
        val graph = introspector.introspect(SingletonWithHiddenProperty::class)

        val root = graph.root.shouldBeInstanceOf<TypeRef.Ref>()
        val node = graph.nodes[root.id].shouldBeInstanceOf<ObjectNode>()

        node.properties.map { it.name } shouldNotContain "internalToken"
        node.required shouldNotContain "internalToken"
    }

    @Test
    fun `introspects enum default value from JsonEnumDefaultValue annotation`() {
        val graph = introspector.introspect(WithEnumDefault::class)

        val root = graph.root.shouldBeInstanceOf<TypeRef.Ref>()
        val node = graph.nodes[root.id].shouldBeInstanceOf<ObjectNode>()
        val priorityRef = node.properties.first { it.name == "priority" }.type.shouldBeInstanceOf<TypeRef.Ref>()
        val enumNode = graph.nodes[priorityRef.id].shouldBeInstanceOf<EnumNode>()

        enumNode.defaultValue shouldBe "MEDIUM"
    }

    @Test
    fun `populates property default value from JsonProperty defaultValue annotation`() {
        val graph = introspector.introspect(WithAnnotationDefault::class)

        val root = graph.root.shouldBeInstanceOf<TypeRef.Ref>()
        val node = graph.nodes[root.id].shouldBeInstanceOf<ObjectNode>()
        val timeoutProp = node.properties.first { it.name == "timeout" }

        timeoutProp.hasDefaultValue shouldBe true
        timeoutProp.defaultValue shouldBe "30"
        node.required shouldNotContain "timeout"
    }

    @Test
    fun `real Kotlin default value takes precedence over JsonProperty defaultValue annotation`() {
        val graph = introspector.introspect(WithRealDefaultPrecedence::class)

        val root = graph.root.shouldBeInstanceOf<TypeRef.Ref>()
        val node = graph.nodes[root.id].shouldBeInstanceOf<ObjectNode>()

        node.properties.first { it.name == "label" }.defaultValue shouldBe "REAL"
    }

    //region Jackson databind node types

    @ParameterizedTest(name = "{0} (nullable={1}) -> {2}")
    @MethodSource("jacksonNodeTypeCases")
    fun `introspects jackson databind node types`(
        type: KType,
        nullable: Boolean,
        expectedNode: TypeNode,
    ) {
        // Constructed directly (rather than via ReflectionClassIntrospector) so bare Jackson
        // KTypes can be resolved without declaring a one-field wrapper class per node type.
        val context = ReflectionIntrospectionContext()

        val ref = context.toRef(type)

        val inline = ref.shouldBeInstanceOf<TypeRef.Inline>()
        inline.node shouldBe expectedNode
        inline.nullable shouldBe nullable
        // None of the Jackson node types create named nodes in the graph
        context.nodes.keys.none { it.value.startsWith("tools.jackson.databind") } shouldBe true
    }

    fun jacksonNodeTypeCases() =
        listOf(
            // Arbitrary/unknown-shape types stay opaque -> emit `{}`
            Arguments.of(JsonNode::class.createType(), false, AnyNode()),
            Arguments.of(JsonNode::class.createType(nullable = true), true, AnyNode()),
            Arguments.of(JacksonObjectNode::class.createType(), false, AnyNode()),
            Arguments.of(ArrayNode::class.createType(), false, AnyNode()),
            Arguments.of(ValueNode::class.createType(), false, AnyNode()),
            Arguments.of(BaseJsonNode::class.createType(), false, AnyNode()),
            Arguments.of(POJONode::class.createType(), false, AnyNode()),
            Arguments.of(MissingNode::class.createType(), false, AnyNode()),
            // ContainerNode<T : ContainerNode<T>> is self-bounded generic, so createType()
            // needs an explicit star projection rather than the default empty argument list.
            Arguments.of(ContainerNode::class.createType(listOf(KTypeProjection.STAR)), false, AnyNode()),
            // NullNode is deliberately left opaque: a dedicated `{"type": "null"}` schema would
            // need a new PrimitiveKind.NULL handled in both JSON Schema transformers.
            Arguments.of(NullNode::class.createType(), false, AnyNode()),
            // Concrete/abstract leaf value types resolve to their real PrimitiveKind
            Arguments.of(StringNode::class.createType(), false, PrimitiveNode(PrimitiveKind.STRING)),
            Arguments.of(BinaryNode::class.createType(), false, PrimitiveNode(PrimitiveKind.STRING)),
            Arguments.of(BooleanNode::class.createType(), false, PrimitiveNode(PrimitiveKind.BOOLEAN)),
            Arguments.of(IntNode::class.createType(), false, PrimitiveNode(PrimitiveKind.INT)),
            Arguments.of(IntNode::class.createType(nullable = true), true, PrimitiveNode(PrimitiveKind.INT)),
            Arguments.of(ShortNode::class.createType(), false, PrimitiveNode(PrimitiveKind.INT)),
            Arguments.of(LongNode::class.createType(), false, PrimitiveNode(PrimitiveKind.LONG)),
            Arguments.of(BigIntegerNode::class.createType(), false, PrimitiveNode(PrimitiveKind.LONG)),
            Arguments.of(NumericIntNode::class.createType(), false, PrimitiveNode(PrimitiveKind.LONG)),
            Arguments.of(DoubleNode::class.createType(), false, PrimitiveNode(PrimitiveKind.DOUBLE)),
            Arguments.of(FloatNode::class.createType(), false, PrimitiveNode(PrimitiveKind.FLOAT)),
            Arguments.of(DecimalNode::class.createType(), false, PrimitiveNode(PrimitiveKind.DOUBLE)),
            Arguments.of(NumericFPNode::class.createType(), false, PrimitiveNode(PrimitiveKind.DOUBLE)),
            Arguments.of(NumericNode::class.createType(), false, PrimitiveNode(PrimitiveKind.DOUBLE)),
        )

    //endregion
}
