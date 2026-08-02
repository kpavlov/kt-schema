package me.kpavlov.kt.schema.generator.json.serialization

import me.kpavlov.kt.schema.generator.core.InternalSchemaGeneratorApi
import me.kpavlov.kt.schema.generator.core.ir.AnyNode
import me.kpavlov.kt.schema.generator.core.ir.BaseIntrospectionContext
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
import me.kpavlov.kt.schema.generator.core.ir.TypeId
import me.kpavlov.kt.schema.generator.core.ir.TypeRef
import me.kpavlov.kt.schema.generator.core.ir.withNullable
import me.kpavlov.kt.schema.generator.json.SerialSchemaIgnore
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlinx.serialization.modules.SerializersModuleCollector
import kotlin.reflect.KClass
import kotlinx.serialization.descriptors.PrimitiveKind as SerialPrimitiveKind

/**
 * Context for introspecting kotlinx.serialization descriptors into Schema IR.
 *
 * Extends [BaseIntrospectionContext] to leverage shared state management
 * (discovered nodes, visiting set for cycle detection, type reference cache).
 *
 * @property json The [Json] configuration used to extract discriminator settings for polymorphic types
 */
@OptIn(InternalSchemaGeneratorApi::class)
@Suppress("TooManyFunctions")
internal class SerializationIntrospectionContext(
    private val json: Json,
    private val config: SerializationClassSchemaIntrospector.Config,
) : BaseIntrospectionContext<SerialDescriptor>() {
    /**
     * Converts a [SerialDescriptor] to a [TypeRef].
     * This is the main entry point for type conversion.
     *
     * Handles:
     * - Nullability from descriptor.isNullable
     * - Primitives (inlined)
     * - Collections (List, Map) (inlined)
     * - Enums (referenced via TypeId)
     * - Objects/Classes (referenced via TypeId)
     * - Polymorphic types (referenced via TypeId)
     */
    @Suppress("ReturnCount")
    override fun toRef(type: SerialDescriptor): TypeRef {
        // Check cache first
        typeRefCache[type]?.let { cachedRef ->
            return if (type.isNullable && !cachedRef.nullable) {
                cachedRef.withNullable(true)
            } else {
                cachedRef
            }
        }

        val nullable = type.isNullable

        // kotlin.Any, java.lang.Object, and configured opaque types all emit {} (empty schema)
        opaqueRefOrNull(type.serialName.removeSuffix("?"), nullable)?.let { return it }

        // Try primitives first (always inlined)
        primitiveFor(type)?.let { primitiveNode ->
            val ref = TypeRef.Inline(primitiveNode, nullable)
            if (!nullable) typeRefCache[type] = ref
            return ref
        }

        // Handle different kinds
        return when (type.kind) {
            is SerialKind.ENUM -> {
                handleEnumType(type, nullable)
            }

            is StructureKind.CLASS, StructureKind.OBJECT -> {
                if (type.isInline) {
                    handleInlineValueClass(type, nullable)
                } else {
                    handleObjectType(type, nullable)
                }
            }

            is StructureKind.MAP -> {
                handleMapType(type, nullable)
            }

            is StructureKind.LIST -> {
                handleListType(type, nullable)
            }

            is PolymorphicKind -> {
                handlePolymorphicType(type, nullable)
            }

            else -> {
                // Fallback: treat unknown kinds as empty objects
                handleUnknownType(type, nullable)
            }
        }
    }

    /**
     * Maps a kotlinx.serialization [SerialDescriptor] with primitive kind to a [PrimitiveNode].
     * Returns null if the descriptor is not a primitive.
     */
    private fun primitiveFor(descriptor: SerialDescriptor): PrimitiveNode? =
        when (descriptor.kind) {
            SerialPrimitiveKind.STRING -> {
                PrimitiveNode(PrimitiveKind.STRING)
            }

            SerialPrimitiveKind.BOOLEAN -> {
                PrimitiveNode(PrimitiveKind.BOOLEAN)
            }

            SerialPrimitiveKind.BYTE, SerialPrimitiveKind.SHORT, SerialPrimitiveKind.INT -> {
                PrimitiveNode(PrimitiveKind.INT)
            }

            SerialPrimitiveKind.LONG -> {
                PrimitiveNode(PrimitiveKind.LONG)
            }

            SerialPrimitiveKind.FLOAT -> {
                PrimitiveNode(PrimitiveKind.FLOAT)
            }

            SerialPrimitiveKind.DOUBLE -> {
                PrimitiveNode(PrimitiveKind.DOUBLE)
            }

            SerialPrimitiveKind.CHAR -> {
                PrimitiveNode(PrimitiveKind.STRING)
            }

            else -> {
                null
            }
        }

    /**
     * Handles enum types by creating an [EnumNode] with entries extracted from descriptor elements.
     */
    private fun handleEnumType(
        descriptor: SerialDescriptor,
        nullable: Boolean,
    ): TypeRef {
        val id = descriptorId(descriptor)

        withCycleDetection(descriptor, id) {
            val entries = (0 until descriptor.elementsCount).map { descriptor.getElementName(it) }
            EnumNode(
                name = descriptor.unwrapSerialName().removeSuffix("?"),
                entries = entries,
                description = extractDescription(descriptor),
            )
        }

        val ref = TypeRef.Ref(id, nullable)
        if (!nullable) typeRefCache[descriptor] = ref
        return ref
    }

    /**
     * Handles inline value classes by delegating to the inner element's type.
     *
     * Inline value classes serialize as their inner value (e.g. `14.5` instead of
     * `{"gramsPerDeciliter": 14.5}`), so the schema must reflect the inner type.
     *
     * If the inline class has a **class-level** description annotation, it is propagated to the
     * flattened primitive node so that it appears in the generated schema. Annotations on the
     * inner `value` property are not used.
     */
    private fun handleInlineValueClass(
        descriptor: SerialDescriptor,
        nullable: Boolean,
    ): TypeRef {
        require(descriptor.elementsCount == 1) { "Inline value class descriptor must have exactly one element" }
        val innerRef = toRef(descriptor.getElementDescriptor(0))
        val description = extractDescription(descriptor)
        val effectiveRef =
            if (innerRef is TypeRef.Inline && innerRef.node is PrimitiveNode) {
                if (description != null) {
                    TypeRef.Inline(
                        (innerRef.node as PrimitiveNode).copy(description = description),
                        innerRef.nullable,
                    )
                } else {
                    innerRef
                }
            } else {
                innerRef
            }
        if (!nullable) typeRefCache[descriptor] = effectiveRef
        return if (nullable && !effectiveRef.nullable) effectiveRef.withNullable(true) else effectiveRef
    }

    /**
     * Handles object/class types by creating an [ObjectNode] with properties.
     */
    private fun handleObjectType(
        descriptor: SerialDescriptor,
        nullable: Boolean,
    ): TypeRef {
        val id = descriptorId(descriptor)

        withCycleDetection(descriptor, id) {
            val properties = mutableListOf<Property>()
            val required = mutableSetOf<String>()

            for (i in 0 until descriptor.elementsCount) {
                val name = descriptor.getElementName(i)
                val elementDescriptor = descriptor.getElementDescriptor(i)
                val elementDescription = extractElementDescription(descriptor, i)
                val typeRef = toRef(elementDescriptor)
                val hasDefault = descriptor.isElementOptional(i)

                if (!hasDefault) {
                    required.add(name)
                }

                properties.add(
                    Property(
                        name = name,
                        type = typeRef,
                        description = elementDescription,
                        hasDefaultValue = hasDefault,
                    ),
                )
            }

            ObjectNode(
                name = descriptor.unwrapSerialName().removeSuffix("?"),
                properties = properties,
                required = required,
                description = extractDescription(descriptor),
            )
        }

        val ref = TypeRef.Ref(id, nullable)
        if (!nullable) typeRefCache[descriptor] = ref
        return ref
    }

    /**
     * Handles list types by creating an inline [ListNode].
     */
    private fun handleListType(
        descriptor: SerialDescriptor,
        nullable: Boolean,
    ): TypeRef {
        val elementDescriptor = descriptor.getElementDescriptor(0)
        val elementRef = toRef(elementDescriptor)
        val node = ListNode(element = elementRef)
        val ref = TypeRef.Inline(node, nullable)
        if (!nullable) typeRefCache[descriptor] = ref
        return ref
    }

    /**
     * Handles map types by creating an inline [MapNode].
     */
    private fun handleMapType(
        descriptor: SerialDescriptor,
        nullable: Boolean,
    ): TypeRef {
        val keyDescriptor = descriptor.getElementDescriptor(0)
        val valueDescriptor = descriptor.getElementDescriptor(1)
        val keyRef = toRef(keyDescriptor)
        val valueRef = toRef(valueDescriptor)
        val node = MapNode(key = keyRef, value = valueRef)
        val ref = TypeRef.Inline(node, nullable)
        if (!nullable) typeRefCache[descriptor] = ref
        return ref
    }

    /**
     * Handles polymorphic types (sealed and open) by creating a [PolymorphicNode].
     *
     * For sealed classes, subtypes are extracted from the descriptor structure.
     * For open polymorphic types, subtypes are resolved from the
     * [SerializersModule][kotlinx.serialization.modules.SerializersModule] registered in the [Json] instance.
     */
    private fun handlePolymorphicType(
        descriptor: SerialDescriptor,
        nullable: Boolean,
    ): TypeRef {
        val id = descriptorId(descriptor)

        withCycleDetection(descriptor, id) {
            // Extract subtypes from the nested structure, excluding @SerialSchemaIgnore-annotated ones
            val subtypeDescriptors =
                extractPolymorphicSubtypes(descriptor)
                    .filter { subtype -> !subtype.isSchemaIgnored() }
            val subtypes =
                subtypeDescriptors
                    .sortedBy { it.serialName }
                    .map { SubtypeRef(TypeId(it.serialName)) }

            // Get discriminator configuration from Json or JsonClassDiscriminator annotation
            val discriminatorName = descriptor.polymorphicDiscriminatorName()

            val discriminator =
                Discriminator(
                    name = discriminatorName,
                    mapping = null, // Mapping is typically derived from serialName
                )

            // Create the polymorphic node
            val node =
                PolymorphicNode(
                    name = descriptor.unwrapSerialName(),
                    subtypes = subtypes,
                    discriminator = discriminator,
                    description = extractDescription(descriptor),
                )

            // Recursively process each subtype to discover their ObjectNodes
            subtypeDescriptors.forEach { toRef(it) }

            node
        }

        val ref = TypeRef.Ref(id, nullable)
        if (!nullable) typeRefCache[descriptor] = ref
        return ref
    }

    /**
     * Extracts subtype descriptors from a polymorphic descriptor.
     *
     * For sealed classes (`PolymorphicKind.SEALED`), subtypes are embedded in the descriptor:
     * ```
     * SerialDescriptor (PolymorphicKind.SEALED)
     *   ├─ element[0] → "klass" discriminator descriptor
     *   └─ element[1] → "value" descriptor containing subtypes
     *        └─ elements → [subtype1, subtype2, ...]
     * ```
     *
     * For open polymorphism (`PolymorphicKind.OPEN`), subtypes are resolved from the
     * [SerializersModule][kotlinx.serialization.modules.SerializersModule] registered in the [Json] instance.
     */
    private fun extractPolymorphicSubtypes(descriptor: SerialDescriptor): List<SerialDescriptor> =
        when (descriptor.kind) {
            is PolymorphicKind.SEALED -> extractSealedSubtypes(descriptor)
            is PolymorphicKind.OPEN -> extractOpenSubtypes(descriptor)
            else -> error("Expected polymorphic descriptor, got ${descriptor.kind}")
        }

    private fun extractSealedSubtypes(descriptor: SerialDescriptor): List<SerialDescriptor> {
        // Standard format produced by the Kotlin serialization compiler plugin for `sealed`
        // classes: a wrapper whose element[0]="type" is the discriminator and element[1]="value"
        // is a synthetic descriptor whose sub-elements are the concrete subtype descriptors.
        if (descriptor.isStandardSealedWrapper()) {
            val valueDescriptor = descriptor.getElementDescriptor(1)
            return (0 until valueDescriptor.elementsCount).map { valueDescriptor.getElementDescriptor(it) }
        }

        // Any other SEALED descriptor comes from a hand-written serializer (e.g. the
        // kotlinx.serialization.json types, which enumerate heterogeneous subtypes such as
        // primitives, maps and lists directly, with no 'type'/'value' wrapper). Such shapes do
        // not map onto a discriminated `oneOf`, so we fail with actionable guidance rather than
        // guessing a structure and emitting a wrong schema.
        error(
            "Cannot derive a polymorphic schema for sealed descriptor '${descriptor.serialName}': " +
                "its structure is not the standard ['type', 'value'] wrapper " +
                "(elements: ${descriptor.elementNames()}). " +
                "If it represents an arbitrary JSON value, add its serial name to " +
                "SerializationClassSchemaIntrospector.Config.opaqueSerialNames.",
        )
    }

    /**
     * True when [kotlinx.serialization.builtins.LongAsStringSerializer.descriptor]
     * has the compiler-generated sealed wrapper shape: element[0] named `type` (the discriminator)
     * and element[1] named `value` (the subtype holder).
     */
    private fun SerialDescriptor.isStandardSealedWrapper(): Boolean =
        elementsCount >= 2 &&
            getElementName(0) == "type" &&
            getElementName(1) == "value"

    private fun SerialDescriptor.elementNames(): List<String> =
        (0 until elementsCount).map { getElementName(it) }

    /**
     * Extracts subtype descriptors for open polymorphic types by querying the
     * [SerializersModule][kotlinx.serialization.modules.SerializersModule].
     *
     * Iterates all polymorphic registrations in the module and collects descriptors
     * whose base class matches the given [descriptor]'s serial name.
     *
     * @throws IllegalStateException if no subtypes are registered for this base type
     */
    private fun extractOpenSubtypes(descriptor: SerialDescriptor): List<SerialDescriptor> {
        val baseSerialName = descriptor.serialName
        val subtypeDescriptors = mutableListOf<SerialDescriptor>()
        val baseClassSerialNames = mutableMapOf<KClass<*>, String>()

        json.serializersModule.dumpTo(
            object : SerializersModuleCollector {
                override fun <T : Any> contextual(
                    kClass: KClass<T>,
                    provider: (typeArgumentsSerializers: List<KSerializer<*>>) -> KSerializer<*>,
                ) = Unit

                override fun <Base : Any, Sub : Base> polymorphic(
                    baseClass: KClass<Base>,
                    actualClass: KClass<Sub>,
                    actualSerializer: KSerializer<Sub>,
                ) {
                    val cachedName =
                        baseClassSerialNames.getOrPut(baseClass) {
                            kotlinx.serialization
                                .PolymorphicSerializer(baseClass)
                                .descriptor.serialName
                        }
                    if (cachedName == baseSerialName) {
                        subtypeDescriptors.add(actualSerializer.descriptor)
                    }
                }

                override fun <Base : Any> polymorphicDefaultSerializer(
                    baseClass: KClass<Base>,
                    defaultSerializerProvider: (value: Base) -> kotlinx.serialization.SerializationStrategy<Base>?,
                ) = Unit

                override fun <Base : Any> polymorphicDefaultDeserializer(
                    baseClass: KClass<Base>,
                    defaultDeserializerProvider: (
                        className: String?,
                    ) -> kotlinx.serialization.DeserializationStrategy<Base>?,
                ) = Unit
            },
        )

        check(subtypeDescriptors.isNotEmpty()) {
            "No subtypes registered in SerializersModule for open polymorphic type '$baseSerialName'. " +
                "Register subtypes via polymorphic(Base::class) { subclass(Sub::class) } in the module."
        }

        return subtypeDescriptors
    }

    /**
     * Handles unknown types by creating an empty [ObjectNode].
     */
    private fun handleUnknownType(
        descriptor: SerialDescriptor,
        nullable: Boolean,
    ): TypeRef {
        val id = descriptorId(descriptor)

        withCycleDetection(descriptor, id) {
            ObjectNode(
                name = descriptor.unwrapSerialName().removeSuffix("?"),
                properties = emptyList(),
                required = emptySet(),
                description = extractDescription(descriptor),
            )
        }

        val ref = TypeRef.Ref(id, nullable)
        if (!nullable) typeRefCache[descriptor] = ref
        return ref
    }

    /**
     * Creates a [TypeId] from a [SerialDescriptor] using its serialName.
     *
     * For open polymorphic descriptors, unwraps the `kotlinx.serialization.Polymorphic<Name>`
     * wrapper to extract the inner type name.
     */
    private fun descriptorId(descriptor: SerialDescriptor): TypeId =
        TypeId(descriptor.unwrapSerialName().removeSuffix("?"))

    /**
     * Returns a cached [AnyNode] ref if [serialName] is a known opaque type, null otherwise.
     *
     * Checks [ANY_SERIAL_NAMES] (`kotlin.Any`, `java.lang.Object`) unconditionally, then
     * [SerializationClassSchemaIntrospector.Config.opaqueSerialNames] for caller-configured types.
     * These two sets are additive: setting [SerializationClassSchemaIntrospector.Config.opaqueSerialNames]
     * to an empty set suppresses only the configurable opaque types — it does not disable
     * the built-in `kotlin.Any`/`java.lang.Object` handling.
     */
    private fun opaqueRefOrNull(serialName: String, nullable: Boolean): TypeRef? =
        if (serialName in ANY_SERIAL_NAMES || serialName in config.opaqueSerialNames) {
            if (nullable) ANY_REF_NULLABLE else ANY_REF
        } else {
            null
        }

    private companion object {
        /** Serial names that represent "any value" — mapped to [AnyNode] (empty schema `{}`). */
        val ANY_SERIAL_NAMES: Set<String> = setOf("kotlin.Any", "java.lang.Object")

        /** Cached [TypeRef] for [AnyNode] to avoid per-call allocation. */
        val ANY_REF: TypeRef = TypeRef.Inline(AnyNode(), false)
        val ANY_REF_NULLABLE: TypeRef = TypeRef.Inline(AnyNode(), true)
    }

    /**
     * Checks whether the descriptor's class-level annotations include a recognized ignore marker.
     *
     * Note: Only recognizes [SerialSchemaIgnore] directly. Custom ignore annotations
     * registered via `kt-schema.properties` are not checked here because
     * `Annotation::class.simpleName` is unreliable in Kotlin common code.
     */
    private fun SerialDescriptor.isSchemaIgnored(): Boolean = annotations.any { it is SerialSchemaIgnore }

    /**
     * Extracts description from a list of type annotations.
     */
    private fun extractDescription(descriptor: SerialDescriptor): String? =
        config.descriptionExtractor.extract(descriptor.annotations)

    /**
     * Extracts description from a list of element annotations.
     */
    private fun extractElementDescription(
        descriptor: SerialDescriptor,
        index: Int,
    ): String? = config.descriptionExtractor.extract(descriptor.getElementAnnotations(index))

    private fun SerialDescriptor.polymorphicDiscriminatorName(): String =
        annotations
            .filterIsInstance<JsonClassDiscriminator>()
            .firstOrNull()
            ?.discriminator ?: json.configuration.classDiscriminator
}
