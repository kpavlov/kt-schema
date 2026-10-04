package me.kpavlov.kt.schema.ksp.ir

import com.google.devtools.ksp.getAllSuperTypes
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Nullability
import me.kpavlov.kt.schema.generator.core.ir.ListNode
import me.kpavlov.kt.schema.generator.core.ir.MapNode
import me.kpavlov.kt.schema.generator.core.ir.PrimitiveKind
import me.kpavlov.kt.schema.generator.core.ir.PrimitiveNode
import me.kpavlov.kt.schema.generator.core.ir.TypeRef

/** Type mapping shared by the KSP introspectors. */
internal object KspTypeMappers {
    /**
     * Maps a Kotlin primitive to a [PrimitiveNode], or null: String/Char -> STRING, Boolean, Int/Byte/Short -> INT,
     * Long, Float, Double.
     */
    fun primitiveFor(type: KSType): PrimitiveNode? {
        val qn = type.declaration.qualifiedName?.asString()
        return when (qn) {
            "kotlin.String", "kotlin.Char" -> PrimitiveNode(PrimitiveKind.STRING)
            "kotlin.Boolean" -> PrimitiveNode(PrimitiveKind.BOOLEAN)
            "kotlin.Int", "kotlin.Byte", "kotlin.Short" -> PrimitiveNode(PrimitiveKind.INT)
            "kotlin.Long" -> PrimitiveNode(PrimitiveKind.LONG)
            "kotlin.Float" -> PrimitiveNode(PrimitiveKind.FLOAT)
            "kotlin.Double" -> PrimitiveNode(PrimitiveKind.DOUBLE)
            else -> null
        }
    }

    /**
     * Maps the named Kotlin collection and array types to a list or map [TypeRef], or null for other types.
     * Element, key and value types are resolved with [recursiveMapper].
     */
    fun collectionTypeRefOrNull(
        type: KSType,
        recursiveMapper: (KSType) -> TypeRef,
    ): TypeRef? {
        val nullable = type.nullability == Nullability.NULLABLE
        val qn = type.declaration.qualifiedName?.asString() ?: return null

        return when (qn) {
            "kotlin.collections.Iterable",
            "kotlin.collections.MutableIterable",
            "kotlin.collections.Collection",
            "kotlin.collections.MutableCollection",
            "kotlin.collections.List",
            "kotlin.collections.MutableList",
            "kotlin.collections.Set",
            "kotlin.collections.MutableSet",
            "kotlin.Array",
            "kotlin.BooleanArray",
            "kotlin.ByteArray",
            "kotlin.ShortArray",
            "kotlin.IntArray",
            "kotlin.LongArray",
            "kotlin.FloatArray",
            "kotlin.DoubleArray",
            "kotlin.CharArray",
            -> {
                listOrSetTypeRef(type, nullable, recursiveMapper)
            }

            "kotlin.collections.Map",
            "kotlin.collections.MutableMap",
            -> {
                mapTypeRef(type, nullable, recursiveMapper)
            }

            else -> {
                null
            }
        }
    }

    /**
     * Maps `java.util`/`kotlin.collections` classes implementing [Iterable] or [Map] that [collectionTypeRefOrNull]
     * doesn't name (e.g. `ArrayList`, `HashMap`, which the `kotlin.collections` typealiases expand to) to a list or
     * map, or null. Other implementers (e.g. `Path`, `IntRange`, user types) keep their object schema.
     */
    fun platformCollectionTypeRefOrNull(
        type: KSType,
        recursiveMapper: (KSType) -> TypeRef,
    ): TypeRef? {
        val declaration =
            (type.declaration as? KSClassDeclaration)?.takeIf { candidate ->
                val qn = candidate.qualifiedName?.asString().orEmpty()
                qn.startsWith("java.util.") || qn.startsWith("kotlin.collections.")
            } ?: return null

        val nullable = type.nullability == Nullability.NULLABLE
        val superTypeNames =
            declaration
                .getAllSuperTypes()
                .mapNotNull { it.declaration.qualifiedName?.asString() }
                .toSet()
        return when {
            "kotlin.collections.Iterable" in superTypeNames -> listOrSetTypeRef(type, nullable, recursiveMapper)
            "kotlin.collections.Map" in superTypeNames -> mapTypeRef(type, nullable, recursiveMapper)
            else -> null
        }
    }

    /** List/Set/Array [TypeRef]. */
    private fun listOrSetTypeRef(
        type: KSType,
        nullable: Boolean,
        recursiveMapper: (KSType) -> TypeRef,
    ): TypeRef {
        val qn = type.declaration.qualifiedName?.asString()
        val primitiveElemKind =
            when (qn) {
                "kotlin.BooleanArray" -> PrimitiveKind.BOOLEAN
                "kotlin.ByteArray", "kotlin.ShortArray", "kotlin.IntArray" -> PrimitiveKind.INT
                "kotlin.LongArray" -> PrimitiveKind.LONG
                "kotlin.FloatArray" -> PrimitiveKind.FLOAT
                "kotlin.DoubleArray" -> PrimitiveKind.DOUBLE
                "kotlin.CharArray" -> PrimitiveKind.STRING
                else -> null
            }

        val elementRef =
            if (primitiveElemKind != null) {
                TypeRef.Inline(PrimitiveNode(primitiveElemKind))
            } else {
                val elem =
                    type.arguments
                        .firstOrNull()
                        ?.type
                        ?.resolve()
                if (elem != null) {
                    recursiveMapper(elem)
                } else {
                    TypeRef.Inline(PrimitiveNode(PrimitiveKind.STRING))
                }
            }
        return TypeRef.Inline(ListNode(element = elementRef), nullable)
    }

    /** Map [TypeRef]. */
    private fun mapTypeRef(
        type: KSType,
        nullable: Boolean,
        recursiveMapper: (KSType) -> TypeRef,
    ): TypeRef {
        val keyType =
            type.arguments
                .getOrNull(0)
                ?.type
                ?.resolve()
        val valueType =
            type.arguments
                .getOrNull(1)
                ?.type
                ?.resolve()

        val keyRef =
            if (keyType != null) {
                recursiveMapper(keyType)
            } else {
                TypeRef.Inline(PrimitiveNode(PrimitiveKind.STRING))
            }

        val valueRef =
            if (valueType != null) {
                recursiveMapper(valueType)
            } else {
                TypeRef.Inline(PrimitiveNode(PrimitiveKind.STRING))
            }

        return TypeRef.Inline(MapNode(key = keyRef, value = valueRef), nullable)
    }
}
