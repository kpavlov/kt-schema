package me.kpavlov.kt.schema.ksp.ir

import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.isPublic
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Modifier
import com.google.devtools.ksp.symbol.Nullability
import me.kpavlov.kt.schema.generator.core.InternalSchemaGeneratorApi
import me.kpavlov.kt.schema.generator.core.defaultOpaqueTypeNames
import me.kpavlov.kt.schema.generator.core.defaultPrimitiveTypeKinds
import me.kpavlov.kt.schema.generator.core.ir.AnyNode
import me.kpavlov.kt.schema.generator.core.ir.BaseIntrospectionContext
import me.kpavlov.kt.schema.generator.core.ir.EnumNode
import me.kpavlov.kt.schema.generator.core.ir.ListNode
import me.kpavlov.kt.schema.generator.core.ir.MapNode
import me.kpavlov.kt.schema.generator.core.ir.ObjectNode
import me.kpavlov.kt.schema.generator.core.ir.PrimitiveKind
import me.kpavlov.kt.schema.generator.core.ir.PrimitiveNode
import me.kpavlov.kt.schema.generator.core.ir.Property
import me.kpavlov.kt.schema.generator.core.ir.TypeRef
import me.kpavlov.kt.schema.generator.core.ir.withNullable

/**
 * Shared introspection context for KSP-based introspectors.
 *
 * Eliminates toRef() duplication between KspClassIntrospector and KspFunctionIntrospector
 * by providing a single, well-tested implementation of the type resolution strategy.
 *
 * Extends [BaseIntrospectionContext] to inherit state management and cycle detection,
 * while implementing KSP-specific type resolution logic.
 *
 * Resolution strategy (applied in order):
 * 1. Basic types (primitives and collections) via [resolveBasicTypeOrNull]
 * 2. JSON collection types ([kotlinx.serialization.json.JsonObject]/[kotlinx.serialization.json.JsonArray]) → inline [MapNode]/[ListNode]
 * 3. Third-party types with a well-defined JSON primitive shape (e.g. Jackson's `StringNode`, `IntNode`) → [PrimitiveNode] via [resolvePrimitiveTypeKindOrNull]
 * 4. Opaque JSON types (kotlinx.serialization.json and the rest of the Jackson databind node
 *    hierarchy) → [AnyNode] → empty schema `{}`
 * 5. Generic type parameters and unknowns -> kotlin.Any via [handleAnyFallback]
 * 6. Inline value classes -> flattened to their wrapped element's type via [resolveInlineValueClassOrNull]
 * 7. Sealed class hierarchies -> PolymorphicNode via [handleSealedClass]
 * 8. Enum classes -> EnumNode via [handleEnum]
 * 9. Regular objects/classes -> ObjectNode via [handleObjectOrClass]
 */
@OptIn(InternalSchemaGeneratorApi::class)
@Suppress("TooManyFunctions")
internal class KspIntrospectionContext : BaseIntrospectionContext<KSType>() {
    /**
     * Converts a KSType to a TypeRef using the standard resolution strategy.
     *
     * This method implements the common type resolution pattern used across all KSP
     * introspectors. It tries each handler in priority order, using elvis operator
     * chain to return the first successful match.
     *
     * All types should be handled by one of the resolution steps. If not, an exception
     * is thrown to fail fast and help identify missing handler cases during development.
     *
     * @param type The KSType to convert
     * @return TypeRef representing the type in the schema IR
     * @throws IllegalArgumentException if the type cannot be handled by any handler
     */
    override fun toRef(type: KSType): TypeRef {
        val nullable = type.effectiveNullable()

        // Try each handler in order, using elvis operator chain for single return
        return requireNotNull(
            resolveBasicTypeOrNull(type)
                ?: resolveJsonCollectionTypeOrNull(type)
                ?: resolvePrimitiveTypeKindOrNull(type)
                ?: resolveOpaqueTypeOrNull(type)
                ?: handleAnyFallback(type)
                ?: resolveInlineValueClassOrNull(type, nullable)
                ?: handleSealedClass(type, nullable)
                ?: handleEnum(type, nullable)
                ?: handleObjectOrClass(type, nullable),
        ) {
            "Unexpected type that couldn't be handled: ${type.declaration.qualifiedName}"
        }
    }

