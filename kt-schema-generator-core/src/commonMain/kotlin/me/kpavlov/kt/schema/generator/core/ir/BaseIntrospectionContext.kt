package me.kpavlov.kt.schema.generator.core.ir

import me.kpavlov.kt.schema.generator.core.InternalSchemaGeneratorApi

/**
 * Base context for introspection that maintains state and provides common utilities.
 *
 * This abstraction extracts common patterns from Reflection, KSP, and potential
 * Serialization introspectors, including:
 * - State management (discovered nodes, visiting set, type cache)
 * - Cycle detection lifecycle
 * - Type resolution patterns
 *
 * @param TType Type to convert from (KType, KSType, SerialDescriptor)
 * @suppress Not part of public API - used internally by introspector implementations.
 */
@InternalSchemaGeneratorApi
@Suppress("AbstractClassCanBeConcreteClass")
public abstract class BaseIntrospectionContext<TType : Any> {
    /**
     * Map of discovered type nodes indexed by their type ID.
     * LinkedHashMap preserves discovery order for deterministic output.
     */
    private val _nodes: MutableMap<TypeId, TypeNode> = linkedMapOf()

    /**
     * Exposes discovered nodes as an unmodifiable view for building TypeGraph.
     * Provides a consistent API across all introspector implementations (Reflection, KSP, Serialization).
     */
    public val nodes: Map<TypeId, TypeNode>
        get(): Map<TypeId, TypeNode> = _nodes

    /**
     * Set of types currently being visited (for cycle detection).
     * When a type references itself (directly or indirectly), we detect the cycle
     * and avoid infinite recursion by checking this set.
     */
    protected val visitingTypes: MutableSet<TType> = mutableSetOf()

    /**
     * Ids of the types currently being visited. A type is also a cycle if another representation of it
     * is being built, which equality of [TType] may not tell (e.g. javac type mirrors compare by identity).
     */
    private val visitingIds: MutableSet<TypeId> = mutableSetOf()

    /**
     * Cache of type references to avoid redundant processing.
     * Stores non-nullable refs to declarations for reuse.
     */
    protected val typeRefCache: MutableMap<TType, TypeRef> = mutableMapOf()

    /**
     * Value classes currently being flattened by [flattenValueClass], each mapped to the
     * [visitingTypes] size when its flattening started.
     */
    private val flatteningDepths: MutableMap<TType, Int> = mutableMapOf()

    /** Value classes from [flatteningDepths] that were re-entered through a purely inline cycle. */
    private val inlineCycleTypes: MutableSet<TType> = mutableSetOf()

    /**
     * Converts [type] to a [TypeRef] for use in the schema.
     * This is the main entry point for type conversion.
     */
    public abstract fun toRef(type: TType): TypeRef

    /**
     * Cycle detection helper that manages visiting set lifecycle.
     *
     * Pattern used by all introspectors:
     * 1. Check if already discovered or currently being visited
     * 2. Mark as visiting
     * 3. Build the node (which may recursively call toRef)
     * 4. Add to discovered nodes
     * 5. Unmark as visiting
     *
     * @param type The type being processed
     * @param id The TypeId for this declaration
     * @param nodeBuilder Lambda that constructs the TypeNode
     * @return true if node was created, false if already visited/in progress
     */
    protected fun withCycleDetection(
        type: TType,
        id: TypeId,
        nodeBuilder: () -> TypeNode,
    ): Boolean {
        if (id in _nodes || id in visitingIds) {
            return false
        }

        visitingTypes += type
        visitingIds += id
        try {
            val node = nodeBuilder()
            _nodes[id] = node
            return true
        } finally {
            visitingTypes -= type
            visitingIds -= id
        }
    }

