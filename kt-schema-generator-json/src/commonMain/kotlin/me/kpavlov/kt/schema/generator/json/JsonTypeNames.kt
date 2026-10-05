@file:OptIn(InternalSchemaGeneratorApi::class)

package me.kpavlov.kt.schema.generator.json

import me.kpavlov.kt.schema.generator.core.InternalSchemaGeneratorApi
import me.kpavlov.kt.schema.generator.core.ir.AppliedTypeName
import me.kpavlov.kt.schema.generator.core.ir.NamedTypeNode
import me.kpavlov.kt.schema.generator.core.ir.TypeGraph
import me.kpavlov.kt.schema.generator.core.ir.TypeId
import me.kpavlov.kt.schema.generator.core.ir.parseApplied

/**
 * Resolves the JSON type name for every node id in the graph, used for `$defs` keys, `$ref`
 * targets, discriminator values and the root `$id`.
 *
 * An explicit name (a node name differing from its id, e.g. from `@JsonTypeName`) is honored
 * exactly. Names derived from the id are the full id, or with [shortNames] start as the simple
 * name and grow one package segment at a time while they collide.
 *
 * A generic application such as `pkg.Box<kotlin.String>` is named after its type arguments,
 * `Box_of_String`: `_of_` precedes the arguments, `_and_` separates them and `nullable_` marks a
 * nullable one. Arguments use simple names; a collision expands the declaration and every argument
 * by one package segment together. Without [shortNames] the declaration stays fully qualified.
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
                else -> id.nameCandidates(shortNames)
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

/** The most dotted segments of any declaration in this type. */
private fun AppliedTypeName.maxSegments(): Int =
    maxOf(declaration.count { it == '.' } + 1, arguments.maxOfOrNull { it.type.maxSegments() } ?: 0)

/** Renders with each declaration cut to its last [segments] segments; the root one stays full if [keepRoot]. */
private fun AppliedTypeName.render(
    segments: Int,
    keepRoot: Boolean,
): String {
    val name = if (keepRoot) declaration else declaration.split('.').takeLast(segments).joinToString(".")
    if (arguments.isEmpty()) return name
    return arguments.joinToString("_and_", "${name}_of_") {
        (if (it.nullable) "nullable_" else "") + it.type.render(segments, keepRoot = false)
    }
}

/**
 * Candidate names for an id, shortest first: each step grows every declaration by one package segment.
 * Without [shortNames] the root declaration stays fully qualified, so only its arguments grow.
 */
private fun TypeId.nameCandidates(shortNames: Boolean): List<String> {
    val name = parseApplied() ?: AppliedTypeName(value)
    return (1..name.maxSegments()).map { name.render(it, keepRoot = !shortNames) }.distinct()
}