    /**
     * Whether this type should be treated as nullable — either natively (Kotlin `?`) or by
     * convention (its declaration's simple name matches a configured nullable-type-name glob
     * pattern, e.g. `*Opt`).
     */
    private fun KSType.effectiveNullable(): Boolean = nullability == Nullability.NULLABLE || isNullableByTypeName()

    /**
     * Attempts to resolve basic types (primitives and collections) to TypeRef.
     *
     * This is the shared prefix logic used by both KspClassIntrospector and KspFunctionIntrospector
     * for handling primitive types and collections before diverging to handle complex types.
     *
     * Returns null if the type requires complex handling (classes, enums, sealed, etc.).
     *
     * @param type The KSType to resolve
     * @return TypeRef if this is a primitive or collection type, null otherwise
     */
    private fun resolveBasicTypeOrNull(type: KSType): TypeRef? {
        val nullable = type.effectiveNullable()

        // Try primitive types first, then collections, using elvis operator chain.
        // KspTypeMappers.collectionTypeRefOrNull computes its own nullable flag from native KSP
        // nullability only, so re-apply the outer `nullable` (which also folds in the
        // type-name-pattern convention) on top of whatever it returns.
        return KspTypeMappers.primitiveFor(type)?.let { TypeRef.Inline(it, nullable) }
            ?: KspTypeMappers.collectionTypeRefOrNull(type, ::toRef)?.let { ref ->
                if (nullable) ref.withNullable(true) else ref
            }
    }

    /**
     * Maps the built-in `kotlinx.serialization.json` collection-like types
     * ([kotlinx.serialization.json.JsonObject], [kotlinx.serialization.json.JsonArray]) to their proper inline
     * schema representations.
     * - [kotlinx.serialization.json.JsonObject] implements
     *    [Map] → [MapNode] → `{ "type": "object", "additionalProperties": {} }`
     * - [kotlinx.serialization.json.JsonArray] implements [List] → [ListNode] → `{ "type": "array" }`
     *
     * The element/value types are [AnyNode] (matching the opaque handling of [kotlinx.serialization.json.JsonElement]),
     * so they produce no `$ref`/`$defs` and remain inline.
     *
     * KSP cannot reliably resolve the supertype type-arguments of external library classes,
     * so we construct the IR nodes directly rather than walking supertypes.
     */
    private fun resolveJsonCollectionTypeOrNull(type: KSType): TypeRef? {
        val nullable = type.effectiveNullable()
        val qn = type.declaration.qualifiedName?.asString() ?: return null
        return when (qn) {
            "kotlinx.serialization.json.JsonObject" -> {
                TypeRef.Inline(
                    MapNode(
                        key = TypeRef.Inline(PrimitiveNode(PrimitiveKind.STRING)),
                        value = TypeRef.Inline(AnyNode()),
                    ),
                    nullable,
                )
            }

            "kotlinx.serialization.json.JsonArray" -> {
                TypeRef.Inline(
                    ListNode(element = TypeRef.Inline(AnyNode())),
                    nullable,
                )
            }

            else -> {
                null
            }
        }
    }

    /**
     * Checks whether [type] is a known opaque type (e.g., [kotlinx.serialization.json] types or the
     * Jackson databind node hierarchy, all with incompatible class structures) and maps it to
     * [AnyNode] → empty schema `{}`.
     */
    private fun resolveOpaqueTypeOrNull(type: KSType): TypeRef? {
        val nullable = type.effectiveNullable()
        val qualifiedName = type.declaration.qualifiedName?.asString() ?: return null
        return if (qualifiedName in OPAQUE_TYPE_NAMES) {
            TypeRef.Inline(AnyNode(), nullable)
        } else {
            null
        }
    }

