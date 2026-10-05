package me.kpavlov.kt.schema.generator.json.serialization

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.descriptors.nonNullOriginal
import me.kpavlov.kt.schema.generator.core.InternalSchemaGeneratorApi
import me.kpavlov.kt.schema.generator.core.ir.Discriminator
import me.kpavlov.kt.schema.generator.core.ir.ListNode
import me.kpavlov.kt.schema.generator.core.ir.MapNode
import me.kpavlov.kt.schema.generator.core.ir.ObjectNode
import me.kpavlov.kt.schema.generator.core.ir.PolymorphicNode
import me.kpavlov.kt.schema.generator.core.ir.SubtypeRef
import me.kpavlov.kt.schema.generator.core.ir.TypeGraph
import me.kpavlov.kt.schema.generator.core.ir.TypeId
import me.kpavlov.kt.schema.generator.core.ir.TypeNode
import me.kpavlov.kt.schema.generator.core.ir.TypeRef
import me.kpavlov.kt.schema.generator.core.ir.appliedTypeId

/**
 * Tells apart the applications of generic classes met while introspecting descriptors.
 *
 * A descriptor doesn't say which of its elements are type arguments, and all applications of a class share its serial
 * name. Descriptors of different applications are not equal though, as a generated descriptor's equality includes its
 * type parameters. So [idOf] hands out a provisional id per distinct descriptor, and [finalIds] names the classes with
 * more than one application after the elements that differ between them, e.g. `pkg.Box<kotlin.String>`. A class used
 * once keeps its serial name, so schemas without generic applications don't change.
 *
 * Limitations: generic sealed hierarchies are not told apart, and applications that differ only below
 * [MAX_LABEL_DEPTH] levels of nested generic classes are numbered in the order they were met.
 */
@OptIn(ExperimentalSerializationApi::class, InternalSchemaGeneratorApi::class)
internal class GenericApplications {
    private val applications = mutableMapOf<String, MutableList<SerialDescriptor>>()

    /** Returns the provisional id of [descriptor]: its serial name for the first application met, else a unique one. */
    fun idOf(descriptor: SerialDescriptor): TypeId {
        val name = descriptor.unwrapSerialName()
        val known = applications.getOrPut(name) { mutableListOf() }
        val original = descriptor.nonNullOriginal
        val index = known.indexOf(original).takeIf { it >= 0 } ?: known.size.also { known += original }
        return provisionalId(name, index)
    }

    /**
     * Returns the final id of every provisional id of a class with more than one application, which makes
     * the result independent of the order the applications were met in.
     */
    fun finalIds(): Map<TypeId, TypeId> =
        applications
            .filterValues { it.size > 1 }
            .flatMap { (name, group) ->
                labels(name, group).mapIndexed { index, label -> provisionalId(name, index) to TypeId(label) }
            }.toMap()

    /**
     * Labels each application of [name] after the elements whose signatures differ between [group]'s members,
     * which are the ones typed by a type parameter. Falls back to all elements if none differs.
     *
     * Applications that still coincide are numbered in the order they were met. That only happens when the
     * difference is hidden infinitely deep, as in a polymorphically recursive class.
     */
    private fun labels(
        name: String,
        group: List<SerialDescriptor>,
    ): List<String> {
        val signatures = group.map { descriptor -> signatures(descriptor, listOf(descriptor)) }
        val elements = signatures.first().indices
        val varying = elements.filter { index -> signatures.map { it[index] }.distinct().size > 1 }
        val named = signatures.map { signature -> varying.ifEmpty { elements.toList() }.map(signature::get) }
        val numbers = mutableMapOf<List<String>, Int>()
        return named.map { arguments ->
            if (named.count { it == arguments } == 1) {
                appliedTypeId(name, arguments).value
            } else {
                val number = (numbers[arguments] ?: 0) + 1
                numbers[arguments] = number
                appliedTypeId(name, arguments + number.toString()).value
            }
        }
    }

    private fun provisionalId(
        name: String,
        index: Int,
    ) = TypeId(if (index == 0) name else "$name#$index")

    private fun signatures(
        descriptor: SerialDescriptor,
        stack: List<SerialDescriptor>,
    ): List<String> = List(descriptor.elementsCount) { signature(descriptor.getElementDescriptor(it), stack) }

    private fun label(
        descriptor: SerialDescriptor,
        stack: List<SerialDescriptor>,
    ): String = appliedTypeId(descriptor.unwrapSerialName(), signatures(descriptor, stack)).value

    /** Names [descriptor] for use in a label; [stack] holds the labels being built, to cut recursive types. */
    private fun signature(
        descriptor: SerialDescriptor,
        stack: List<SerialDescriptor>,
    ): String {
        val original = descriptor.nonNullOriginal
        val name = original.unwrapSerialName()
        val base =
            when {
                original.kind == StructureKind.LIST -> {
                    appliedTypeId(LIST, listOf(signature(original.getElementDescriptor(0), stack))).value
                }

                original.kind == StructureKind.MAP -> {
                    appliedTypeId(MAP, List(2) { signature(original.getElementDescriptor(it), stack) }).value
                }

                applications[name].orEmpty().size > 1 && stack.size < MAX_LABEL_DEPTH && original !in stack -> {
                    label(original, stack + original)
                }

                else -> {
                    name
                }
            }
        return if (descriptor.isNullable) "$base?" else base
    }

    private companion object {
        const val LIST = "kotlin.collections.List"
        const val MAP = "kotlin.collections.Map"

        /** Bounds the nesting of a label: polymorphically recursive classes have descriptors without end. */
        const val MAX_LABEL_DEPTH = 4
    }
}

/** Returns this graph with the ids in [mapping] replaced, including the names of the objects they identify. */
internal fun TypeGraph.renameIds(mapping: Map<TypeId, TypeId>): TypeGraph =
    if (mapping.isEmpty()) this else IdRenamer(mapping).rename(this)

private class IdRenamer(
    private val mapping: Map<TypeId, TypeId>,
) {
    fun rename(graph: TypeGraph): TypeGraph =
        TypeGraph(
            root = ref(graph.root),
            nodes =
                graph.nodes.entries.associate { (id, node) ->
                    val renamed = id.renamed()
                    renamed to if (node is ObjectNode && id in mapping) named(node, renamed) else node(node)
                },
        )

    private fun TypeId.renamed(): TypeId = mapping[this] ?: this

    private fun named(
        node: ObjectNode,
        id: TypeId,
    ): TypeNode = (node(node) as ObjectNode).copy(name = id.value)

    private fun ref(type: TypeRef): TypeRef =
        when (type) {
            is TypeRef.Ref -> type.copy(id = type.id.renamed())
            is TypeRef.Inline -> type.copy(node = node(type.node))
        }

    private fun node(node: TypeNode): TypeNode =
        when (node) {
            is ObjectNode -> node.copy(properties = node.properties.map { it.copy(type = ref(it.type)) })
            is ListNode -> node.copy(element = ref(node.element))
            is MapNode -> node.copy(key = ref(node.key), value = ref(node.value))
            is PolymorphicNode ->
                node.copy(subtypes = node.subtypes.map { it.renamed() }, discriminator = node.discriminator.renamed())
            else -> node
        }

    private fun SubtypeRef.renamed() =
        copy(id = id.renamed(), ref = ref.copy(id = ref.id.renamed()))

    private fun Discriminator.renamed() = copy(mapping = mapping?.mapValues { it.value.renamed() })
}
