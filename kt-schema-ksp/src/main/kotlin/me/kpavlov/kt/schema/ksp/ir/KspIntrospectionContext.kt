package me.kpavlov.kt.schema.ksp.ir

import com.google.devtools.ksp.getAllSuperTypes
import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.isPublic
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeAlias
import com.google.devtools.ksp.symbol.KSTypeParameter
import com.google.devtools.ksp.symbol.KSValueParameter
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
 * Resolves [KSType]s to [TypeRef]s for the KSP introspectors, on top of the state and cycle detection of
 * [BaseIntrospectionContext]. Typealiases are resolved through the aliased type first, see [toAliasedRef].
 *
 * Resolution order:
 * 1. Primitives and collections ([resolveBasicTypeOrNull])
 * 2. [kotlinx.serialization.json.JsonObject]/[kotlinx.serialization.json.JsonArray] -> inline [MapNode]/[ListNode]
 * 3. Third-party types with a JSON primitive shape, e.g. Jackson's `StringNode` ([resolvePrimitiveTypeKindOrNull])
 * 4. Opaque JSON types (kotlinx.serialization.json, Jackson databind nodes) -> [AnyNode], i.e. `{}`
 * 5. Type parameters and unknown declarations -> [AnyNode] ([handleAnyFallback])
 * 6. Inline value classes -> their wrapped type ([resolveInlineValueClassOrNull])
 * 7. `java.util`/`kotlin.collections` Iterable/Map classes -> [ListNode]/[MapNode] ([resolvePlatformCollectionOrNull])
 * 8. Sealed classes -> PolymorphicNode ([handleSealedClass])
 * 9. Enums -> EnumNode ([handleEnum])
 * 10. Other classes and objects -> ObjectNode ([handleObjectOrClass])
 */
@OptIn(InternalSchemaGeneratorApi::class)
@Suppress("TooManyFunctions")
internal class KspIntrospectionContext : BaseIntrospectionContext<KSType>() {
    /** Use-site arguments of the typealiases being resolved, by alias type parameter. */
    private val aliasBindings = mutableMapOf<KSTypeParameter, KSType>()

    /**
     * Converts [type] to a [TypeRef], trying the handlers listed on the class in order.
     *
     * @throws IllegalArgumentException if no handler accepts [type]
     */
    override fun toRef(type: KSType): TypeRef =
        when (val declaration = type.declaration) {
            is KSTypeAlias -> toAliasedRef(type, declaration)
            is KSTypeParameter -> toBoundRef(type, declaration) ?: toResolvedRef(type)
            else -> toResolvedRef(type)
        }

    /**
     * Resolves a typealias through the type it stands for. KSP reports the alias as [KSType.declaration] and
     * [KSType.arguments] are the alias's own, so its type parameters are bound in [aliasBindings] while the
     * aliased type is resolved (see [toBoundRef]).
     */
    private fun toAliasedRef(
        type: KSType,
        alias: KSTypeAlias,
    ): TypeRef {
        val outerBindings = aliasBindings.toMap()
        alias.typeParameters.zip(type.arguments).forEach { (parameter, argument) ->
            val resolved = argument.type?.resolve()
            if (resolved != null) aliasBindings[parameter] = resolved else aliasBindings -= parameter
        }
        try {
            return toRef(alias.type.resolve()).withNullableIf(type.isNullableAtUseSite())
        } finally {
            aliasBindings.clear()
            aliasBindings += outerBindings
        }
    }

    /** Substitutes a bound typealias type parameter with its use-site argument. */
    private fun toBoundRef(
        type: KSType,
        parameter: KSTypeParameter,
    ): TypeRef? = aliasBindings[parameter]?.let { toRef(it).withNullableIf(type.isNullableAtUseSite()) }

    /**
     * Explicitly nullable (`T?`) or nullable by type-name convention. Unlike [effectiveNullable], ignores
     * [KSType.nullability], which is `NULLABLE` for a bare type parameter with a nullable upper bound.
     */
    private fun KSType.isNullableAtUseSite(): Boolean = isMarkedNullable || isNullableByTypeName()

    private fun TypeRef.withNullableIf(condition: Boolean): TypeRef =
        if (condition && !nullable) withNullable(true) else this

    private fun toResolvedRef(type: KSType): TypeRef {
        val nullable = type.effectiveNullable()

        return requireNotNull(
            resolveBasicTypeOrNull(type)
                ?: resolveJsonCollectionTypeOrNull(type)
                ?: resolvePrimitiveTypeKindOrNull(type)
                ?: resolveOpaqueTypeOrNull(type)
                ?: handleAnyFallback(type)
                ?: resolveInlineValueClassOrNull(type, nullable)
                ?: resolvePlatformCollectionOrNull(type, nullable)
                ?: handleSealedClass(type, nullable)
                ?: handleEnum(type, nullable)
                ?: handleObjectOrClass(type, nullable),
        ) {
            "Unexpected type that couldn't be handled: ${type.declaration.qualifiedName}"
        }
    }