    /**
     * Checks whether [type] is a known third-party type with a single well-defined JSON
     * primitive shape (e.g. Jackson's `StringNode`, `IntNode`) and maps it to the matching
     * [PrimitiveNode] — unlike the types with no fixed shape handled by [resolveOpaqueTypeOrNull].
     */
    private fun resolvePrimitiveTypeKindOrNull(type: KSType): TypeRef? {
        val nullable = type.effectiveNullable()
        val qualifiedName = type.declaration.qualifiedName?.asString() ?: return null
        return PRIMITIVE_TYPE_KINDS[qualifiedName]?.let { TypeRef.Inline(PrimitiveNode(it), nullable) }
    }

    /**
     * Handles generic type parameters or unknown declarations by falling back to kotlin.Any.
     *
     * This handler is invoked when the type declaration is not a KSClassDeclaration or lacks
     * a qualified name (e.g., generic type parameters like `T` in `fun <T> foo(param: T)`).
     *
     * @param type The KSType to check
     * @return [TypeRef.Inline] wrapping [AnyNode] if fallback is needed, null otherwise
     */
    private fun handleAnyFallback(type: KSType): TypeRef? {
        val nullable = type.effectiveNullable()
        val declAnyFallback = type.declaration !is KSClassDeclaration || type.declaration.qualifiedName == null
        if (!declAnyFallback) return null

        return TypeRef.Inline(AnyNode(), nullable)
    }

    /**
     * Handles inline value classes (`@JvmInline value class Wrapper(val inner: T)`, surfaced by
     * KSP as [Modifier.VALUE]) by delegating to the wrapped element's type.
     *
     * Inline value classes serialize as their inner value (e.g. `14.5` instead of
     * `{"value": 14.5}`), so the schema must reflect the inner type.
     *
     * If the value class has a class-level `@Description` (or KDoc), it is propagated to the
     * flattened primitive node so it still appears in the generated schema.
     *
     * Returns null (falling through to [handleObjectOrClass]) when [type] isn't a value class,
     * its wrapped type can't be determined, or it (transitively) wraps itself — flattening that
     * would recurse forever.
     *
     * @param type The KSType to check
     * @param nullable Whether the type reference should be nullable
     * @return The flattened TypeRef, or null if this isn't a flattenable inline value class
     */
    @Suppress("ReturnCount")
    private fun resolveInlineValueClassOrNull(
        type: KSType,
        nullable: Boolean,
    ): TypeRef? {
        val decl = type.declaration as? KSClassDeclaration ?: return null
        if (Modifier.VALUE !in decl.modifiers) return null
        val wrappedParam = decl.primaryConstructor?.parameters?.singleOrNull() ?: return null
        if (type in visitingTypes) return null

        visitingTypes += type
        val wrappedRef =
            try {
                toRef(wrappedParam.type.resolve())
            } finally {
                visitingTypes -= type
            }

        val classDescription = extractDescription(decl) { decl.descriptionFromKdoc() }
        val resultRef =
            if (classDescription != null && wrappedRef is TypeRef.Inline && wrappedRef.node is PrimitiveNode) {
                TypeRef.Inline(
                    (wrappedRef.node as PrimitiveNode).copy(description = classDescription),
                    wrappedRef.nullable,
                )
            } else {
                wrappedRef
            }

        return if (nullable && !resultRef.nullable) resultRef.withNullable(true) else resultRef
    }

