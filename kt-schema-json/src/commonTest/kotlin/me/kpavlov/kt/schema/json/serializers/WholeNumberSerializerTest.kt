package me.kpavlov.kt.schema.json.serializers

import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlin.test.Test

class WholeNumberSerializerTest {
    private val cases =
        listOf(
            127.0 to "127",
            -128.0 to "-128",
            0.0 to "0",
            4294967295.0 to "4294967295",
            0.5 to "0.5",
            -2.25 to "-2.25",
        )

    @Test
    fun `should write whole values as integers and others as numbers`() {
        cases.forEach { (value, expected) ->
            Json.encodeToString(WholeNumberSerializer, value) shouldBe expected
        }
    }

    @Test
    fun `should read integers and decimals`() {
        Json.decodeFromString(WholeNumberSerializer, "127") shouldBe 127.0
        Json.decodeFromString(WholeNumberSerializer, "0.5") shouldBe 0.5
    }
}
