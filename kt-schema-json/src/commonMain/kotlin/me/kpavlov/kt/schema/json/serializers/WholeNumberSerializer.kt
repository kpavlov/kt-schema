package me.kpavlov.kt.schema.json.serializers

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.math.abs

/**
 * Writes a [Double] without a fractional part as a JSON integer (`127` rather than `127.0`),
 * so bounds such as `minimum` read naturally; any other value is written as a plain number.
 * Reads any JSON number.
 */
internal object WholeNumberSerializer : KSerializer<Double> {
    /** Largest magnitude a double represents exactly as an integer. */
    private const val MAX_EXACT_INTEGER = 1L shl 53

    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("WholeNumber", PrimitiveKind.DOUBLE)

    override fun serialize(
        encoder: Encoder,
        value: Double,
    ) {
        val whole = value.toLong()
        if (whole.toDouble() == value && abs(whole) <= MAX_EXACT_INTEGER) {
            encoder.encodeLong(whole)
        } else {
            encoder.encodeDouble(value)
        }
    }

    override fun deserialize(decoder: Decoder): Double = decoder.decodeDouble()
}
