package me.kpavlov.kt.schema.generator.reflect

import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import me.kpavlov.kt.schema.Description
import me.kpavlov.kt.schema.SchemaIgnore
import me.kpavlov.kt.schema.generator.core.ir.AnyNode
import me.kpavlov.kt.schema.generator.core.ir.EnumNode
import me.kpavlov.kt.schema.generator.core.ir.ListNode
import me.kpavlov.kt.schema.generator.core.ir.MapNode
import me.kpavlov.kt.schema.generator.core.ir.ObjectNode
import me.kpavlov.kt.schema.generator.core.ir.PolymorphicNode
import me.kpavlov.kt.schema.generator.core.ir.PrimitiveKind
import me.kpavlov.kt.schema.generator.core.ir.PrimitiveNode
import me.kpavlov.kt.schema.generator.core.ir.TypeId
import me.kpavlov.kt.schema.generator.core.ir.TypeRef
import kotlin.test.Test

class ReflectionIntrospectorTest {
    @Description("A user model")
    data class User(
        @property:Description("The name of the user")
        val name: String,
        val age: Int?,
        val email: String = "n/a",
        val tags: List<String>,
        val attributes: Map<String, Int>?,
    )

    @Description("Available colors")
    @Suppress("unused")
    enum class Color { RED, GREEN, BLUE }

    data class WithEnum(
        val color: Color,
    )

    @Suppress("unused", "AbstractClassCanBeInterface")
    sealed class Shape {
        @Description("Circle shape")
        data class Circle(
            val radius: Double,
        ) : Shape()

        @Description("Rectangle shape")
        data class Rectangle(
            val width: Double,
            val height: Double,
        ) : Shape()
    }

    @Suppress("unused")
    sealed interface Vehicle {
        sealed interface Motorized : Vehicle {
            data class Car(
                val doors: Int,
            ) : Motorized

            data class Truck(
                val payload: Double,
            ) : Motorized
        }

        data class Bicycle(
            val gears: Int,
        ) : Vehicle
    }

    @Suppress("unused")
    sealed interface Event {
        data class Click(
            val x: Int,
            val y: Int,
        ) : Event

        data class PageView(
            val url: String,
        ) : Event

        @SchemaIgnore
        data class Internal(
            val trace: String,
        ) : Event
    }

    data class WithAny(
        val content: Any,
        val optContent: Any?,
        val metadata: Map<String, Any>,
    )

    data class WithStarProjections(
        val items: List<*>,
        val mapping: Map<*, *>,
    )

    @JvmInline
    value class Age(
        val value: Int,
    )

    @Description("Distance in meters")
    @JvmInline
    value class DescribedDistance(
        val value: Double,
    )

    @Description("Tag list")
    @JvmInline
    value class DescribedTags(
        val items: List<String>,
    )

    data class WithDescribedCollectionValueClass(
        val tags: DescribedTags,
    )

    data class WithInlineValueClass(
        val age: Age,
        val nullableAge: Age?,
        val distance: DescribedDistance,
    )

    @JvmInline
    value class RecursiveWrapper(
        val items: List<RecursiveWrapper>,
    )

    data class WithRecursiveInlineValueClass(
        val wrapper: RecursiveWrapper,
    )

    @JvmInline
    value class Wrapper<T>(
        val value: T,
    )

    @JvmInline
    value class ListWrapper<T>(
        val items: List<T>,
    )

    data class WithGenericValueClass(
        val intWrapper: Wrapper<Int>,
        val nullableStringWrapper: Wrapper<String?>,
        val listWrapper: ListWrapper<Int>,
        val starWrapper: Wrapper<*>,
    )

    @JvmInline
    value class Hop1(
        val stop: Stop1,
    )

    data class Stop1(
        val next: Hop2,
    )

    @JvmInline
    value class Hop2(
        val stop: Stop2,
    )

    data class Stop2(
        val next: Hop3,
    )

    @JvmInline
    value class Hop3(
        val stop: Stop3,
    )

    data class Stop3(
        val next: Hop4,
    )

    @JvmInline
    value class Hop4(
        val stop: Stop4,
    )

    data class Stop4(
        val next: Hop5,
    )

    @JvmInline
    value class Hop5(
        val stop: Stop5,
    )

