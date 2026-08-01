package me.kpavlov.kt.schema.generator.reflect

import me.kpavlov.kt.schema.generator.core.Config
import me.kpavlov.kt.schema.generator.core.InternalSchemaGeneratorApi
import me.kpavlov.kt.schema.generator.core.defaultPrimitiveTypeKinds
import me.kpavlov.kt.schema.generator.core.ir.AnyNode
import me.kpavlov.kt.schema.generator.core.ir.BaseIntrospectionContext
import me.kpavlov.kt.schema.generator.core.ir.Discriminator
import me.kpavlov.kt.schema.generator.core.ir.EnumNode
import me.kpavlov.kt.schema.generator.core.ir.Introspections
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
import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import kotlin.reflect.KType
import kotlin.reflect.KVisibility
import kotlin.reflect.full.createType

/**
 * Reflection-based introspection context based on [KType].
 * Only supports [KClass] classifiers for introspection, generics are not supported.
 */
@OptIn(InternalSchemaGeneratorApi::class)
@Suppress("TooManyFunctions")
internal class ReflectionIntrospectionContext : BaseIntrospectionContext<KType>() {
    /**
     * This is a shared instance, so different schema generation runs would reuse the same class metadata cache.
     */
    private val defaultValueExtractor = DefaultValueExtractor

    /**
     * Converts a [KType] to a [TypeRef].
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
    override fun toRef(type: KType): TypeRef {
        val klass = type.klass
        val nullable = type.effectiveNullable()

        // Check cache first
        typeRefCache[type]?.let { cachedRef ->
            return if (nullable && !cachedRef.nullable) {
                cachedRef.withNullable(true)
            } else {
                cachedRef
            }
        }

        // kotlin.Any / java.lang.Object: any value — emit empty schema {}
        if (klass == Any::class) {
            return TypeRef.Inline(AnyNode(), nullable)
        }

        // Opaque types (kotlinx.serialization.json and Jackson databind node hierarchy):
        // treated as any JSON value — emit empty schema {}
        if (klass.qualifiedName in Config.opaqueTypeNames) {
            return TypeRef.Inline(AnyNode(), nullable)
        }

        // Try to convert to primitive type — either a Kotlin built-in or a known third-party
        // type with a single well-defined JSON primitive shape (e.g. Jackson's StringNode).
        primitiveKindFor(klass)?.let { primitiveKind ->
            val ref = TypeRef.Inline(PrimitiveNode(primitiveKind), nullable)
            if (!nullable) typeRefCache[type] = ref
            return ref
        }

        // Handle different kinds
        return when {
            isListLike(klass) -> handleListType(type)
            isMapLike(klass) -> handleMapType(type)
            isEnumClass(klass) -> handleEnumType(type)
            klass.isSealed -> handleSealedType(type)
            else -> handleObjectType(type)
        }
    }

    //region KClass type matchers

    /**
     * Whether [this] type should be treated as nullable — either natively (Kotlin `?`) or by
     * convention (its resolved class's simple name matches a configured nullable-type-name
     * glob pattern, e.g. `*Opt`).
     */
    private fun KType.effectiveNullable(): Boolean =
        isMarkedNullable || Introspections.isNullableTypeName(klass.simpleName)

    /**
     * Checks and maps a class to its corresponding [PrimitiveKind] — either a Kotlin built-in
     * primitive, or a known third-party type with a single well-defined JSON primitive shape
     * (currently Jackson's databind leaf/numeric-abstraction node types, e.g. StringNode, IntNode).
     * Returns null if the class is not a supported primitive type.
     */
    private fun primitiveKindFor(klass: KClass<*>): PrimitiveKind? =
        when (klass) {
            String::class -> PrimitiveKind.STRING
            Boolean::class -> PrimitiveKind.BOOLEAN
            Byte::class, Short::class, Int::class -> PrimitiveKind.INT
            Long::class -> PrimitiveKind.LONG
            Float::class -> PrimitiveKind.FLOAT
            Double::class -> PrimitiveKind.DOUBLE
            Char::class -> PrimitiveKind.STRING
            else -> PRIMITIVE_TYPE_KINDS[klass.qualifiedName]
        }

