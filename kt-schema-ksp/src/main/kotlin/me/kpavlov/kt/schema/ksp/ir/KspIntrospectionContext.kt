package me.kpavlov.kt.schema.ksp.ir

import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.isPublic
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Modifier
import com.google.devtools.ksp.symbol.Nullability
import me.kpavlov.kt.schema.generator.core.defaultOpaqueTypeNames
import me.kpavlov.kt.schema.generator.core.ir.AnyNode
import me.kpavlov.kt.schema.generator.core.ir.BaseIntrospectionContext
import me.kpavlov.kt.schema.generator.core.ir.ListNode
import me.kpavlov.kt.schema.generator.core.ir.MapNode
import me.kpavlov.kt.schema.generator.core.ir.ObjectNode
import me.kpavlov.kt.schema.generator.core.ir.PrimitiveKind
import me.kpavlov.kt.schema.generator.core.ir.PrimitiveNode
import me.kpavlov.kt.schema.generator.core.ir.Property
import me.kpavlov.kt.schema.generator.core.ir.TypeRef

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
 * 2. JSON collection types ([JsonObject]/[JsonArray]) → inline [MapNode]/[ListNode]
 * 3. Opaque JSON types ([JsonElement]/[JsonPrimitive]/[JsonNull]) → [AnyNode] → empty schema `{}`
 * 4. Generic type parameters and unknowns -> kotlin.Any via [handleAnyFallback]
 * 5. Sealed class hierarchies -> PolymorphicNode via [handleSealedClass]
 * 6. Enum classes -> EnumNode via [handleEnum]
 * 7. Regular objects/classes -> ObjectNode via [handleObjectOrClass]
 */
@Suppress("TooManyFunctions")
internal class KspIntrospectionContext : BaseIntrospectionContext<KSType>() {
    /**
     * Converts a KSType into schema IR using the standard resolution order.
     *
     * @param type The type to convert.
     * @return The resolved type reference.
     * @throws IllegalArgumentException if the type does not match any supported handler.
     */
    override fun toRef(type: KSType): TypeRef {
        val nullable = type.nullability == Nullability.NULLABLE

        // Try each handler in order, using elvis operator chain for single return
        return requireNotNull(
            resolveBasicTypeOrNull(type)
                ?: resolveJsonCollectionTypeOrNull(type)
                ?: resolveOpaqueTypeOrNull(type)
                ?: handleAnyFallback(type)
                ?: handleSealedClass(type, nullable)
                ?: handleEnum(type, nullable)
                ?: handleObjectOrClass(type, nullable),
        ) {
            "Unexpected type that couldn't be handled: ${type.declaration.qualifiedName}"
        }
    }

    /**
     * Resolves primitive and supported collection types to a type reference.
     *
     * @param type Type to resolve.
     * @return An inline primitive or collection type reference, or `null` if the type requires structured handling.
     */
    private fun resolveBasicTypeOrNull(type: KSType): TypeRef? {
        val nullable = type.nullability == Nullability.NULLABLE

        // Try primitive types first, then collections, using elvis operator chain
        return KspTypeMappers.primitiveFor(type)?.let { TypeRef.Inline(it, nullable) }
            ?: KspTypeMappers.collectionTypeRefOrNull(type, ::toRef)
    }