    data class Stop5(
        val next: Hop6,
    )

    @JvmInline
    value class Hop6(
        val stop: Stop6,
    )

    data class Stop6(
        val next: Hop7,
    )

    @JvmInline
    value class Hop7(
        val stop: Stop7,
    )

    data class Stop7(
        val next: Hop8,
    )

    @JvmInline
    value class Hop8(
        val stop: Stop8,
    )

    data class Stop8(
        val next: Hop9,
    )

    @JvmInline
    value class Hop9(
        val stop: Stop9,
    )

    data class Stop9(
        val leaf: String,
    )

    data class WithDeepValueClassChain(
        val first: Hop1,
    )

    data class WithNestedGenericValueClass(
        val nested: Wrapper<Wrapper<Int>>,
    )

    private val introspector = ReflectionClassIntrospector

    @Test
    @Suppress("LongMethod")
    fun `introspects object with primitives list map nullability and defaults`() {
        val graph = introspector.introspect(User::class)

        // Root must be a ref to the User id (serial name)
        val rootRef = graph.root.shouldBeInstanceOf<TypeRef.Ref>()
        rootRef.id.value shouldBe User::class.qualifiedName
        rootRef.nullable shouldBe false

        val userNode = graph.nodes[rootRef.id].shouldBeInstanceOf<ObjectNode>()

        // Verify class-level description
        userNode.description shouldBe "A user model"

        // Required should include all without defaults: name, age, tags, attributes (email has default)
        userNode.required.shouldContainExactlyInAnyOrder(setOf("name", "age", "tags", "attributes"))

        // Properties: check types
        val props = userNode.properties.associateBy { it.name }

        // Verify property with description and required status
        props.getValue("name").apply {
            description shouldBe "The name of the user"
            hasDefaultValue shouldBe false
            type.shouldBeInstanceOf<TypeRef.Inline> { inline ->
                inline.node.shouldBeInstanceOf<PrimitiveNode> { prim ->
                    prim.kind shouldBe PrimitiveKind.STRING
                }
                inline.nullable shouldBe false
            }
        }

        // age is nullable but still required (no default value)
        props.getValue("age").apply {
            hasDefaultValue shouldBe false
            type.shouldBeInstanceOf<TypeRef.Inline> { inline ->
                inline.node.shouldBeInstanceOf<PrimitiveNode> { prim ->
                    prim.kind shouldBe PrimitiveKind.INT
                }
                inline.nullable shouldBe true
            }
        }

        // email has default value, so hasDefaultValue should be true
        props.getValue("email").apply {
            hasDefaultValue shouldBe true
            type.shouldBeInstanceOf<TypeRef.Inline> { inline ->
                inline.node.shouldBeInstanceOf<PrimitiveNode> { prim ->
                    prim.kind shouldBe PrimitiveKind.STRING
                }
            }
        }

        // tags is required (no default)
        props.getValue("tags").apply {
            hasDefaultValue shouldBe false
        }

        // attributes is nullable but required (no default)
        props.getValue("attributes").apply {
            hasDefaultValue shouldBe false
        }

        // Verify collection types
        props.getValue("tags").type.shouldBeInstanceOf<TypeRef.Inline> { inline ->
            inline.node.shouldBeInstanceOf<ListNode> { list ->
                list.element.shouldBeInstanceOf<TypeRef.Inline> { el ->
                    el.node.shouldBeInstanceOf<PrimitiveNode> { prim ->
                        prim.kind shouldBe PrimitiveKind.STRING
                    }
                }
            }
        }

        props.getValue("attributes").type.shouldBeInstanceOf<TypeRef.Inline> { inline ->
            inline.nullable shouldBe true
            inline.node.shouldBeInstanceOf<MapNode> { map ->
                map.key.shouldBeInstanceOf<TypeRef.Inline> { k ->
                    k.node.shouldBeInstanceOf<PrimitiveNode> { prim ->
                        prim.kind shouldBe PrimitiveKind.STRING
                    }
                }
                map.value.shouldBeInstanceOf<TypeRef.Inline> { v ->
                    v.node.shouldBeInstanceOf<PrimitiveNode> { prim ->
                        prim.kind shouldBe PrimitiveKind.INT
                    }
                }
            }
        }
    }

