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

/** Returns a copy of this [TypeNode] with the specified [description]. */
@InternalSchemaGeneratorApi
public fun TypeNode.withDescription(description: String): TypeNode =
    when (this) {
        is PrimitiveNode -> copy(description = description)
        is ListNode -> copy(description = description)
        is MapNode -> copy(description = description)
        is AnyNode -> copy(description = description)
        is EnumNode -> copy(description = description)
        is ObjectNode -> copy(description = description)
        is PolymorphicNode -> copy(description = description)
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
    val defaultValue: String? = null,
    override val description: String? = null,
) : NamedTypeNode

/**
 * Object node with named properties.
 *
 * Requiredness is not stored: emitters derive it from [Property.optional], [Property.value] and their config.
 */
public data class ObjectNode(
    override val name: String,
    val properties: List<Property>,
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

/** The value attached to a [Property]: nothing, an unknown default, a default literal, or a constant. */
public sealed interface PropertyValue {
    /** No default or constant is declared. */
    public data object None : PropertyValue

    /**
     * A default is declared but its value can't be obtained, so the schema gets no `default`.
     * Implies [Property.optional].
     */
    public data object UnknownDefault : PropertyValue

    /** A known default value. */
    public data class Default(
        val literal: Literal,
    ) : PropertyValue

    /** A fixed value; the property is always required and the schema gets a `const`. */
    public data class Const(
        val literal: Literal,
    ) : PropertyValue
}

/** A JSON-compatible literal value, independent of any schema dialect; used for defaults and constants. */
public sealed interface Literal {
    /** The JSON `null` literal. */
    public data object Null : Literal

    /** A JSON boolean. */
    public data class Bool(
        val value: Boolean,
    ) : Literal

    /** A JSON integer. */
    public data class Integer(
        val value: Long,
    ) : Literal

    /** A JSON number with a fractional part or exponent; always finite. */
    public data class Decimal(
        val value: Double,
    ) : Literal

    /** A JSON string. */
    public data class Str(
        val value: String,
    ) : Literal

    /** A JSON array. */
    public data class ListOf(
        val items: List<Literal>,
    ) : Literal

    /** A JSON object with string keys. */
    public data class MapOf(
        val entries: Map<String, Literal>,
    ) : Literal
}

/**
 * Property of an object.
 *
 * @property optional whether the property may be omitted, as declared by an explicit marker in the source
 *   (a Kotlin default value, an optional type-name convention or an optional annotation).
 *   A default literal alone does not set it. Ignored for [PropertyValue.Const], which is always required.
 * @property value the default or constant attached to the property, if any.
 */
public data class Property(
    val name: String,
    val type: TypeRef,
    val description: String? = null,
    val deprecated: Boolean = false,
    val optional: Boolean = false,
    val value: PropertyValue = PropertyValue.None,
    val annotations: Map<String, String?> = emptyMap(),
)

/**
 * Returns whether this property must be present under the base presence rule: constants always are,
 * otherwise it is unless it is [Property.optional] or has a known default.
 *
 * Emitters apply their config on top of this.
 */
@InternalSchemaGeneratorApi
public fun Property.isPresenceRequired(): Boolean =
    value is PropertyValue.Const || !(optional || value is PropertyValue.Default)

/**
 * Converts this Kotlin value to a [Literal], or returns `null` if it has no JSON representation
 * (e.g. a non-finite decimal).
 *
 * Enums are converted by [enumEntryName]; elements and entries that can't be converted are dropped
 * from collections.
 */
@InternalSchemaGeneratorApi
@Suppress("CyclomaticComplexMethod")
public fun Any?.toLiteral(enumEntryName: (Enum<*>) -> String = { it.name }): Literal? =
    when (this) {
        null -> Literal.Null
        is Boolean -> Literal.Bool(this)
        is Byte, is Short, is Int, is Long -> Literal.Integer((this as Number).toLong())
        // toString() avoids widening artifacts such as 0.1f -> 0.10000000149011612
        is Float -> if (isFinite()) Literal.Decimal(toString().toDouble()) else null
        is Double -> if (isFinite()) Literal.Decimal(this) else null
        is Number -> {
            val text = toString()
            text.toLongOrNull()?.let(Literal::Integer)
                ?: if (text.removePrefix("-").all { it in '0'..'9' }) {
                    null // An integer beyond Long has no exact literal; a Double would silently round it
                } else {
                    toDouble().takeIf { it.isFinite() }?.let(Literal::Decimal)
                }
        }
        is String -> Literal.Str(this)
        is Char -> Literal.Str(toString())
        is Enum<*> -> Literal.Str(enumEntryName(this))
        is Iterable<*> -> Literal.ListOf(mapNotNull { it.toLiteral(enumEntryName) })
        is Array<*> -> Literal.ListOf(mapNotNull { it.toLiteral(enumEntryName) })
        is Map<*, *> ->
            Literal.MapOf(
                entries.mapNotNull { (k, v) ->
                    val key = (k as? Enum<*>)?.let(enumEntryName) ?: k?.toString() ?: return@mapNotNull null
                    val literal = v.toLiteral(enumEntryName) ?: return@mapNotNull null
                    key to literal
                }.toMap(),
            )
        else -> null
    }

/** Converts this literal to the equivalent Kotlin value (`Long`, `Double`, `String`, `List`, `Map`, ...). */
@InternalSchemaGeneratorApi
public fun Literal.toKotlinValue(): Any? =
    when (this) {
        Literal.Null -> null
        is Literal.Bool -> value
        is Literal.Integer -> value
        is Literal.Decimal -> value
        is Literal.Str -> value
        is Literal.ListOf -> items.map { it.toKotlinValue() }
        is Literal.MapOf -> entries.mapValues { it.value.toKotlinValue() }
    }

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
) {
    public companion object {
        /** The discriminator property name used when a sealed type does not declare one. */
        public const val DEFAULT_NAME: String = "type"
    }
}