    /** Nullable natively (`?`) or by a configured nullable-type-name pattern (e.g. `*Opt`). */
    private fun KSType.effectiveNullable(): Boolean = nullability == Nullability.NULLABLE || isNullableByTypeName()

    /** Resolves primitives and collections; null for anything that needs complex handling. */
    private fun resolveBasicTypeOrNull(type: KSType): TypeRef? {
        val nullable = type.effectiveNullable()

        // collectionTypeRefOrNull sees only native nullability; re-apply `nullable` (incl. name convention).
        return KspTypeMappers.primitiveFor(type)?.let { TypeRef.Inline(it, nullable) }
            ?: KspTypeMappers.collectionTypeRefOrNull(type, ::toRef)?.withNullableIf(nullable)
    }

    /** See [KspTypeMappers.platformCollectionTypeRefOrNull]. */
    private fun resolvePlatformCollectionOrNull(
        type: KSType,
        nullable: Boolean,
    ): TypeRef? = KspTypeMappers.platformCollectionTypeRefOrNull(type, ::toRef)?.withNullableIf(nullable)

    /**
     * Maps [kotlinx.serialization.json.JsonObject]/[kotlinx.serialization.json.JsonArray] to inline
     * [MapNode]/[ListNode] of [AnyNode]. Built directly, as KSP can't reliably resolve the supertype arguments
     * of library classes.
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

    /** Maps known opaque types (kotlinx.serialization.json, Jackson databind nodes) to [AnyNode]. */
    private fun resolveOpaqueTypeOrNull(type: KSType): TypeRef? {
        val nullable = type.effectiveNullable()
        val qualifiedName = type.declaration.qualifiedName?.asString() ?: return null
        return if (qualifiedName in OPAQUE_TYPE_NAMES) {
            TypeRef.Inline(AnyNode(), nullable)
        } else {
            null
        }
    }

    /** Maps third-party types with a fixed JSON primitive shape (e.g. Jackson's `StringNode`) to [PrimitiveNode]. */
    private fun resolvePrimitiveTypeKindOrNull(type: KSType): TypeRef? {
        val nullable = type.effectiveNullable()
        val qualifiedName = type.declaration.qualifiedName?.asString() ?: return null
        return PRIMITIVE_TYPE_KINDS[qualifiedName]?.let { TypeRef.Inline(PrimitiveNode(it), nullable) }
    }

    /** Falls back to [AnyNode] for type parameters (`T`) and declarations that aren't named classes. */
    private fun handleAnyFallback(type: KSType): TypeRef? {
        val nullable = type.effectiveNullable()
        val declAnyFallback = type.declaration !is KSClassDeclaration || type.declaration.qualifiedName == null
        if (!declAnyFallback) return null

        return TypeRef.Inline(AnyNode(), nullable)
    }

    /**
     * Flattens an inline value class ([Modifier.VALUE]) to its wrapped type, since it serializes as the inner value.
     * Type parameters of the wrapped type are substituted with the use-site arguments (`Wrapper<Int>` is an integer)
     * and the class description is carried over to the flattened node. Recursion is handled by [flattenValueClass].
     *
     * @return null if [type] isn't a value class or its wrapped type can't be determined
     */
    @Suppress("ReturnCount")
    private fun resolveInlineValueClassOrNull(
        type: KSType,
        nullable: Boolean,
    ): TypeRef? {
        val decl = type.declaration as? KSClassDeclaration ?: return null
        if (Modifier.VALUE !in decl.modifiers) return null
        val wrappedParam = decl.primaryConstructor?.parameters?.singleOrNull() ?: return null

        val wrappedType = resolveWrappedTypeAsMemberOf(decl, wrappedParam, type)
        val wrappedRef =
            flattenValueClass(
                key = type.makeNotNullable(),
                id = decl.typeId(),
                nullable = nullable,
                wrappedNullable = wrappedType.isMarkedNullable,
                description = extractDescription(decl) { decl.descriptionFromKdoc() },
            ) { toRef(wrappedType) }

        return wrappedRef.withNullableIf(nullable)
    }

