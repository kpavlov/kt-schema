// Copyright (c) 2026 Konstantin Pavlov and contributors
package me.kpavlov.kt.schema.apt.ir

import me.kpavlov.kt.schema.generator.core.InternalSchemaGeneratorApi
import me.kpavlov.kt.schema.generator.core.ir.AnyNode
import me.kpavlov.kt.schema.generator.core.ir.BaseIntrospectionContext
import me.kpavlov.kt.schema.generator.core.ir.Introspections
import me.kpavlov.kt.schema.generator.core.ir.ListNode
import me.kpavlov.kt.schema.generator.core.ir.MapNode
import me.kpavlov.kt.schema.generator.core.ir.ObjectNode
import me.kpavlov.kt.schema.generator.core.ir.PrimitiveKind
import me.kpavlov.kt.schema.generator.core.ir.PrimitiveNode
import me.kpavlov.kt.schema.generator.core.ir.Property
import me.kpavlov.kt.schema.generator.core.ir.TypeId
import me.kpavlov.kt.schema.generator.core.ir.TypeNode
import me.kpavlov.kt.schema.generator.core.ir.TypeRef
import me.kpavlov.kt.schema.generator.core.ir.withNullable
import javax.lang.model.element.Element
import javax.lang.model.element.ElementKind
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.Modifier
import javax.lang.model.element.RecordComponentElement
import javax.lang.model.element.TypeElement
import javax.lang.model.element.VariableElement
import javax.lang.model.type.ArrayType
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.TypeKind
import javax.lang.model.type.TypeMirror
import javax.lang.model.type.TypeVariable
import javax.lang.model.type.WildcardType
import javax.lang.model.util.Types

/**
 * Introspection context for the Java annotation-processor (JSR 269) front end.
 *
 * Supports Java records, plain classes and interfaces with primitive/boxed/String fields,
 * nested references, collections (`List`/`Set`/`Collection`/`Iterable`), maps (`Map`),
 * arrays, `Object` and upper-bounded type variables. Java has no notion of nullability or
 * optionality/default values, so every property is non-nullable/required by default — except
 * where marked nullable/optional by convention (a type-name glob pattern, e.g. `*Opt`, or a
 * `@Nullable`-style annotation; see [Introspections.isNullableTypeName]/[Introspections.isNullableAnnotation]
 * and their optional-marker counterparts).
 *
 * A fresh context is created per root type so `$defs` stay scoped to the types reachable
 * from that root; [nodeCache] memoizes built nodes across roots so nested types shared
 * between roots are introspected once. On a cache hit the node's `$ref` targets are
 * re-registered into the current context to keep `$defs` complete.
 *
 * @author Konstantin Pavlov
 */