    /**
     * Checks if a class is list-like (List, Collection, or Iterable).
     */
    private fun isListLike(klass: KClass<*>): Boolean = Iterable::class.java.isAssignableFrom(klass.java)

    /**
     * Checks if a class is a map-like type (Map).
     */
    private fun isMapLike(klass: KClass<*>): Boolean = Map::class.java.isAssignableFrom(klass.java)

    /**
     * Checks if a class is an enum class.
     */
    private fun isEnumClass(klass: KClass<*>): Boolean = !klass.isData && klass.java.isEnum

    /**
     * Extracts a type argument from a supertype of [klass].
     * Searches through direct supertypes using [superType]'s `isAssignableFrom` to match
     * both the exact type and its subtypes (e.g., [Iterable] matches [List]/[Collection]).
     * Returns null if no matching supertype is found or the argument index is out of bounds.
     */
    private fun superTypeArg(klass: KClass<*>, superType: KClass<*>, argumentIndex: Int): KType? {
        val found = klass.supertypes.firstOrNull {
            val classifier = it.classifier as? KClass<*> ?: return@firstOrNull false
            superType.java.isAssignableFrom(classifier.java)
        } ?: return null
        return found.arguments.getOrNull(argumentIndex)?.type
    }

    //endregion

    //region KType to TypeRef conversion handlers

    /**
     * Handles list-like types (List, Collection, Iterable).
     * Falls back to supertype type arguments when the direct type arguments are unavailable
     * (e.g., for classes like [kotlinx.serialization.json.JsonArray] that implement
     * [List] with concrete type arguments).
     */
    private fun handleListType(type: KType): TypeRef {
        // Only fall back to supertype arguments for non-generic wrappers (e.g. JsonArray).
        // When the type already declares arguments, honor them so star projections like
        // List<*> resolve to a null element instead of leaking a raw type parameter.
        val elementType =
            if (type.arguments.isEmpty()) {
                superTypeArg(type.klass, Iterable::class, 0)
            } else {
                type.arguments.firstOrNull()?.type
            }

        val elementRef =
            elementType
                ?.let { toRef(it) }
                ?: TypeRef.Inline(PrimitiveNode(PrimitiveKind.STRING), false)

        val ref = TypeRef.Inline(ListNode(elementRef), type.effectiveNullable())
        if (!type.effectiveNullable()) typeRefCache[type] = ref
        return ref
    }

    /**
     * Handles Map types.
     * Falls back to supertype type arguments when the direct type arguments are unavailable
     * (e.g., for classes like [kotlinx.serialization.json.JsonObject] that implement
     * [Map] with concrete type arguments).
     */
    private fun handleMapType(type: KType): TypeRef {
        // Only fall back to supertype arguments for non-generic wrappers (e.g. JsonObject).
        // When the type already declares arguments, honor them so star projections like
        // Map<*, *> resolve to null key/value instead of leaking raw type parameters.
        val hasArguments = type.arguments.isNotEmpty()
        val keyType =
            if (hasArguments) type.arguments.getOrNull(0)?.type else superTypeArg(type.klass, Map::class, 0)
        val valueType =
            if (hasArguments) type.arguments.getOrNull(1)?.type else superTypeArg(type.klass, Map::class, 1)

        val keyRef =
            keyType
                ?.let { toRef(it) }
                ?: TypeRef.Inline(PrimitiveNode(PrimitiveKind.STRING), false)

        val valueRef =
            valueType
                ?.let { toRef(it) }
                ?: TypeRef.Inline(PrimitiveNode(PrimitiveKind.STRING), false)

        val ref = TypeRef.Inline(MapNode(keyRef, valueRef), type.effectiveNullable())
        if (!type.effectiveNullable()) typeRefCache[type] = ref
        return ref
    }

    /**
     * Handles enum types by creating an EnumNode and adding it to discovered nodes.
     */
    private fun handleEnumType(type: KType): TypeRef {
        val id = createTypeId(type.klass)

        withCycleDetection(type, id) {
            createEnumNode(type.klass)
        }

        val ref = TypeRef.Ref(id, type.effectiveNullable())
        if (!type.effectiveNullable()) typeRefCache[type] = ref
        return ref
    }