    /**
     * Resolves Kotlinx Serialization JSON collection types to inline schema nodes.
     *
     * @return An inline `MapNode` for `kotlinx.serialization.json.JsonObject`, an inline `ListNode` for `kotlinx.serialization.json.JsonArray`, or `null` when the type is not handled.
     */
    private fun resolveJsonCollectionTypeOrNull(type: KSType): TypeRef? {
        val nullable = type.nullability == Nullability.NULLABLE
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
     * Maps a known opaque type to an inline `AnyNode`.
     *
     * @param type The type to inspect.
     * @return An inline `AnyNode` when the type is in the opaque type allowlist, `null` otherwise.
     */
    private fun resolveOpaqueTypeOrNull(type: KSType): TypeRef? {
        val nullable = type.nullability == Nullability.NULLABLE
        val qualifiedName = type.declaration.qualifiedName?.asString() ?: return null
        return if (qualifiedName in OPAQUE_TYPE_NAMES) {
            TypeRef.Inline(AnyNode(), nullable)
        } else {
            null
        }
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
        val nullable = type.nullability == Nullability.NULLABLE
        val declAnyFallback = type.declaration !is KSClassDeclaration || type.declaration.qualifiedName == null
        if (!declAnyFallback) return null

        return TypeRef.Inline(AnyNode(), nullable)
    }

    /**
     * Builds a polymorphic type reference for a sealed class hierarchy.
     *
     * @param type Type to resolve.
     * @param nullable Whether the resulting reference should be nullable.
     * @return A reference to the sealed class node, or null if the type is not sealed.
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
                baseName = sealedNameOverride ?: decl.simpleName.asString(),
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

        withCycleDetection(type, id) {
            val entries =
                decl.declarations
                    .filterIsInstance<KSClassDeclaration>()
                    .filter { it.classKind == com.google.devtools.ksp.symbol.ClassKind.ENUM_ENTRY }
                    .map { entry -> extractNameOverride(entry) ?: entry.simpleName.asString() }
                    .toList()

            val nameOverride = extractNameOverride(decl)
            me.kpavlov.kt.schema.generator.core.ir.EnumNode(
                name = nameOverride ?: decl.qualifiedName?.asString() ?: decl.simpleName.asString(),
                entries = entries,
                description = extractDescription(decl) { decl.descriptionFromKdoc() },
            )
        }

        return TypeRef.Ref(id, nullable)
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

            val processedProperties = HashSet<String>()

            /**
             * Helper to add a property and track whether it's required.
             *
             * Properties without default values are automatically added to the required set.
             */
            fun addProperty(
                name: String,
                type: KSType,
                description: String?,
                hasDefaultValue: Boolean,
                isConstant: Boolean = false,
            ) {
                if (!hasDefaultValue || isConstant) required += name
                props += createProperty(name, toRef(type), description, hasDefaultValue, isConstant)
                processedProperties += name
            }

            extractConstructorOrProperties(decl, ::addProperty)
            extractInheritedSealedProperties(decl, processedProperties, ::addProperty)

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

    private fun extractConstructorOrProperties(
        decl: KSClassDeclaration,
        addProperty: (String, KSType, String?, Boolean) -> Unit,
    ) {
        // Prefer primary constructor parameters for data classes; fall back to public properties
        val params = decl.primaryConstructor?.parameters.orEmpty()
        if (params.isNotEmpty()) {
            params.forEach { p ->
                val kotlinName = p.name?.asString() ?: return@forEach
                val propertyName = extractNameOverride(p) ?: kotlinName
                val description = extractConstructorParamDescription(p, kotlinName, decl.docString)
                addProperty(propertyName, p.type.resolve(), description, p.hasDefault)
            }
        } else {
            decl.getDeclaredProperties().filter { it.isPublic() }.forEach { prop ->
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
                addProperty(propertyName, prop.type.resolve(), description, false)
            }
        }
    }

    /**
     * Adds public properties declared in sealed parent classes that are not already processed.
     *
     * @param decl The class declaration being inspected.
     * @param processedProperties Property names that have already been added.
     * @param addProperty Callback used to record each inherited property.
     */
    private fun extractInheritedSealedProperties(
        decl: KSClassDeclaration,
        processedProperties: Set<String>,
        addProperty: (String, KSType, String?, Boolean, Boolean) -> Unit,
    ) {
        // Add inherited properties from sealed parents that weren't in the constructor
        val sealedParents =
            decl.superTypes
                .mapNotNull { it.resolve().declaration as? KSClassDeclaration }
                .filter { it.modifiers.contains(Modifier.SEALED) }
                .toList()

        sealedParents.forEach { parent ->
            parent.getDeclaredProperties().filter { it.isPublic() }.forEach { prop ->
                val name = prop.simpleName.asString()
                if (name !in processedProperties) {
                    val description =
                        extractPropertyDescription(
                            annotated = prop,
                            propertyName = name,
                            parentKdoc = parent.docString,
                            kdocTagName = "property",
                            elementKdocFallback = { prop.descriptionFromKdoc() },
                        )
                    addProperty(
                        name,
                        prop.type.resolve(),
                        description,
                        true, // Fixed value in the subclass
                        false, // KSP cannot get the value
                    )
                }
            }
        }
    }

    private companion object {
        /**
         * Fully-qualified class names of types that represent arbitrary JSON values
         * and should be treated as opaque (mapped to [AnyNode] → empty schema `{}`).
         */
        val OPAQUE_TYPE_NAMES: Set<String> = defaultOpaqueTypeNames()
    }
}
