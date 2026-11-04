@file:OptIn(ExperimentalUuidApi::class)

package me.kpavlov.kt.schema.integration.type

import me.kpavlov.kt.schema.Schema
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.util.UUID
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Schema
data class FormattedStrings(
    val id: UUID,
    val uid: Uuid,
    val at: Instant,
    val day: LocalDate,
    val clock: LocalTime,
    val stamp: OffsetDateTime,
    val local: LocalDateTime,
)
