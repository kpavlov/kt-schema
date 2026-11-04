@file:OptIn(ExperimentalUuidApi::class, ExperimentalTime::class)

package me.kpavlov.kt.schema.test.fixtures

import kotlinx.serialization.Serializable
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** Kotlin built-in types with intrinsic schema constraints (ranges, lengths, formats), plus an unconstrained Int. */
@Serializable
data class BuiltInTypes(
    val tiny: Byte,
    val small: Short,
    val letter: Char,
    val ubyte: UByte,
    val ushort: UShort,
    val uint: UInt,
    val ulong: ULong,
    val optional: Short?,
    val uid: Uuid,
    val at: Instant,
    val count: Int,
)