    @Test
    fun `introspects enum and adds node with entries and description`() {
        val graph = introspector.introspect(WithEnum::class)

        val rootRef = graph.root.shouldBeInstanceOf<TypeRef.Ref>()
        val withEnumNode = graph.nodes[rootRef.id].shouldBeInstanceOf<ObjectNode>()
        val colorProp = withEnumNode.properties.first { it.name == "color" }

        val colorRef = colorProp.type.shouldBeInstanceOf<TypeRef.Ref>()
        val enumNode = graph.nodes[colorRef.id].shouldBeInstanceOf<EnumNode>()

        // Verify enum entries and description
        enumNode.entries.shouldContainExactlyInAnyOrder(listOf("RED", "GREEN", "BLUE"))
        enumNode.description shouldBe "Available colors"
    }

    @Test
    fun `introspects sealed polymorphic adds polymorphic node and subtype objects`() {
        val graph = introspector.introspect(Shape::class)

        val rootRef = graph.root.shouldBeInstanceOf<TypeRef.Ref>()
        val polyNode = graph.nodes[rootRef.id].shouldNotBeNull().shouldBeInstanceOf<PolymorphicNode>()

        // Verify polymorphic node has no description (Shape class is not annotated)
        polyNode.description shouldBe null

        // Discriminator should be required
        polyNode.discriminator shouldNotBeNull {
            name shouldBe "type"
        }

        // Verify subtypes use qualified names (Parent.Child pattern)
        val subtypeIds = polyNode.subtypes.map { it.id.value }.toSet()
        subtypeIds.shouldContainExactlyInAnyOrder(
            setOf(
                "me.kpavlov.kt.schema.generator.reflect.ReflectionIntrospectorTest.Shape.Circle",
                "me.kpavlov.kt.schema.generator.reflect.ReflectionIntrospectorTest.Shape.Rectangle",
            ),
        )

        // Verify each subtype node is registered with qualified name
        val circleNode =
            graph.nodes[
                TypeId(
                    "me.kpavlov.kt.schema.generator.reflect.ReflectionIntrospectorTest.Shape.Circle",
                ),
            ].shouldNotBeNull()
                .shouldBeInstanceOf<ObjectNode>()
        circleNode.description shouldBe "Circle shape"

        val rectangleNode =
            graph.nodes[
                TypeId(
                    "me.kpavlov.kt.schema.generator.reflect.ReflectionIntrospectorTest.Shape.Rectangle",
                ),
            ].shouldNotBeNull()
                .shouldBeInstanceOf<ObjectNode>()
        rectangleNode.description shouldBe "Rectangle shape"
    }

    @Test
    fun `sealed class excludes @SchemaIgnore subtypes from polymorphic node`() {
        val graph = introspector.introspect(Event::class)

        val rootRef = graph.root.shouldBeInstanceOf<TypeRef.Ref>()
        val polyNode = graph.nodes[rootRef.id].shouldNotBeNull().shouldBeInstanceOf<PolymorphicNode>()

        val subtypeIds = polyNode.subtypes.map { it.id.value }.toSet()
        subtypeIds.shouldContainExactlyInAnyOrder(
            setOf(
                "me.kpavlov.kt.schema.generator.reflect.ReflectionIntrospectorTest.Event.Click",
                "me.kpavlov.kt.schema.generator.reflect.ReflectionIntrospectorTest.Event.PageView",
            ),
        )

        // Internal should not appear in nodes
        graph.nodes.keys.none { it.value.contains("Internal") } shouldBe true
    }