    /**
     * Flattens the value class [key] (its non-null type) to the [TypeRef] returned by [flatten].
     *
     * A value class re-entered with no `$ref`-able node built in between (e.g.
     * `value class Tree(val children: List<Tree>)`) is a purely inline cycle: the re-entry returns
     * a [TypeRef.Ref] to [id], and the outermost frame registers the flattened node under [id].
     * A cycle that passes through an object is already broken by that object's `$ref`.
     *
     * More than [MAX_FLATTENING_DEPTH] value classes flattened directly inside one another are cut
     * off as "any value". This stops polymorphic recursion
     * (`value class Nest<T>(val items: List<Nest<List<T>>>)`), which yields a new type per level,
     * but also applies to deeper chains of distinct value classes.
     *
     * @param nullable whether the value class occurrence is nullable
     * @param wrappedNullable whether the wrapped type is nullable
     * @param description class-level description of the value class, set on the flattened node
     */
    @Suppress("ReturnCount")
    protected fun flattenValueClass(
        key: TType,
        id: TypeId,
        nullable: Boolean,
        wrappedNullable: Boolean,
        description: String?,
        flatten: () -> TypeRef,
    ): TypeRef {
        val objectDepth = visitingTypes.size
        if (flatteningDepths[key] == objectDepth) {
            inlineCycleTypes += key
            return TypeRef.Ref(id, nullable || wrappedNullable)
        }
        if (flatteningDepths.values.count { it == objectDepth } >= MAX_FLATTENING_DEPTH) {
            return TypeRef.Inline(AnyNode(), nullable)
        }

        val outerDepth = flatteningDepths.put(key, objectDepth)
        val wrappedRef =
            try {
                flatten()
            } finally {
                if (outerDepth == null) flatteningDepths -= key else flatteningDepths[key] = outerDepth
            }
        // There is no wrapper object left to carry the class description, so it moves to the inline node.
        val describedRef =
            if (description != null && wrappedRef is TypeRef.Inline) {
                wrappedRef.copy(node = wrappedRef.node.withDescription(description))
            } else {
                wrappedRef
            }
        if (!inlineCycleTypes.remove(key)) return describedRef

        // Kotlin rejects a value class whose underlying type is itself, so the cycle always runs
        // through an inline collection here.
        val node = checkNotNull((describedRef as? TypeRef.Inline)?.node) { "Unexpected inline cycle via $wrappedRef" }
        // The id must carry the type arguments (see appliedTypeId), or two recursive applications of one
        // generic value class would share a definition.
        withCycleDetection(key, id) { node }
        return TypeRef.Ref(id, nullable || wrappedRef.nullable)
    }

    /**
     * Returns the [TypeId] of [declaration] applied to [arguments], each the id value of an argument type, followed
     * by `?` if nullable, or null for an unbound one (a star projection or an unbound type variable), which is
     * treated as `Any?`. With no bound argument this is the plain declaration id, so a generic root keeps the types
     * its members are declared with.
     */
    protected fun applicationId(
        declaration: String,
        arguments: List<String?>,
    ): TypeId =
        if (arguments.all { it == null }) {
            TypeId(declaration)
        } else {
            appliedTypeId(declaration, arguments.map { it ?: ANY_ARGUMENT_ID })
        }

    /**
     * Returns whether [MAX_APPLICATION_NESTING] or more types matching [isSameDeclaration] are being built inside
     * one another. Each application of a polymorphically recursive generic class
     * (`class Skew<T>(val next: Skew<List<T>>?)`) is a new type, so callers cut such nesting off as "any value".
     */
    protected fun exceedsApplicationNesting(isSameDeclaration: (TType) -> Boolean): Boolean =
        visitingTypes.count(isSameDeclaration) >= MAX_APPLICATION_NESTING

    /**
     * Registers the [NamedTypeNode] built by [nodeBuilder] for [id] (idempotent via
     * [withCycleDetection]) and returns a [TypeRef.Ref] to it.
     *
     * Named type nodes (enums, objects, polymorphic hierarchies) are always addressable by
     * `$ref`, never inlined — encoding that contract in the return type keeps it enforced by
     * the compiler instead of by convention repeated per front end. Currently used for enum
     * registration across the reflection, KSP, APT and kotlinx.serialization front ends;
     * object/polymorphic registration remains front-end-specific for now.
     */
    protected fun namedRef(
        type: TType,
        id: TypeId,
        nullable: Boolean = false,
        nodeBuilder: () -> NamedTypeNode,
    ): TypeRef.Ref {
        withCycleDetection(type, id, nodeBuilder)
        return TypeRef.Ref(id, nullable)
    }

    /**
     * Returns the discriminator property name of a sealed type: the first name [declaredName] finds on
     * [typeAndSupertypes], nearest first, or [Discriminator.DEFAULT_NAME].
     */
    protected fun <D> discriminatorName(
        typeAndSupertypes: Sequence<D>,
        declaredName: (D) -> String?,
    ): String = typeAndSupertypes.firstNotNullOfOrNull(declaredName) ?: Discriminator.DEFAULT_NAME

    protected companion object {
        /** Id of a type argument that is a star projection or an unbound type parameter: it is treated as `Any?`. */
        protected const val ANY_ARGUMENT_ID: String = "kotlin.Any?"
    }
}

/** Maximum number of value classes flattened directly inside one another. */
private const val MAX_FLATTENING_DEPTH = 8

/** Maximum number of applications of one generic class being built inside one another. */
private const val MAX_APPLICATION_NESTING = 8