@OptIn(InternalSchemaGeneratorApi::class)
@Suppress("TooManyFunctions")
internal class AptIntrospectionContext(
    private val types: Types,
    private val nodeCache: MutableMap<TypeId, CachedNode>,
) : BaseIntrospectionContext<TypeMirror>() {
    //region Type conversion

    override fun toRef(type: TypeMirror): TypeRef {
        val nullable = isNullableByTypeName(type)
        val ref =
            primitiveKindFor(type)?.let { TypeRef.Inline(PrimitiveNode(it)) }
                ?: when (type.kind) {
                    TypeKind.ARRAY -> handleArray(type)
                    TypeKind.TYPEVAR -> handleTypeVariable(type)
                    TypeKind.DECLARED -> handleDeclared(type as DeclaredType) ?: handleReferenceType(type)
                    else -> handleReferenceType(type)
                }
        return if (nullable) ref.withNullable(true) else ref
    }

    /**
     * Handles `Object`, map and iterable-derived declared types; returns null for anything
     * else so [toRef] falls through to [handleReferenceType].
     */
    private fun handleDeclared(type: DeclaredType): TypeRef? {
        val element = asTypeElement(type) ?: return null
        val container = containerKindOf(type)
        return when {
            element.qualifiedName.toString() == "java.lang.Object" -> TypeRef.Inline(AnyNode())
            container?.kind == ContainerKind.MAP -> handleMap(container.type)
            container?.kind == ContainerKind.ITERABLE -> handleList(container.type)
            else -> null
        }
    }

    /**
     * Resolves a type variable to its upper bound, so `T extends Foo` introspects as `Foo`
     * and an unbounded `T` (whose bound is `Object`) introspects as [AnyNode].
     */
    private fun handleTypeVariable(type: TypeMirror): TypeRef {
        val upperBound = (type as TypeVariable).upperBound
        return when (upperBound.kind) {
            TypeKind.DECLARED -> toRef(upperBound)
            else -> error(
                "Unsupported type parameter $type for kt-schema-apt " +
                    "(only upper-bounded type variables are supported)",
            )
        }
    }

    private fun handleReferenceType(type: TypeMirror): TypeRef =
        handleRecord(type)
            ?: handleClass(type)
            ?: handleInterface(type)
            ?: error(
                "Unsupported type for kt-schema-apt " +
                    "(only records, classes, interfaces, primitives, String, collections, maps and arrays " +
                    "are supported): $type",
            )

    private fun primitiveKindFor(type: TypeMirror): PrimitiveKind? =
        when (type.kind) {
            TypeKind.BOOLEAN -> PrimitiveKind.BOOLEAN
            TypeKind.INT, TypeKind.SHORT, TypeKind.BYTE -> PrimitiveKind.INT
            TypeKind.LONG -> PrimitiveKind.LONG
            TypeKind.FLOAT -> PrimitiveKind.FLOAT
            TypeKind.DOUBLE -> PrimitiveKind.DOUBLE
            TypeKind.CHAR -> PrimitiveKind.STRING
            TypeKind.DECLARED -> boxedPrimitiveKindFor(type)
            else -> null
        }

    private fun boxedPrimitiveKindFor(type: TypeMirror): PrimitiveKind? =
        when (asTypeElement(type)?.qualifiedName?.toString()) {
            "java.lang.String" -> PrimitiveKind.STRING
            "java.lang.Boolean" -> PrimitiveKind.BOOLEAN
            "java.lang.Integer", "java.lang.Short", "java.lang.Byte" -> PrimitiveKind.INT
            "java.lang.Long" -> PrimitiveKind.LONG
            "java.lang.Float" -> PrimitiveKind.FLOAT
            "java.lang.Double" -> PrimitiveKind.DOUBLE
            "java.lang.Character" -> PrimitiveKind.STRING
            else -> null
        }

    private fun asTypeElement(type: TypeMirror): TypeElement? = types.asElement(type) as? TypeElement

    /**
     * Converts an array type to an inline [ListNode] of its component type, so `int[]`
     * and `String[][]` map to `{"type": "array", "items": ...}` in JSON Schema.
     */
    private fun handleArray(type: TypeMirror): TypeRef =
        TypeRef.Inline(ListNode(toRef((type as ArrayType).componentType)))

    /**
     * Converts an `Iterable`-derived declared type to an inline [ListNode]. [type] is the
     * supertype that declares the interface (resolved by [containerKindOf]), so type
     * arguments are read from it rather than the original subtype. Unbounded wildcards and
     * raw types fall back to a STRING element, mirroring the reflection introspector's
     * star-projection handling.
     */
    private fun handleList(type: DeclaredType): TypeRef {
        val elementRef =
            type.typeArguments.firstOrNull()?.resolveWildcard()?.let(::toRef)
                ?: TypeRef.Inline(PrimitiveNode(PrimitiveKind.STRING))
        return TypeRef.Inline(ListNode(elementRef))
    }

    /**
     * Converts a `Map`-derived declared type to an inline [MapNode]. [type] is the supertype
     * that declares the interface (resolved by [containerKindOf]), so type arguments are read
     * from it rather than the original subtype. Unbounded wildcards and raw types fall back
     * to a STRING key/value, mirroring the reflection introspector's star-projection handling.
     */
    private fun handleMap(type: DeclaredType): TypeRef {
        val keyRef =
            type.typeArguments.getOrNull(0)?.resolveWildcard()?.let(::toRef)
                ?: TypeRef.Inline(PrimitiveNode(PrimitiveKind.STRING))
        val valueRef =
            type.typeArguments.getOrNull(1)?.resolveWildcard()?.let(::toRef)
                ?: TypeRef.Inline(PrimitiveNode(PrimitiveKind.STRING))
        return TypeRef.Inline(MapNode(keyRef, valueRef))
    }

    /**
     * The container kind of a `Map`/`Iterable`-derived declared type, or null otherwise.
     */
    private enum class ContainerKind { MAP, ITERABLE }

    /**
     * A container kind paired with the declared type that declares the matching
     * `java.util.Map`/`java.lang.Iterable` interface, so type arguments can be read from
     * the resolved supertype rather than the original subtype (e.g. `ArrayList<String>` for
     * a raw `MyList extends ArrayList<String>` field).
     */
    private data class ContainerInfo(
        val kind: ContainerKind,
        val type: DeclaredType,
    )

    /** Container kinds are memoized by qualified name. */
    private val containerKinds: MutableMap<String, ContainerKind?> = mutableMapOf()

    /**
     * Returns the [ContainerInfo] of [type], walking the supertype hierarchy (so `ArrayList`
     * matches `java.lang.Iterable`). Whether a type is a container does not depend on its type
     * arguments, so the kind is memoized by qualified name. The matched declared supertype,
     * however, is resolved per call so its type arguments reflect the concrete usage.
     */
    private fun containerKindOf(type: DeclaredType): ContainerInfo? {
        val element = asTypeElement(type) ?: return null
        val name = element.qualifiedName.toString()
        return containerKinds.getOrPut(name) { computeContainerKind(type, name) }
            ?.let { kind -> ContainerInfo(kind, resolveContainerType(type, kind) ?: type) }
    }

    private fun computeContainerKind(
        type: DeclaredType,
        name: String,
    ): ContainerKind? =
        when (name) {
            "java.lang.Object" -> null
            "java.util.Map" -> ContainerKind.MAP
            "java.lang.Iterable" -> ContainerKind.ITERABLE
            else ->
                types.directSupertypes(type)
                    .filterIsInstance<DeclaredType>()
                    .firstNotNullOfOrNull(::containerKindOf)
                    ?.kind
        }

    private fun resolveContainerType(
        type: DeclaredType,
        kind: ContainerKind,
    ): DeclaredType? {
        val element = asTypeElement(type) ?: return null
        return when (element.qualifiedName.toString()) {
            "java.util.Map" -> if (kind == ContainerKind.MAP) type else null
            "java.lang.Iterable" -> if (kind == ContainerKind.ITERABLE) type else null
            "java.lang.Object" -> null
            else ->
                types.directSupertypes(type)
                    .filterIsInstance<DeclaredType>()
                    .firstNotNullOfOrNull { resolveContainerType(it, kind) }
        }
    }

    /**
     * Resolves a wildcard type argument to its declared bound (`? extends Foo` → `Foo`,
     * `? super Bar` → `Bar`), leaving concrete types untouched. Unbounded wildcards
     * resolve to null so callers can apply a fallback.
     */
    private fun TypeMirror.resolveWildcard(): TypeMirror? =
        when (this) {
            is WildcardType -> extendsBound ?: superBound
            else -> this
        }

    private fun handleRecord(type: TypeMirror): TypeRef? {
        val element = asTypeElement(type)
        if (element == null || element.kind != ElementKind.RECORD) return null

        val id = TypeId(element.qualifiedName.toString())

        withCycleDetection(type, id) {
            buildOrGet(type, id) {
                val props = ArrayList<Property>()
                val required = LinkedHashSet<String>()

                element.recordComponents.forEach { component ->
                    val name = component.simpleName.toString()
                    val field = fieldFor(element, name)
                    val targets = listOfNotNull(component, component.accessor, field)
                    // Skip components marked with an ignore annotation (e.g. @JsonIgnore)
                    if (isIgnored(targets)) return@forEach
                    val propertyName = nameOverrideFor(targets) ?: name
                    val componentType = component.asType()
                    // Optional by convention (type-name pattern or @Nullable-style annotation) —
                    // excluded from `required`, the same way a Kotlin default value is handled.
                    if (!isOptionalByTypeName(componentType) && !isOptionalAnnotated(targets)) {
                        required += propertyName
                    }
                    val description = recordComponentDescription(component, field)
                    props += toProperty(propertyName, componentType, description, targets)
                }

                objectNode(element, props, required)
            }
        }

        return TypeRef.Ref(id)
    }

    private fun handleClass(type: TypeMirror): TypeRef? {
        val element = asTypeElement(type)
        if (element == null || element.kind != ElementKind.CLASS) return null

        val id = TypeId(element.qualifiedName.toString())

        withCycleDetection(type, id) {
            buildOrGet(type, id) {
                val props = ArrayList<Property>()
                val required = LinkedHashSet<String>()

                element.enclosedElements
                    .filterIsInstance<VariableElement>()
                    .filter { it.kind == ElementKind.FIELD && !it.modifiers.contains(Modifier.STATIC) }
                    .forEach { field ->
                        val name = field.simpleName.toString()
                        // Skip fields marked with an ignore annotation (e.g. @JsonIgnore)
                        if (isIgnored(listOf(field))) return@forEach
                        val propertyName = nameOverrideFor(listOf(field)) ?: name
                        val fieldType = field.asType()
                        if (!isOptionalByTypeName(fieldType) && !isOptionalAnnotated(listOf(field))) {
                            required += propertyName
                        }
                        props += toProperty(propertyName, fieldType, extractDescription(field), listOf(field))
                    }

                objectNode(element, props, required)
            }
        }

        return TypeRef.Ref(id)
    }

    private fun handleInterface(type: TypeMirror): TypeRef? {
        val element = asTypeElement(type)
        if (element == null || element.kind != ElementKind.INTERFACE) return null

        val id = TypeId(element.qualifiedName.toString())

        withCycleDetection(type, id) {
            buildOrGet(type, id) {
                val props = ArrayList<Property>()
                val required = LinkedHashSet<String>()

                element.enclosedElements
                    .filterIsInstance<ExecutableElement>()
                    .filter { it.kind == ElementKind.METHOD }
                    .filter { !it.modifiers.contains(Modifier.STATIC) }
                    .filter { it.parameters.isEmpty() && it.returnType.kind != TypeKind.VOID }
                    .forEach { method ->
                        // Skip accessors marked with an ignore annotation (e.g. @JsonIgnore)
                        if (isIgnored(listOf(method))) return@forEach
                        val name = nameOverrideFor(listOf(method)) ?: propertyName(method.simpleName.toString())
                        val returnType = method.returnType
                        if (!isOptionalByTypeName(returnType) && !isOptionalAnnotated(listOf(method))) {
                            required += name
                        }
                        props += toProperty(name, returnType, extractDescription(method), listOf(method))
                    }

                objectNode(element, props, required)
            }
        }

        return TypeRef.Ref(id)
    }

    /**
     * Maps a no-arg accessor method to a property name using the JavaBeans convention:
     * `getName()` → `name`, `isActive()` → `active`, and a bare `name()` accessor is kept as-is.
     */
    private fun propertyName(methodName: String): String =
        when {
            methodName.length > GET_PREFIX.length &&
                methodName.startsWith(GET_PREFIX) &&
                methodName[GET_PREFIX.length].isUpperCase() ->
                decapitalize(methodName.substring(GET_PREFIX.length))

            methodName.length > IS_PREFIX.length &&
                methodName.startsWith(IS_PREFIX) &&
                methodName[IS_PREFIX.length].isUpperCase() ->
                decapitalize(methodName.substring(IS_PREFIX.length))

            else -> methodName
        }

    /**
     * Applies JavaBeans decapitalization to a property suffix: `Name` → `name`, while
     * preserving all-uppercase acronyms such as `URL` or `OK` and leaving mixed-case
     * suffixes such as `urlPath` untouched.
     */
    private fun decapitalize(name: String): String =
        if (name.length > 1 && name[0].isUpperCase() && name[1].isUpperCase()) {
            name
        } else {
            name.replaceFirstChar { it.lowercase() }
        }

    /**
     * Returns the cached node for [id] or builds and caches it via [builder].
     *
     * A cached node was built for another root, so its `$ref` targets are not yet
     * registered in the current context; re-register them so each root's `$defs` stays
     * complete. Registration recurses through [toRef], which deduplicates via
     * [withCycleDetection], so shared subgraphs are registered exactly once per root.
     */
    private fun buildOrGet(
        type: TypeMirror,
        id: TypeId,
        builder: () -> TypeNode,
    ): TypeNode {
        nodeCache[id]?.let { cached ->
            registerRefs(cached.node)
            return cached.node
        }
        return builder().also { nodeCache[id] = CachedNode(type, it) }
    }

    /**
     * Re-registers the types referenced by [node] into the current context. The apt front
     * end only caches object nodes, so any other cached node kind is an unsupported
     * invariant. References are registered through inline list/map nodes as well, so a
     * cached `List<Shared>` property still pulls `Shared` into a fresh root's `$defs`.
     */
    private fun registerRefs(node: TypeNode) {
        val objectNode =
            node as? ObjectNode
                ?: error("Unsupported cached node kind $node; registerRefs must handle all node kinds")
        objectNode.properties.forEach { property ->
            registerRefs(property.type)
        }
    }

    private fun registerRefs(typeRef: TypeRef) {
        when (typeRef) {
            is TypeRef.Ref -> toRef(nodeCache.getValue(typeRef.id).type)
            is TypeRef.Inline ->
                when (val node = typeRef.node) {
                    is ListNode -> registerRefs(node.element)
                    is MapNode -> {
                        registerRefs(node.key)
                        registerRefs(node.value)
                    }
                    else -> Unit
                }
        }
    }

    private companion object {
        const val GET_PREFIX: String = "get"
        const val IS_PREFIX: String = "is"
    }

    //endregion

    //region Property conversion

    private fun toProperty(
        name: String,
        type: TypeMirror,
        description: String?,
        annotationTargets: List<Element> = emptyList(),
    ): Property =
        Property(
            name = name,
            type = toRef(type).let { if (isNullableAnnotated(annotationTargets)) it.withNullable(true) else it },
            description = description,
        )

    private fun objectNode(
        element: TypeElement,
        properties: List<Property>,
        required: Set<String>,
    ): ObjectNode =
        ObjectNode(
            name = nameOverrideFor(listOf(element)) ?: element.qualifiedName.toString(),
            properties = properties,
            required = required,
            description = extractDescription(element),
        )

    //endregion

    //region Annotation helpers

    /**
     * Resolves a record component's description from its annotation targets, in order of
     * precedence: the record component itself, its accessor, then the backing field.
     */
    private fun recordComponentDescription(
        component: RecordComponentElement,
        field: VariableElement?,
    ): String? =
        extractDescription(component)
            ?: extractDescription(component.accessor)
            ?: field?.let(::extractDescription)

    /**
     * Record component annotations propagate to the backing field (among other targets),
     * so the field is used as a last-resort description target for annotations whose
     * `@Target` does not cover the record component or its accessor.
     */
    private fun fieldFor(
        type: TypeElement,
        name: String,
    ): VariableElement? =
        type.enclosedElements
            .filterIsInstance<VariableElement>()
            .firstOrNull { it.kind == ElementKind.FIELD && it.simpleName.contentEquals(name) }

    private fun extractDescription(element: Element): String? =
        element.annotationMirrors.firstNotNullOfOrNull { mirror ->
            val annotationElement = mirror.annotationType.asElement() as TypeElement
            val args =
                mirror.elementValues.entries.map { (attribute, value) ->
                    attribute.simpleName.toString() to value.value
                }
            Introspections.getDescriptionFromAnnotation(
                simpleName = annotationElement.simpleName.toString(),
                qualifiedName = annotationElement.qualifiedName.toString(),
                annotationArguments = args,
            )
        }

    /**
     * Returns the first name-override value (e.g. from `@JsonProperty`, `@JsonTypeName`)
     * found across the given annotation targets, in order, or null if none provides one.
     */
    private fun nameOverrideFor(targets: List<Element>): String? =
        targets.firstNotNullOfOrNull(::extractNameOverride)

    private fun extractNameOverride(element: Element): String? =
        element.annotationMirrors.firstNotNullOfOrNull { mirror ->
            val annotationElement = mirror.annotationType.asElement() as TypeElement
            val args =
                mirror.elementValues.entries.map { (attribute, value) ->
                    attribute.simpleName.toString() to value.value
                }
            Introspections.getNameOverride(
                simpleName = annotationElement.simpleName.toString(),
                qualifiedName = annotationElement.qualifiedName.toString(),
                annotationArguments = args,
            )
        }

    /**
     * Returns `true` if any of the given annotation targets carries a recognized
     * ignore annotation (e.g. `@JsonIgnore`).
     */
    private fun isIgnored(targets: List<Element>): Boolean =
        targets.any(::isIgnoreAnnotation)

    private fun isIgnoreAnnotation(element: Element): Boolean =
        element.annotationMirrors.any { mirror ->
            val annotationElement = mirror.annotationType.asElement() as TypeElement
            Introspections.isIgnoreAnnotation(
                simpleName = annotationElement.simpleName.toString(),
                qualifiedName = annotationElement.qualifiedName.toString(),
            )
        }

    /**
     * Returns `true` if any of the given annotation targets carries a recognized nullable
     * marker (e.g. `@Nullable`).
     */
    private fun isNullableAnnotated(targets: List<Element>): Boolean =
        targets.any(::isNullableAnnotation)

    private fun isNullableAnnotation(element: Element): Boolean =
        element.annotationMirrors.any { mirror ->
            val annotationElement = mirror.annotationType.asElement() as TypeElement
            Introspections.isNullableAnnotation(
                simpleName = annotationElement.simpleName.toString(),
                qualifiedName = annotationElement.qualifiedName.toString(),
            )
        }

    /**
     * Returns `true` if any of the given annotation targets carries a recognized optional
     * marker (e.g. `@Nullable`).
     */
    private fun isOptionalAnnotated(targets: List<Element>): Boolean =
        targets.any(::isOptionalAnnotation)

    private fun isOptionalAnnotation(element: Element): Boolean =
        element.annotationMirrors.any { mirror ->
            val annotationElement = mirror.annotationType.asElement() as TypeElement
            Introspections.isOptionalAnnotation(
                simpleName = annotationElement.simpleName.toString(),
                qualifiedName = annotationElement.qualifiedName.toString(),
            )
        }

    /**
     * Checks whether [type]'s simple class name matches a configured nullable-type-name glob
     * pattern (e.g. `*Opt`).
     */
    private fun isNullableByTypeName(type: TypeMirror): Boolean =
        Introspections.isNullableTypeName(asTypeElement(type)?.simpleName?.toString())

    /**
     * Checks whether [type]'s simple class name matches a configured optional-type-name glob
     * pattern (e.g. `*Opt`).
     */
    private fun isOptionalByTypeName(type: TypeMirror): Boolean =
        Introspections.isOptionalTypeName(asTypeElement(type)?.simpleName?.toString())

    //endregion
}

/**
 * A memoized node together with the [TypeMirror] used to build it, so cached `$ref`
 * targets can be re-registered into a fresh root context.
 */
internal data class CachedNode(
    val type: TypeMirror,
    val node: TypeNode,
)
