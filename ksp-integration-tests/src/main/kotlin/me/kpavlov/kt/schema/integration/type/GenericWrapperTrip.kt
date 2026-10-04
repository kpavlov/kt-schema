package me.kpavlov.kt.schema.integration.type

import me.kpavlov.kt.schema.Schema

@JvmInline
value class Wrapper<T>(
    val value: T,
)

@JvmInline
value class ListWrapper<T>(
    val items: List<T>,
)

@Schema
data class GenericWrapperTrip(
    val intWrapper: Wrapper<Int>,
    val nullableStringWrapper: Wrapper<String?>,
    val listWrapper: ListWrapper<Int>,
)
