package me.kpavlov.kt.schema.integration.type

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.JsonClassDiscriminator
import me.kpavlov.kt.schema.Schema

// Custom discriminator property name, inherited by the nested sealed subtype.
@Schema
@OptIn(ExperimentalSerializationApi::class)
@JsonClassDiscriminator("outcome")
sealed interface DiscriminatedResponse {
    data class Ok(
        val value: String,
    ) : DiscriminatedResponse

    sealed interface Failure : DiscriminatedResponse {
        data class Timeout(
            val seconds: Int,
        ) : Failure
    }
}
