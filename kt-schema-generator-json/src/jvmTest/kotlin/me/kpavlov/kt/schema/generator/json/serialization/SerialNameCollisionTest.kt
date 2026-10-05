package me.kpavlov.kt.schema.generator.json.serialization

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.string.shouldContain
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import me.kpavlov.kt.schema.generator.json.serialization.SerializationClassJsonSchemaGenerator.Companion.jsonSchemaOf
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import kotlin.test.Test

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SerialNameCollisionTest {
    @Serializable
    @SerialName("Shared")
    data class TextValue(
        val text: String,
    )

    @Serializable
    @SerialName("Shared")
    data class CountValue(
        val count: Int,
    )

    @Serializable
    data class Holder(
        val textValue: TextValue,
        val countValue: CountValue,
    )

    @Serializable
    data class NullableHolder(
        val required: TextValue,
        val optional: TextValue?,
    )

    @Serializable
    data class Box<T>(
        val value: T,
    )

    @Serializable
    data class BoxHolder(
        val text: Box<String>,
        val number: Box<Int>,
    )

    @Serializable
    @SerialName("Palette")
    enum class Color { RED, GREEN }

    @Serializable
    @SerialName("Palette")
    enum class Shade { LIGHT, DARK }

    @Serializable
    data class EnumHolder(
        val color: Color,
        val shade: Shade,
    )

    @Serializable
    @SerialName("Mixed")
    enum class MixedEnum { ONLY }

    @Serializable
    @SerialName("Mixed")
    data class MixedClass(
        val only: String,
    )

    @Serializable
    data class EnumThenClassHolder(
        val first: MixedEnum,
        val second: MixedClass,
    )

    @Serializable
    data class ClassThenEnumHolder(
        val first: MixedClass,
        val second: MixedEnum,
    )

    @Serializable
    @SerialName("Renamed")
    data class Named(
        val text: String,
    )

    @Serializable
    @SerialName("Renamed")
    data class Labeled(
        val label: String,
    )

    @Serializable
    data class RenamedHolder(
        val named: Named,
        val labeled: Labeled,
    )

    @Serializable
    data class ListHolder(
        val named: List<Named>,
        val labeled: List<Labeled>,
    )

    @Serializable
    data class MapHolder(
        val named: Map<String, Named>,
        val labeled: Map<String, Labeled>,
    )

    @Serializable
    @SerialName("Result")
    sealed interface OkResult {
        @Serializable
        @SerialName("ok")
        data class Ok(
            val value: String,
        ) : OkResult
    }

    @Serializable
    @SerialName("Result")
    sealed interface ErrResult {
        @Serializable
        @SerialName("err")
        data class Err(
            val message: String,
        ) : ErrResult
    }

    @Serializable
    data class ResultHolder(
        val ok: OkResult,
        val err: ErrResult,
    )

    @Serializable
    data class SealedHolder(
        val required: OkResult,
        val optional: OkResult?,
        val all: List<OkResult>,
    )

    class V1 {
        @Serializable
        @SerialName("Event")
        sealed interface Event {
            @Serializable
            @SerialName("created")
            data class Created(
                val id: String,
            ) : Event
        }
    }

    class V2 {
        @Serializable
        @SerialName("Event")
        sealed interface Event {
            @Serializable
            @SerialName("deleted")
            data class Deleted(
                val id: String,
            ) : Event
        }
    }

    @Serializable
    data class EventHolder(
        val v1: V1.Event,
        val v2: V2.Event,
    )

    @Serializable
    @SerialName("Wrapper")
    @JvmInline
    value class NamedWrapper(
        val named: Named,
    )

    @Serializable
    @SerialName("Wrapper")
    @JvmInline
    value class LabeledWrapper(
        val labeled: Labeled,
    )

    @Serializable
    data class WrapperHolder(
        val named: NamedWrapper,
        val labeled: LabeledWrapper,
    )

    fun clashingHolders(): List<Arguments> =
        listOf(
            Arguments.of("enums with different entries", EnumHolder.serializer(), "Palette"),
            Arguments.of("enum, then class", EnumThenClassHolder.serializer(), "Mixed"),
            Arguments.of("class, then enum", ClassThenEnumHolder.serializer(), "Mixed"),
            Arguments.of("classes with different property names", RenamedHolder.serializer(), "Renamed"),
            Arguments.of("classes in lists", ListHolder.serializer(), "Renamed"),
            Arguments.of("classes in maps", MapHolder.serializer(), "Renamed"),
            Arguments.of("sealed types with different subtypes", ResultHolder.serializer(), "Result"),
            Arguments.of("sealed types with equal descriptors", EventHolder.serializer(), "Event"),
            Arguments.of("value classes wrapping clashing classes", WrapperHolder.serializer(), "Renamed"),
        )

    @ParameterizedTest(name = "{0}")
    @MethodSource("clashingHolders")
    fun `should fail when distinct types share a serial name`(
        @Suppress("UNUSED_PARAMETER") title: String,
        holder: KSerializer<*>,
        serialName: String,
    ) {
        val error =
            shouldThrow<IllegalStateException> {
                SerializationClassJsonSchemaGenerator.Default.generateSchema(holder.descriptor)
            }

        error.message shouldContain "serial name '$serialName'"
    }

    @Test
    fun `should fail when distinct types share a serial name with different shapes`() {
        val error = shouldThrow<IllegalStateException> { jsonSchemaOf<Holder>() }

        error.message shouldContain "Shared"
    }

    @Test
    fun `should accept the same class used nullable and non-null`() {
        jsonSchemaOf<NullableHolder>()
    }

    @Test
    fun `should accept generic applications of the same class`() {
        jsonSchemaOf<BoxHolder>()
    }

    @Test
    fun `should accept the same sealed type used nullable, non-null and in a list`() {
        jsonSchemaOf<SealedHolder>()
    }
}
