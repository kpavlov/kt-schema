package me.kpavlov.kt.schema.integration.type

import me.kpavlov.kt.schema.Description
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
value class OptionalRecursiveWrapper(
    val items: List<OptionalRecursiveWrapper>?,
)

@Schema
data class OptionalRecursiveValueClassTrip(
    val wrapper: OptionalRecursiveWrapper,
)

@Description("Nested items")
@JvmInline
value class DescribedRecursiveWrapper(
    val items: List<DescribedRecursiveWrapper>,
)

@Schema
data class DescribedRecursiveValueClassTrip(
    val wrapper: DescribedRecursiveWrapper,
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

@JvmInline
value class ChainHolder(
    val chain: Chain,
)

data class Chain(
    val value: String,
    val next: ChainHolder?,
)

@Schema
data class ValueClassCycleThroughObjectTrip(
    val holder: ChainHolder,
)

@Schema
data class NestedGenericValueClassTrip(
    val nested: Wrapper<Wrapper<Int>>,
)

@JvmInline
value class Nest<T>(
    val items: List<Nest<List<T>>>,
)

@Schema
data class PolymorphicallyRecursiveValueClassTrip(
    val nest: Nest<Int>,
)
