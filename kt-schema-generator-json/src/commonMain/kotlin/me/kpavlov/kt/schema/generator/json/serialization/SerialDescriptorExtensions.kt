package me.kpavlov.kt.schema.generator.json.serialization

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.nonNullOriginal

private const val POLYMORPHIC_PREFIX = "kotlinx.serialization.Polymorphic<"
private const val POLYMORPHIC_SUFFIX = ">"

/**
 * Returns the base type serial name without the nullable marker, unwrapping the
 * `kotlinx.serialization.Polymorphic<Name>` wrapper for open polymorphic descriptors.
 *
 * For all other descriptor kinds, returns the serial name of [nonNullOriginal] unchanged.
 */
@OptIn(ExperimentalSerializationApi::class)
internal fun SerialDescriptor.unwrapSerialName(): String {
    val serialName = nonNullOriginal.serialName
    return if (kind is PolymorphicKind.OPEN) {
        serialName.removeSurrounding(POLYMORPHIC_PREFIX, POLYMORPHIC_SUFFIX)
    } else {
        serialName
    }
}
