package me.kpavlov.kt.schema.integration.type

import me.kpavlov.kt.schema.Schema

@JvmInline
value class RecursiveWrapper(
    val items: List<RecursiveWrapper>,
)

@Schema
data class RecursiveValueClassTrip(
    val wrapper: RecursiveWrapper,
)

@JvmInline
value class NullableRecursiveWrapper(
    val items: List<NullableRecursiveWrapper?>,
)

@Schema
data class NullableRecursiveValueClassTrip(
    val wrapper: NullableRecursiveWrapper,
)

@JvmInline
value class Ping(
    val pongs: List<Pong>,
)

@JvmInline
value class Pong(
    val pings: List<Ping>,
)

@Schema
data class MutuallyRecursiveValueClassTrip(
    val ping: Ping,
)
