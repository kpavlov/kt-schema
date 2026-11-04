package me.kpavlov.kt.schema.integration.type

import me.kpavlov.kt.schema.Schema

@Schema
data class NumericRanges(
    val tiny: Byte,
    val small: Short,
    val letter: Char,
    val ubyte: UByte,
    val ushort: UShort,
    val uint: UInt,
    val ulong: ULong,
    val count: Int,
    val raw: ByteArray,
    val optional: Short? = null,
)