    /**
     * Handles sealed class hierarchies by generating a PolymorphicNode.
     *
     * Creates a polymorphic schema with discriminator-based subtype resolution. Each sealed
     * subclass is recursively processed and registered in the type graph. The discriminator
     * maps simple class names to their fully qualified TypeIds.
     *
     * @param type The KSType to check
     * @param nullable Whether the type reference should be nullable
     * @return TypeRef.Ref to the polymorphic node if this is a sealed class, null otherwise
     */
    private fun handleSealedClass(
        type: KSType,
        nullable: Boolean,
    ): TypeRef? {
        val decl = type.sealedClassDeclOrNull() ?: return null
        val id = decl.typeId()

        withCycleDetection(type, id) {
            // Find all sealed subclasses, excluding those annotated with @SchemaIgnore
            val sealedSubclasses =
                decl
                    .getSealedSubclasses()
                    .filter { !it.isSchemaIgnored() }
                    .toList()

            // Create SubtypeRef for each sealed subclass using their typeId()
            val subtypes =
                sealedSubclasses.map {
                    me.kpavlov.kt.schema.generator.core.ir
                        .SubtypeRef(it.typeId())
                }

            // Build discriminator mapping: discriminator value (fully qualified name) -> TypeId
            // Keys must match the `const` values emitted for each subtype's discriminator property.
            val discriminatorMapping =
                sealedSubclasses.associate { it.typeId().value to it.typeId() }

            // Process each sealed subclass
            sealedSubclasses.forEach { toRef(it.asType(emptyList())) }

            val sealedNameOverride = extractNameOverride(decl)
            me.kpavlov.kt.schema.generator.core.ir.PolymorphicNode(
                name = sealedNameOverride ?: decl.qualifiedName?.asString() ?: decl.simpleName.asString(),
                subtypes = subtypes,
                discriminator =
                    me.kpavlov.kt.schema.generator.core.ir.Discriminator(
                        // TODO allow to configure discriminator property name
                        name = "type",
                        mapping = discriminatorMapping,
                    ),
                description = extractDescription(decl) { decl.descriptionFromKdoc() },
            )
        }

        return TypeRef.Ref(id, nullable)
    }

    /**
     * Handles enum classes by generating an EnumNode.
     *
     * Extracts all enum entries and creates a schema node that constrains values to the
     * declared enum constants. Enum entries are identified by ClassKind.ENUM_ENTRY.
     *
     * @param type The KSType to check
     * @param nullable Whether the type reference should be nullable
     * @return TypeRef.Ref to the enum node if this is an enum class, null otherwise
     */
    private fun handleEnum(
        type: KSType,
        nullable: Boolean,
    ): TypeRef? {
        val decl = type.enumClassDeclOrNull() ?: return null
        val id = decl.typeId()

        return namedRef(type, id, nullable) {
            val constants =
                decl.declarations
                    .filterIsInstance<KSClassDeclaration>()
                    .filter { it.classKind == com.google.devtools.ksp.symbol.ClassKind.ENUM_ENTRY }
                    .toList()
            var defaultValue: String? = null
            val entries =
                constants.map { entry ->
                    val entryName = extractNameOverride(entry) ?: entry.simpleName.asString()
                    if (defaultValue == null && entry.isEnumDefaultAnnotated()) defaultValue = entryName
                    entryName
                }

            val nameOverride = extractNameOverride(decl)
            EnumNode(
                name = nameOverride ?: decl.qualifiedName?.asString() ?: decl.simpleName.asString(),
                entries = entries,
                defaultValue = defaultValue,
                description = extractDescription(decl) { decl.descriptionFromKdoc() },
            )
        }
    }