    /**
     * Handles object/class types by creating an ObjectNode.
     */
    private fun handleObjectType(type: KType): TypeRef {
        val klass = type.klass
        val id = createTypeId(klass)

        withCycleDetection(type, id) {
            createObjectNode(klass)
        }

        val ref = TypeRef.Ref(id, type.effectiveNullable())
        if (!type.effectiveNullable()) typeRefCache[type] = ref
        return ref
    }

    /**
     * Handles sealed types by creating a PolymorphicNode and processing each sealed subclass.
     */
    private fun handleSealedType(type: KType): TypeRef {
        val klass = type.klass
        val id = createTypeId(klass)

        withCycleDetection(type, id) {
            val filteredSubclasses = klass.filteredSealedSubclasses()
            val polymorphicNode = createPolymorphicNode(klass, filteredSubclasses)

            filteredSubclasses.forEach { subclass ->
                toRef(subclass.createType())
            }

            polymorphicNode
        }

        val ref = TypeRef.Ref(id, type.effectiveNullable())
        if (!type.effectiveNullable()) typeRefCache[type] = ref
        return ref
    }

    //endregion

    //region Create methods

    /**
     * Creates a [TypeId] from a [KClass], using `@SerialName` override if present,
     * or qualified name / simple name as fallback.
     */
    private fun createTypeId(klass: KClass<*>): TypeId {
        val nameOverride = extractNameOverride(klass.java.annotations.toList())
        return TypeId(nameOverride ?: klass.qualifiedName ?: klass.simpleName ?: "Anonymous")
    }

    /**
     * Creates an [EnumNode] from an enum [KClass].
     *
     * Respects `@SerialName` on the enum class (overrides the class name)
     * and on individual enum entries (overrides the entry name in the schema).
     */
    private fun createEnumNode(klass: KClass<*>): EnumNode {
        @Suppress("UNCHECKED_CAST")
        val enumConstants = (klass.java as Class<out Enum<*>>).enumConstants
        val entries = enumConstants.map { constant ->
            val field = klass.java.getField(constant.name)
            extractNameOverride(field.annotations.toList()) ?: constant.name
        }
        val nameOverride = extractNameOverride(klass.java.annotations.toList())
        return EnumNode(
            name = nameOverride ?: klass.simpleName ?: "UnknownEnum",
            entries = entries,
            description = extractDescription(klass.java.annotations.toList()),
        )
    }

