package me.kpavlov.kt.schema.generator.core.ir

import me.kpavlov.kt.schema.generator.core.InternalSchemaGeneratorApi
import kotlin.jvm.JvmInline

/** A graph of discovered types plus the root type reference used to emit schemas. */
public data class TypeGraph(
    val root: TypeRef,
    val nodes: Map<TypeId, TypeNode>,
)

/** A stable identifier for a type definition used for deduplication and $ref linking. */
@JvmInline
public value class TypeId(
    public val value: String,
)

/** Reference to a type: either inline node or reference by [TypeId]. */
public sealed interface TypeRef {
    public val nullable: Boolean

    /** Inline node reference (anonymous, not addressable by $ref). */
    public data class Inline(
        val node: TypeNode,
        override val nullable: Boolean = false,
    ) : TypeRef

    /** Reference a named/type-def node by its [TypeId]. */
    public data class Ref(
        val id: TypeId,
        override val nullable: Boolean = false,
    ) : TypeRef
}

/**
 * Returns a copy of this [TypeRef] with the specified nullable flag.
 * Shared across introspection front ends (reflection, KSP, APT, kotlinx.serialization) so
 * conventions layered on top of native nullability (e.g. `@Nullable`-style annotations,
 * type-name patterns) all go through the same code path.
 */
@InternalSchemaGeneratorApi
public fun TypeRef.withNullable(nullable: Boolean): TypeRef =
    when (this) {
        is TypeRef.Inline -> copy(nullable = nullable)
        is TypeRef.Ref -> copy(nullable = nullable)
    }

/** Base node for all kinds supported by the schema IR. */
public sealed interface TypeNode {
    public val description: String?
}

/**
 * A named type node.
 *
 * Contract of [name]:
 * - Populated for classes, enums, and sealed/polymorphic hierarchies alike. The reflection, KSP,
 *   and APT front ends populate it with the `@JsonTypeName` override when present, otherwise with
 *   the declared type name. The serialization front end uses the raw `@SerialName` value without
 *   an FQN fallback.
 * - `$ref`/`$id`/`$defs` emission for nodes reachable via [TypeId] is driven by [name] through
 *   [TypeGraph.jsonTypeNames], which falls back to the [TypeId] value only when two different
 *   nodes resolve to the same [name] (e.g. two distinct types sharing the same override).
 */
public sealed interface NamedTypeNode : TypeNode {
    public val name: String
}

/** Primitive kinds supported by the IR. */
public enum class PrimitiveKind { STRING, BOOLEAN, INT, LONG, FLOAT, DOUBLE }

/** Primitive node. */
public data class PrimitiveNode(
    val kind: PrimitiveKind,
    override val description: String? = null,
) : TypeNode

/** Enum node with symbolic entries. */
public data class EnumNode(
    override val name: String,
    val entries: List<String>,
    override val description: String? = null,
) : NamedTypeNode

/** Object node with named properties and required set. */
public data class ObjectNode(
    override val name: String,
    val properties: List<Property>,
    val required: Set<String>,
    override val description: String? = null,
) : NamedTypeNode

/** List/array node. */
public data class ListNode(
    val element: TypeRef,
    override val description: String? = null,
) : TypeNode

/** Map/dictionary node. */
public data class MapNode(
    val key: TypeRef,
    val value: TypeRef,
    override val description: String? = null,
) : TypeNode

/** Any/unconstrained type node — emits `{}` in JSON Schema (accepts any value). */
public data class AnyNode(
    override val description: String? = null,
) : TypeNode

/** Polymorphic node for sealed/open hierarchies. */
public data class PolymorphicNode(
    override val name: String,
    val subtypes: List<SubtypeRef>,
    val discriminator: Discriminator,
    override val description: String? = null,
) : NamedTypeNode

/** Property of an object. */
public data class Property(
    val name: String,
    val type: TypeRef,
    val description: String? = null,
    val deprecated: Boolean = false,
    val hasDefaultValue: Boolean = false,
    val defaultValue: Any? = null,
    val isConstant: Boolean = false,
    val annotations: Map<String, String?> = emptyMap(),
)

/** Reference to a subtype in a polymorphic hierarchy. */
public data class SubtypeRef(
    val id: TypeId,
    val ref: TypeRef.Ref = TypeRef.Ref(id),
)

/**
 * Class discriminator information. If [mapping] is null, default implicit mapping is assumed
 * (typically discriminator value equals subtype serial name).
 */
public data class Discriminator(
    val name: String,
    val mapping: Map<String, TypeId>? = null,
)