    /**
     * Handles regular objects and data classes by generating an ObjectNode.
     *
     * Prefers primary constructor parameters for data classes (extracting parameter names,
     * types, and default value presence). Falls back to public properties for objects and
     * classes without primary constructors. Properties without defaults are marked as required.
     *
     * Note: KSP does not provide access to default value expressions at compile-time
     * (https://github.com/google/ksp/issues/1868), so only the presence of defaults is tracked.
     *
     * @param type The KSType to check
     * @param nullable Whether the type reference should be nullable
     * @return TypeRef.Ref to the object node if this is a class/object, null otherwise
     */
    @Suppress("ReturnCount")
    private fun handleObjectOrClass(
        type: KSType,
        nullable: Boolean,
    ): TypeRef? {
        val decl = type.declaration as? KSClassDeclaration ?: return null

        // kotlin.Any / java.lang.Object: any value — emit empty schema {}
        val qualifiedName = decl.qualifiedName?.asString()
        if (qualifiedName == "kotlin.Any" || qualifiedName == "java.lang.Object") {
            return TypeRef.Inline(AnyNode(), nullable)
        }

        val id = decl.typeId()

        withCycleDetection(type, id) {
            val props = ArrayList<Property>()
            val required = LinkedHashSet<String>()

            // Original (pre-rename) Kotlin declaration names already handled directly by this
            // class — NOT the emitted/renamed names, so a sealed-parent property satisfied via a
            // renamed constructor override isn't mistaken for unprocessed and re-added below.
            val processedKotlinNames = HashSet<String>()

            /**
             * Helper to add a property and track whether it's required.
             *
             * Properties without default values are automatically added to the required set.
             */
            fun addProperty(
                kotlinName: String,
                name: String,
                type: TypeRef,
                description: String?,
                hasDefaultValue: Boolean,
                defaultValue: String? = null,
                isConstant: Boolean = false,
            ) {
                if (!hasDefaultValue || isConstant) required += name
                props += createProperty(name, type, description, hasDefaultValue, defaultValue, isConstant)
                processedKotlinNames += kotlinName
            }

            extractConstructorOrProperties(decl, ::addProperty)
            extractInheritedSealedProperties(decl, processedKotlinNames, ::addProperty)

            val nameOverride = extractNameOverride(decl)
            ObjectNode(
                name = nameOverride ?: decl.qualifiedName?.asString() ?: decl.simpleName.asString(),
                properties = props,
                required = required,
                description = extractDescription(decl) { decl.descriptionFromKdoc() },
            )
        }

        return TypeRef.Ref(id, nullable)
    }

    /**
     * Resolves a property's [TypeRef] and effective "has default value" flag, folding in the
     * nullable/optional convention (type-name pattern or `@Nullable`-style annotation) on top of
     * [nativeHasDefault] (a Kotlin default-value expression, or `true` for an inherited
     * sealed-parent property with a fixed value).
     *
     * @param annotationSources annotated declarations to check for the convention annotation
     *   (e.g. both a constructor parameter and its corresponding property)
     */
    private fun resolvePropertyTypeAndOptionality(
        resolvedType: KSType,
        nativeHasDefault: Boolean,
        vararg annotationSources: KSAnnotated?,
    ): Triple<TypeRef, Boolean, String?> {
        val nullableAnnotated = annotationSources.any { it?.isNullableAnnotated() == true }
        val optionalAnnotated = annotationSources.any { it?.isOptionalAnnotated() == true }
        val defaultValue = annotationSources.firstNotNullOfOrNull { it?.let(::extractDefaultValueOverride) }
        val typeRef = toRef(resolvedType).let { if (nullableAnnotated) it.withNullable(true) else it }
        val hasDefault =
            nativeHasDefault || resolvedType.isOptionalByTypeName() || optionalAnnotated || defaultValue != null
        return Triple(typeRef, hasDefault, defaultValue)
    }

    private fun extractConstructorOrProperties(
        decl: KSClassDeclaration,
        addProperty: (String, String, TypeRef, String?, Boolean, String?) -> Unit,
    ) {
        val declaredProperties = decl.getDeclaredProperties().associateBy { it.simpleName.asString() }
        // Prefer primary constructor parameters for data classes; fall back to public properties
        val params = decl.primaryConstructor?.parameters.orEmpty()
        if (params.isNotEmpty()) {
            params.forEach { p ->
                val kotlinName = p.name?.asString() ?: return@forEach
                val property = declaredProperties[kotlinName]
                // Skip properties marked with an ignore annotation (e.g. @JsonIgnore)
                if (p.isSchemaIgnored() || property?.isIgnoredForSchema() == true) return@forEach
                val propertyName =
                    extractNameOverride(p) ?: property?.let { extractNameOverride(it) } ?: kotlinName
                val description = extractConstructorParamDescription(p, kotlinName, decl.docString, property)
                val (typeRef, hasDefault, defaultValue) =
                    resolvePropertyTypeAndOptionality(
                        p.type.resolve(),
                        p.hasDefault,
                        p,
                        property,
                        property?.getter,
                    )
                addProperty(kotlinName, propertyName, typeRef, description, hasDefault, defaultValue)
            }
        } else {
            declaredProperties.values
                .filter { it.isPublic() && !it.isIgnoredForSchema() }
                .forEach { prop ->
                    val kotlinName = prop.simpleName.asString()
                    val propertyName = extractNameOverride(prop) ?: kotlinName
                    val description =
                        extractPropertyDescription(
                            annotated = prop,
                            propertyName = kotlinName,
                            parentKdoc = decl.docString,
                            kdocTagName = "property",
                            elementKdocFallback = { prop.descriptionFromKdoc() },
                        )
                    val (typeRef, hasDefault, defaultValue) =
                        resolvePropertyTypeAndOptionality(
                            prop.type.resolve(),
                            nativeHasDefault = false,
                            prop,
                            prop.getter,
                        )
                    addProperty(kotlinName, propertyName, typeRef, description, hasDefault, defaultValue)
                }
        }
    }