    /**
     * Creates an [ObjectNode] from a [KClass].
     */
    @Suppress("LongMethod", "CyclomaticComplexMethod")
    private fun createObjectNode(klass: KClass<*>): ObjectNode {
        val properties = mutableListOf<Property>()
        val requiredProperties = mutableSetOf<String>()

        // Find sealed parent classes to inherit property descriptions
        val sealedParents =
            klass.supertypes
                .mapNotNull { it.classifier as? KClass<*> }
                .filter { it.isSealed }

        // Build a map of parent property descriptions, name overrides and properties
        val parentPropertyDescriptions = mutableMapOf<String, String>()
        val parentPropertyNameOverrides = mutableMapOf<String, String>()
        val parentProperties = mutableSetOf<String>()
        sealedParents.forEach { parent ->
            parent.members
                .filterIsInstance<KProperty<*>>()
                .forEach { prop ->
                    parentProperties.add(prop.name)
                    // Use the full annotation set (incl. `@get:`-targeted) so idiomatic Jackson
                    // placements like `@get:JsonProperty` on the parent are recognized too.
                    val parentAnnotations = collectPropertyAnnotations(prop)
                    extractDescription(parentAnnotations)?.let { parentPropertyDescriptions[prop.name] = it }
                    extractNameOverride(parentAnnotations)?.let { parentPropertyNameOverrides[prop.name] = it }
                }
        }

        // Try to extract default values by creating an instance
        val defaultValues = defaultValueExtractor.extractDefaultValues(klass)

        // Extract properties from primary constructor using shared method
        val (constructorProperties, constructorRequired) = extractConstructorProperties(klass, defaultValues)

        // Track which properties were processed from constructor
        val processedProperties = constructorProperties.map { it.name }.toMutableSet()

        // Original (pre-rename) constructor parameter names, used to detect sealed-parent
        // properties already satisfied via a renamed constructor override (see below) —
        // `processedProperties` above holds the emitted/renamed names, not the declaration names.
        val constructorParameterNames =
            findPrimaryConstructor(klass)?.parameters?.mapNotNull { it.name }?.toSet().orEmpty()

        // If there are sealed parents, update descriptions to inherit from parent if needed
        if (sealedParents.isNotEmpty()) {
            constructorProperties.forEach { prop ->
                val updatedProp =
                    if (prop.description == null && parentPropertyDescriptions.containsKey(prop.name)) {
                        prop.copy(description = parentPropertyDescriptions[prop.name])
                    } else {
                        prop
                    }
                properties += updatedProp
            }
        } else {
            properties += constructorProperties
        }

        requiredProperties += constructorRequired

        // Add inherited properties from sealed parents that weren't in the constructor
        val inheritedPropertyNames = parentProperties - constructorParameterNames
        inheritedPropertyNames.forEach { propertyName ->
            // Find the property in the current class (inherited)
            val property = findPropertyByName(klass, propertyName) ?: return@forEach
            val extra =
                buildExtraProperty(
                    property = property,
                    defaultValues = defaultValues,
                    fallbackDescription = parentPropertyDescriptions[propertyName],
                    fallbackNameOverride = parentPropertyNameOverrides[propertyName],
                ) ?: return@forEach

            properties += extra
            // Inherited properties with fixed values are required; a property that's also
            // optional by convention (type-name pattern or `@Nullable`-style annotation) and
            // has no fixed value is excluded, the same way a Kotlin default value is handled.
            if (!extra.hasDefaultValue || extra.isConstant) requiredProperties += extra.name
            processedProperties += propertyName
            processedProperties += extra.name
        }

        // Add public properties for objects (singletons) that weren't in the constructor or from parents
        if (klass.objectInstance != null) {
            klass.members
                .filterIsInstance<KProperty<*>>()
                .filter { it.visibility == KVisibility.PUBLIC }
                .forEach { prop ->
                    if (prop.name in processedProperties) return@forEach
                    val extra = buildExtraProperty(prop, defaultValues) ?: return@forEach

                    properties += extra
                    if (!extra.hasDefaultValue || extra.isConstant) requiredProperties += extra.name
                    processedProperties += prop.name
                    processedProperties += extra.name
                }
        }

        val nameOverride = extractNameOverride(klass.java.annotations.toList())
        return ObjectNode(
            name = nameOverride ?: klass.simpleName ?: "UnknownClass",
            properties = properties,
            required = requiredProperties,
            description = extractDescription(klass.java.annotations.toList()),
        )
    }

    /**
     * Builds a [Property] for a property discovered outside the primary constructor — either
     * inherited from a sealed parent or declared on a singleton object. Shared by both call
     * sites in [createObjectNode] so ignore/name-override/description resolution stays in sync.
     *
     * @param fallbackDescription description to use when [property]'s own annotations have none
     *   (e.g. inherited from the sealed parent's declaration)
     * @param fallbackNameOverride name override to use when [property]'s own annotations have
     *   none (e.g. declared only on the sealed parent's declaration)
     * @return the built [Property], or null if [property] is annotated as ignored
     */
    private fun buildExtraProperty(
        property: KProperty<*>,
        defaultValues: Map<String, Any?>,
        fallbackDescription: String? = null,
        fallbackNameOverride: String? = null,
    ): Property? {
        val annotations = collectPropertyAnnotations(property)
        if (isSchemaIgnored(annotations)) return null

        val typeRef =
            toRef(property.returnType).let {
                if (isNullableAnnotated(annotations)) it.withNullable(true) else it
            }
        val fixedValue = defaultValues[property.name]
        val hasDefaultValue =
            fixedValue != null ||
                isOptionalTypeName(property.returnType.klass) ||
                isOptionalAnnotated(annotations)
        return Property(
            name = extractNameOverride(annotations) ?: fallbackNameOverride ?: property.name,
            type = typeRef,
            description = extractDescription(annotations) ?: fallbackDescription,
            hasDefaultValue = hasDefaultValue,
            defaultValue = fixedValue,
            isConstant = fixedValue != null,
        )
    }

