package me.kpavlov.kt.schema.generator.json

import me.kpavlov.kt.schema.generator.core.ir.NamedTypeNode
import me.kpavlov.kt.schema.generator.core.ir.TypeGraph
import me.kpavlov.kt.schema.generator.core.ir.TypeId

/**
 * Resolves the JSON type name for every node id in the graph, used for `$defs` keys, `$ref`
 * targets, discriminator values and the root `$id`.
 *
 * An explicit name (a node name differing from its id, e.g. from `@JsonTypeName`) is honored
 * exactly. Names derived from the id are the full id, or with [shortNames] start as the simple
 * name and grow one package segment at a time while they collide.
 *
 * @throws IllegalStateException if two declarations resolve to the same name.
 *
 * Callers should compute the map once per graph (O(n)) and look up by id (O(1)).
 */
internal fun TypeGraph.jsonTypeNames(shortNames: Boolean = false): Map<TypeId, String> {
    val candidates =
        nodes.mapValues { (id, node) ->
            val name = (node as? NamedTypeNode)?.name
            when {
                name != null && name != id.value -> listOf(name)
                shortNames -> id.value.dottedSuffixes()
                else -> listOf(id.value)
            }
        }
    val chosen = candidates.mapValuesTo(LinkedHashMap()) { 0 }
    var changed = true
    while (changed) {
        changed = false
        val collidingNames =
            chosen
                .map { (id, index) -> candidates.getValue(id)[index] }
                .groupingBy { it }
                .eachCount()
                .filterValues { count -> count > 1 }
                .keys
        for ((id, index) in chosen) {
            val options = candidates.getValue(id)
            if (options[index] in collidingNames && index < options.lastIndex) {
                chosen[id] = index + 1
                changed = true
            }
        }
    }
    val resolved = chosen.mapValues { (id, index) -> candidates.getValue(id)[index] }
    resolved.entries
        .groupBy({ it.value }, { it.key.value })
        .forEach { (name, ids) ->
            check(ids.size == 1) {
                "Type name '$name' is used by multiple declarations: ${ids.sorted().joinToString()}. " +
                    "Give them distinct @JsonTypeName/@SerialName values."
            }
        }
    return resolved
}

// ponytail: type arguments stay attached to the last segment; #146 may put them into ids.
private fun String.dottedSuffixes(): List<String> {
    val head = substringBefore('<')
    val tail = substring(head.length)
    val segments = head.split('.')
    return segments.indices.reversed().map { segments.drop(it).joinToString(".") + tail }
}