    @Test
    fun `introspects kotlin Any as inline AnyNode for non-nullable nullable and map value`() {
        val graph = introspector.introspect(WithAny::class)

        val root = graph.root.shouldBeInstanceOf<TypeRef.Ref>()
        val node = graph.nodes[root.id].shouldBeInstanceOf<ObjectNode>()
        val props = node.properties.associateBy { it.name }

        // non-nullable Any → TypeRef.Inline(AnyNode(), nullable=false)
        props.getValue("content").type.shouldBeInstanceOf<TypeRef.Inline> {
            it.node.shouldBeInstanceOf<AnyNode>()
            it.nullable shouldBe false
        }

        // nullable Any? → TypeRef.Inline(AnyNode(), nullable=true)
        props.getValue("optContent").type.shouldBeInstanceOf<TypeRef.Inline> {
            it.node.shouldBeInstanceOf<AnyNode>()
            it.nullable shouldBe true
        }

        // Map<String, Any> value type → AnyNode
        props.getValue("metadata").type.shouldBeInstanceOf<TypeRef.Inline> { mapRef ->
            mapRef.node.shouldBeInstanceOf<MapNode> { mapNode ->
                mapNode.value.shouldBeInstanceOf<TypeRef.Inline> {
                    it.node.shouldBeInstanceOf<AnyNode>()
                }
            }
        }

        // kotlin.Any does not create a named node in the graph
        graph.nodes.keys.none { it.value == "kotlin.Any" } shouldBe true
    }

    @Test
    fun `introspects star-projected collections without surfacing raw type parameters`() {
        val graph = introspector.introspect(WithStarProjections::class)

        val root = graph.root.shouldBeInstanceOf<TypeRef.Ref>()
        val node = graph.nodes[root.id].shouldBeInstanceOf<ObjectNode>()
        val props = node.properties.associateBy { it.name }

        // List<*> falls back to a STRING element instead of leaking the `E` type parameter
        props.getValue("items").type.shouldBeInstanceOf<TypeRef.Inline> { inline ->
            inline.node.shouldBeInstanceOf<ListNode> { list ->
                list.element.shouldBeInstanceOf<TypeRef.Inline> { el ->
                    el.node.shouldBeInstanceOf<PrimitiveNode> { prim ->
                        prim.kind shouldBe PrimitiveKind.STRING
                    }
                }
            }
        }

        // Map<*, *> falls back to STRING key/value instead of leaking type parameters
        props.getValue("mapping").type.shouldBeInstanceOf<TypeRef.Inline> { inline ->
            inline.node.shouldBeInstanceOf<MapNode> { map ->
                map.key.shouldBeInstanceOf<TypeRef.Inline> { k ->
                    k.node.shouldBeInstanceOf<PrimitiveNode> { prim ->
                        prim.kind shouldBe PrimitiveKind.STRING
                    }
                }
                map.value.shouldBeInstanceOf<TypeRef.Inline> { v ->
                    v.node.shouldBeInstanceOf<PrimitiveNode> { prim ->
                        prim.kind shouldBe PrimitiveKind.STRING
                    }
                }
            }
        }
    }

    @Test
    fun `flattens inline value class to its wrapped primitive, carrying nullability and class description`() {
        val graph = introspector.introspect(WithInlineValueClass::class)

        val root = graph.root.shouldBeInstanceOf<TypeRef.Ref>()
        val node = graph.nodes[root.id].shouldBeInstanceOf<ObjectNode>()
        val props = node.properties.associateBy { it.name }

        // Age(Int) flattens to a bare INT primitive — no {"value": ...} wrapper.
        props.getValue("age").type.shouldBeInstanceOf<TypeRef.Inline> { inline ->
            inline.node.shouldBeInstanceOf<PrimitiveNode> { prim ->
                prim.kind shouldBe PrimitiveKind.INT
            }
            inline.nullable shouldBe false
        }

        // Age? propagates nullability onto the flattened primitive.
        props.getValue("nullableAge").type.shouldBeInstanceOf<TypeRef.Inline> { inline ->
            inline.nullable shouldBe true
        }

        // A class-level @Description on the value class lands on the flattened primitive.
        props.getValue("distance").type.shouldBeInstanceOf<TypeRef.Inline> { inline ->
            inline.node.shouldBeInstanceOf<PrimitiveNode> { prim ->
                prim.kind shouldBe PrimitiveKind.DOUBLE
                prim.description shouldBe "Distance in meters"
            }
        }

        // Neither Age nor DescribedDistance should appear as a named node in the graph.
        graph.nodes.keys.none { it.value.endsWith(".Age") || it.value.endsWith(".DescribedDistance") } shouldBe true
    }