    /**
     * Creates a [PolymorphicNode] from a [KClass] using the given [sealedSubclasses]
     * (already filtered to exclude `@SchemaIgnore`-annotated subtypes).
     */
    private fun createPolymorphicNode(
        klass: KClass<*>,
        sealedSubclasses: List<KClass<*>>,
    ): PolymorphicNode {
        val nameOverride = extractNameOverride(klass.java.annotations.toList())
        val baseName = nameOverride ?: klass.simpleName ?: "UnknownSealed"

        val subtypes =
            sealedSubclasses.map { subclass ->
                SubtypeRef(createTypeId(subclass))
            }

        // Build discriminator mapping: discriminator value -> TypeId
        // Key must equal the TypeId value so it matches the `const` value the transformer emits
        val discriminatorMapping =
            sealedSubclasses.associate { subclass ->
                val id = createTypeId(subclass)
                id.value to id
            }

        return PolymorphicNode(
            baseName = baseName,
            subtypes = subtypes,
            discriminator =
                Discriminator(
                    // TODO allow to configure discriminator property name
                    name = "type",
                    mapping = discriminatorMapping,
                ),
            description = extractDescription(klass.java.annotations.toList()),
        )
    }

    /**
     * Returns sealed subclasses excluding those annotated with a recognized ignore annotation.
     */
    private fun KClass<*>.filteredSealedSubclasses(): List<KClass<*>> =
        sealedSubclasses.filter { !isSchemaIgnored(it.annotations) }

    //endregion

    /**
     * Extracts properties from the primary constructor of a class.
     *
     * This method processes constructor parameters to create Property objects,
     * handling type conversion, default values, descriptions, and nullability.
     *
     * @param klass The class whose constructor to analyze
     * @param defaultValues Map of property names to their default values (from DefaultValueExtractor)
     * @return Pair of (list of properties, set of required property names)
     */
    private fun extractConstructorProperties(
        klass: KClass<*>,
        defaultValues: Map<String, Any?>,
    ): Pair<List<Property>, Set<String>> {
        val properties = mutableListOf<Property>()
        val requiredProperties = mutableSetOf<String>()

        val constructor = findPrimaryConstructor(klass)

        constructor?.parameters?.forEach { param ->
            val kotlinName = param.name ?: return@forEach

            // Collect annotations from the parameter, property, getter, and backing field
            val annotations = collectConstructorAnnotations(klass, kotlinName, param.annotations)

            // Skip properties marked with an ignore annotation (e.g. @JsonIgnore)
            if (isSchemaIgnored(annotations)) return@forEach

            // Name override (e.g. @SerialName, @JsonProperty), else Kotlin property name
            val propertyName = extractNameOverride(annotations) ?: kotlinName

            val propertyType = param.type
            val typeRef =
                toRef(propertyType).let {
                    if (isNullableAnnotated(annotations)) it.withNullable(true) else it
                }

            // A property is optional (excluded from `required`) when it has a Kotlin default
            // value, or when it's marked nullable/optional by convention (type-name pattern or
            // `@Nullable`-style annotation) — the latter mainly matters for front ends without
            // native default-value support, but applies uniformly here for consistency.
            val hasDefault =
                param.isOptional ||
                    isOptionalTypeName(propertyType.klass) ||
                    isOptionalAnnotated(annotations)

            // Get the actual default value if available
            val defaultValue = if (param.isOptional) defaultValues[kotlinName] else null

            properties +=
                Property(
                    name = propertyName,
                    type = typeRef,
                    description = extractDescription(annotations),
                    hasDefaultValue = hasDefault,
                    defaultValue = defaultValue,
                )

            if (!hasDefault) {
                requiredProperties += propertyName
            }
        }

        return properties to requiredProperties
    }

    private companion object {
        /**
         * Fully qualified third-party type names mapped to the [PrimitiveKind] they represent
         * (e.g. Jackson's `StringNode` -> `STRING`).
         */
        val PRIMITIVE_TYPE_KINDS: Map<String, PrimitiveKind> = defaultPrimitiveTypeKinds()
    }
}