    private fun extractInheritedSealedProperties(
        decl: KSClassDeclaration,
        processedKotlinNames: Set<String>,
        addProperty: (String, String, TypeRef, String?, Boolean, String?, Boolean) -> Unit,
    ) {
        // Add inherited properties from sealed parents that weren't in the constructor
        val sealedParents =
            decl.superTypes
                .mapNotNull { it.resolve().declaration as? KSClassDeclaration }
                .filter { it.modifiers.contains(Modifier.SEALED) }
                .toList()

        // The child's own declared properties, so an override that re-declares the annotation
        // (e.g. `@get:JsonProperty` placed on the subclass's `override val`) takes precedence
        // over the parent's — mirroring how the reflection-based introspector resolves overrides.
        val childDeclaredProperties = decl.getDeclaredProperties().associateBy { it.simpleName.asString() }

        sealedParents.forEach { parent ->
            parent.getDeclaredProperties().filter { it.isPublic() && !it.isIgnoredForSchema() }.forEach { parentProp ->
                val kotlinName = parentProp.simpleName.asString()
                if (kotlinName in processedKotlinNames) return@forEach

                val overridingProp = childDeclaredProperties[kotlinName]
                val effectiveProp = overridingProp ?: parentProp

                // Prefer the child override's own name override (covers a re-declared
                // `@get:JsonProperty`), falling back to the parent's — mirroring how the
                // reflection-based introspector resolves overrides.
                val name =
                    overridingProp?.let { extractNameOverride(it) }
                        ?: extractNameOverride(parentProp)
                        ?: kotlinName
                val description =
                    extractPropertyDescription(
                        annotated = effectiveProp,
                        propertyName = kotlinName,
                        parentKdoc = parent.docString,
                        kdocTagName = "property",
                        elementKdocFallback = { effectiveProp.descriptionFromKdoc() },
                    )
                val (typeRef, _, _) =
                    resolvePropertyTypeAndOptionality(
                        effectiveProp.type.resolve(),
                        nativeHasDefault = true,
                        parentProp,
                        parentProp.getter,
                        overridingProp,
                        overridingProp?.getter,
                    )
                addProperty(
                    kotlinName,
                    name,
                    typeRef,
                    description,
                    true, // Fixed value in the subclass
                    null, // KSP cannot get the value
                    false, // isConstant: not marked const since the value can't be extracted
                )
            }
        }
    }

    private companion object {
        /**
         * Fully-qualified class names of types that represent arbitrary JSON values
         * and should be treated as opaque (mapped to [AnyNode] → empty schema `{}`).
         */
        val OPAQUE_TYPE_NAMES: Set<String> = defaultOpaqueTypeNames()

        /**
         * Fully qualified third-party type names mapped to the [PrimitiveKind] they represent
         * (e.g. Jackson's `StringNode` -> `STRING`).
         */
        val PRIMITIVE_TYPE_KINDS: Map<String, PrimitiveKind> = defaultPrimitiveTypeKinds()
    }
}