    /**
     * The wrapped parameter's type as a member of [type]
     * ([com.google.devtools.ksp.symbol.KSPropertyDeclaration.asMemberOf]), so type parameters become the use-site
     * arguments. Falls back to the declared type if that isn't possible.
     */
    private fun resolveWrappedTypeAsMemberOf(
        decl: KSClassDeclaration,
        wrappedParam: KSValueParameter,
        type: KSType,
    ): KSType {
        val declaredType = wrappedParam.type.resolve()
        if (decl.typeParameters.isEmpty()) return declaredType

        val wrappedProperty =
            decl.getDeclaredProperties().firstOrNull { it.simpleName.asString() == wrappedParam.name?.asString() }
        return wrappedProperty?.asMemberOf(type.makeNotNullable())?.takeUnless { it.isError } ?: declaredType
    }

    /** Builds a PolymorphicNode over the sealed subclasses not marked as ignored, registering each of them. */
    private fun handleSealedClass(
        type: KSType,
        nullable: Boolean,
    ): TypeRef? {
        val decl = type.sealedClassDeclOrNull() ?: return null
        val id = decl.typeId()

        withCycleDetection(type, id) {
            val sealedSubclasses =
                decl
                    .getSealedSubclasses()
                    .filter { !it.isSchemaIgnored() }
                    .toList()

            val subtypes =
                sealedSubclasses.map {
                    me.kpavlov.kt.schema.generator.core.ir
                        .SubtypeRef(it.typeId())
                }

            // Keys must match the `const` values emitted for each subtype's discriminator property.
            val discriminatorMapping =
                sealedSubclasses.associate { it.typeId().value to it.typeId() }

            sealedSubclasses.forEach { toRef(it.asStarProjectedType()) }

            val sealedNameOverride = extractNameOverride(decl)
            me.kpavlov.kt.schema.generator.core.ir.PolymorphicNode(
                name = sealedNameOverride ?: decl.qualifiedName?.asString() ?: decl.simpleName.asString(),
                subtypes = subtypes,
                discriminator =
                    me.kpavlov.kt.schema.generator.core.ir.Discriminator(
                        name = decl.discriminatorPropertyName(),
                        mapping = discriminatorMapping,
                    ),
                description = extractDescription(decl) { decl.descriptionFromKdoc() },
            )
        }

        return TypeRef.Ref(id, nullable)
    }

    /** Resolves the discriminator name declared on this class or, for nested sealed types, on a supertype. */
    private fun KSClassDeclaration.discriminatorPropertyName(): String =
        discriminatorName(sequenceOf<KSDeclaration>(this) + getAllSuperTypes().map { it.declaration }) { declaration ->
            declaration.annotations.firstNotNullOfOrNull(KSAnnotation::discriminatorNameOrNull)
        }

    /** Builds an EnumNode from the enum entries, honoring name overrides and the default-value marker. */
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
     * Builds an ObjectNode from the primary constructor parameters, or from the public properties when there is
     * none. Properties without defaults are required. KSP can't expose default value expressions
     * (https://github.com/google/ksp/issues/1868), so only their presence is tracked.
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

            // Kotlin declaration names (not the emitted ones), so a sealed-parent property already covered
            // by a renamed constructor override isn't re-added.
            val processedKotlinNames = HashSet<String>()

            /** Adds a property; it's required unless it has a default value (or is constant). */
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
     * Resolves a property's [TypeRef], whether it has a default value and the annotation-provided default value.
     * The type-name and `@Nullable`/`@Optional`-style conventions apply on top of [nativeHasDefault].
     *
     * @param annotationSources declarations whose annotations are checked (e.g. a parameter and its property)
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
        val params = decl.primaryConstructor?.parameters.orEmpty()
        if (params.isNotEmpty()) {
            params.forEach { p ->
                val kotlinName = p.name?.asString() ?: return@forEach
                val property = declaredProperties[kotlinName]
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
        val sealedParents =
            decl.superTypes
                .mapNotNull { it.resolve().declaration as? KSClassDeclaration }
                .filter { it.modifiers.contains(Modifier.SEALED) }
                .toList()

        // A child override that re-declares an annotation (e.g. `@get:JsonProperty`) wins over the parent's,
        // as in the reflection front end.
        val childDeclaredProperties = decl.getDeclaredProperties().associateBy { it.simpleName.asString() }

        sealedParents.forEach { parent ->
            parent.getDeclaredProperties().filter { it.isPublic() && !it.isIgnoredForSchema() }.forEach { parentProp ->
                val kotlinName = parentProp.simpleName.asString()
                if (kotlinName in processedKotlinNames) return@forEach

                val overridingProp = childDeclaredProperties[kotlinName]
                val effectiveProp = overridingProp ?: parentProp

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
        /** Types representing arbitrary JSON values, mapped to [AnyNode]. */
        val OPAQUE_TYPE_NAMES: Set<String> = defaultOpaqueTypeNames()

        /** Third-party type names mapped to the [PrimitiveKind] they represent. */
        val PRIMITIVE_TYPE_KINDS: Map<String, PrimitiveKind> = defaultPrimitiveTypeKinds()
    }
}
