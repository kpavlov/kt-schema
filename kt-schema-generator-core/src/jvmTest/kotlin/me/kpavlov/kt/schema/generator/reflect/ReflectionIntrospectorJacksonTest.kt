package me.kpavlov.kt.schema.generator.reflect

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonPropertyDescription
import com.fasterxml.jackson.annotation.JsonTypeName
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import me.kpavlov.kt.schema.generator.core.ir.ObjectNode
import me.kpavlov.kt.schema.generator.core.ir.PolymorphicNode
import me.kpavlov.kt.schema.generator.core.ir.TypeRef
import kotlin.test.Test

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
}
