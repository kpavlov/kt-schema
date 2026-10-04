@file:OptIn(ExperimentalTime::class)

package me.kpavlov.kt.schema.integration.type

import me.kpavlov.kt.schema.Schema
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@Schema
data class InstantTrip(
    val departure: Instant,
    val arrival: Instant? = null,
)
