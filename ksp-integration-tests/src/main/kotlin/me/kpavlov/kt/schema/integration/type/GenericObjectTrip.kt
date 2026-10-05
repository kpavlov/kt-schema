package me.kpavlov.kt.schema.integration.type

import me.kpavlov.kt.schema.Schema

data class GenericBox<T>(
    val value: T,
)

data class GenericNode<T>(
    val value: T,
    val children: List<GenericNode<T>>,
)

@Schema
data class GenericObjectTrip(
    val text: GenericBox<String>,
    val count: GenericBox<Int>,
    val nullableText: GenericBox<String?>,
    val ids: GenericBox<List<Int>>,
)

@Schema
data class GenericObjectReversedTrip(
    val ids: GenericBox<List<Int>>,
    val nullableText: GenericBox<String?>,
    val count: GenericBox<Int>,
    val text: GenericBox<String>,
)

@Schema
data class GenericNodeTrip(
    val root: GenericNode<String>,
)

@JvmInline
value class GenericIndex<T>(
    val byKey: Map<T, GenericIndex<T>>,
)

@Schema
data class GenericIndexTrip(
    val ints: GenericIndex<Int>,
    val strings: GenericIndex<String>,
)
