package me.kpavlov.kt.schema.generator.json.serialization

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.string.shouldContain
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import me.kpavlov.kt.schema.generator.json.serialization.SerializationClassJsonSchemaGenerator.Companion.jsonSchemaOf
import kotlin.test.Test

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
}
