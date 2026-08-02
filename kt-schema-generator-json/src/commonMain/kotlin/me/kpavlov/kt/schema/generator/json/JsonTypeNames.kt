package me.kpavlov.kt.schema.generator.json

import me.kpavlov.kt.schema.generator.core.ir.NamedTypeNode
import me.kpavlov.kt.schema.generator.core.ir.TypeGraph
import me.kpavlov.kt.schema.generator.core.ir.TypeId

/**
 * Resolves the JSON type name for every node id in the graph, used for `$defs` keys, `$ref`
 * targets, discriminator values and the root `$id`.
 *
 * Each id resolves to its node's [NamedTypeNode.name] (the FQN unless a name-override annotation
 * was applied), falling back to the full id when the resolved name is ambiguous across the graph
 * — e.g. two distinct types both annotated with the same override name would otherwise collide
 * in `$defs`.
 *
 * Callers should compute the map once per graph (O(n)) and look up by id (O(1)).
 */
internal fun TypeGraph.jsonTypeNames(): Map<TypeId, String> {
    val names = nodes.mapValuesTo(LinkedHashMap()) { (id, node) -> (node as? NamedTypeNode)?.name ?: id.value }
    var changed = true
    while (changed) {
        changed = false
        val collidingNames =
            names.values
                .groupingBy { it }
                .eachCount()
                .filterValues { count -> count > 1 }
                .keys
        for ((id, name) in names) {
            if (name in collidingNames && name != id.value) {
                names[id] = id.value
                changed = true
            }
        }
    }
    return names
}
