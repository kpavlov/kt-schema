package me.kpavlov.kt.schema.generator.json

import me.kpavlov.kt.schema.generator.core.ir.TypeGraph
import me.kpavlov.kt.schema.generator.core.ir.TypeId

/**
 * Resolves the JSON type name for every node id in the graph, used for `$defs` keys, `$ref`
 * targets, discriminator values and the root `$id`.
 *
 * Each id resolves to its short (simple) name, falling back to the full id when the short name is
 * ambiguous across the graph — e.g. nested types `ResultA.Success` and `ResultB.Success` both
 * resolve to `Success`, which would collide in `$defs`.
 *
 * Callers should compute the map once per graph (O(n)) and look up by id (O(1)).
 */
internal fun TypeGraph.jsonTypeNames(): Map<TypeId, String> {
    val shortNames = nodes.keys.associateWith { it.value.substringAfterLast('.') }
    val collidingShortNames =
        shortNames.values
            .groupingBy { it }
            .eachCount()
            .filterValues { count -> count > 1 }
            .keys
    return nodes.keys.associateWith { id ->
        val shortName = shortNames.getValue(id)
        if (shortName in collidingShortNames) id.value else shortName
    }
}