    @Test
    fun `carries class description onto flattened collection of inline value class`() {
        val graph = introspector.introspect(WithDescribedCollectionValueClass::class)

        val root = graph.root.shouldBeInstanceOf<TypeRef.Ref>()
        val node = graph.nodes[root.id].shouldBeInstanceOf<ObjectNode>()

        node.properties.single().type.shouldBeInstanceOf<TypeRef.Inline> { inline ->
            inline.node.shouldBeInstanceOf<ListNode> { list ->
                list.description shouldBe "Tag list"
            }
        }
    }

    @Test
    fun `flattens generic inline value class using the type arguments from the use site`() {
        val graph = introspector.introspect(WithGenericValueClass::class)

        val root = graph.root.shouldBeInstanceOf<TypeRef.Ref>()
        val node = graph.nodes[root.id].shouldBeInstanceOf<ObjectNode>()
        val props = node.properties.associateBy { it.name }

        // Wrapper<Int> resolves T to Int.
        props.getValue("intWrapper").type.shouldBeInstanceOf<TypeRef.Inline> { inline ->
            inline.node.shouldBeInstanceOf<PrimitiveNode> { prim ->
                prim.kind shouldBe PrimitiveKind.INT
            }
            inline.nullable shouldBe false
        }

        // Wrapper<String?> resolves T to String? and carries the nullability.
        props.getValue("nullableStringWrapper").type.shouldBeInstanceOf<TypeRef.Inline> { inline ->
            inline.node.shouldBeInstanceOf<PrimitiveNode> { prim ->
                prim.kind shouldBe PrimitiveKind.STRING
            }
            inline.nullable shouldBe true
        }

        // ListWrapper<Int> resolves T nested inside List<T>.
        props.getValue("listWrapper").type.shouldBeInstanceOf<TypeRef.Inline> { inline ->
            inline.node.shouldBeInstanceOf<ListNode> { list ->
                list.element.shouldBeInstanceOf<TypeRef.Inline> { element ->
                    element.node.shouldBeInstanceOf<PrimitiveNode> { prim ->
                        prim.kind shouldBe PrimitiveKind.INT
                    }
                }
            }
        }

        // Wrapper<*> is treated as Wrapper<Any?>: any value, nullable.
        props.getValue("starWrapper").type.shouldBeInstanceOf<TypeRef.Inline> { inline ->
            inline.node.shouldBeInstanceOf<AnyNode>()
            inline.nullable shouldBe true
        }

        graph.nodes.keys.none { it.value.endsWith("Wrapper") } shouldBe true
    }

    @Test
    fun `inline value class wrapping a collection of itself is defined by its wrapped shape`() {
        val graph = introspector.introspect(WithRecursiveInlineValueClass::class)

        val wrapperId = TypeId(RecursiveWrapper::class.qualifiedName!!)
        val list = graph.nodes[wrapperId].shouldBeInstanceOf<ListNode>()
        list.element.shouldBeInstanceOf<TypeRef.Ref>().id shouldBe wrapperId
    }

    @Test
    fun `flattens nested instantiations of the same generic inline value class`() {
        val graph = introspector.introspect(WithNestedGenericValueClass::class)

        val root = graph.root.shouldBeInstanceOf<TypeRef.Ref>()
        val node = graph.nodes[root.id].shouldBeInstanceOf<ObjectNode>()
        node.properties.single().type.shouldBeInstanceOf<TypeRef.Inline> { inline ->
            inline.node.shouldBeInstanceOf<PrimitiveNode>().kind shouldBe PrimitiveKind.INT
        }
        graph.nodes.keys.none { it.value.endsWith("Wrapper") } shouldBe true
    }

    @Test
    fun `does not cut off value classes nested through objects`() {
        val graph = introspector.introspect(WithDeepValueClassChain::class)

        val lastStop = graph.nodes[TypeId(Stop9::class.qualifiedName!!)].shouldBeInstanceOf<ObjectNode>()
        lastStop.properties.single().type.shouldBeInstanceOf<TypeRef.Inline> { inline ->
            inline.node.shouldBeInstanceOf<PrimitiveNode>().kind shouldBe PrimitiveKind.STRING
        }
        graph.nodes[TypeId(Stop8::class.qualifiedName!!)].shouldBeInstanceOf<ObjectNode> { stop ->
            stop.properties.single().type shouldBe TypeRef.Ref(TypeId(Stop9::class.qualifiedName!!))
        }
    }
}
