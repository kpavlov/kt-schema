package me.kpavlov.kt.schema.generator.json

import com.fasterxml.jackson.annotation.JsonTypeName
import io.kotest.assertions.json.shouldEqualJson
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.string.shouldStartWith
import me.kpavlov.kt.schema.generator.core.SchemaGeneratorService
import me.kpavlov.kt.schema.json.JsonSchema
import me.kpavlov.kt.schema.json.encodeToString
import kotlin.reflect.KClass
import kotlin.test.Test

class SharedTypeNameTest {
    @JsonTypeName("Shared")
    data class TextValue(
        val text: String,
    )

    @JsonTypeName("Shared")
    data class CountValue(
        val count: Int,
    )

    data class Payload(
        val textValue: TextValue,
        val countValue: CountValue,
    )

    data class ReversedPayload(
        val countValue: CountValue,
        val textValue: TextValue,
    )

    sealed interface ResultA {
        @JsonTypeName("Success")
        data class Success(
            val value: String,
        ) : ResultA
    }

    sealed interface ResultB {
        @JsonTypeName("Success")
        data class Success(
            val data: Int,
        ) : ResultB
    }

    data class Results(
        val a: ResultA,
        val b: ResultB,
    )

    sealed interface Ambiguous {
        @JsonTypeName("Same")
        data class B(
            val x: String,
        ) : Ambiguous

        @JsonTypeName("Same")
        data class C(
            val y: Int,
        ) : Ambiguous
    }

    @JsonTypeName("Node")
    data class TreeNode(
        val children: List<TreeNode>,
    )

    private val generator =
        requireNotNull(SchemaGeneratorService.getGenerator(KClass::class, JsonSchema::class))

    private val pkg = "me.kpavlov.kt.schema.generator.json.SharedTypeNameTest"

    @Test
    fun `should fail when two classes share a name override`() {
        val error = shouldThrow<IllegalStateException> { generator.generateSchema(Payload::class) }

        error.message shouldStartWith
            "Type name 'Shared' is used by multiple declarations: $pkg.CountValue, $pkg.TextValue."
    }

    @Test
    fun `should fail with the same message regardless of property order`() {
        val error = shouldThrow<IllegalStateException> { generator.generateSchema(ReversedPayload::class) }

        error.message shouldStartWith
            "Type name 'Shared' is used by multiple declarations: $pkg.CountValue, $pkg.TextValue."
    }

    @Test
    fun `should fail when subtypes of different hierarchies share a name override`() {
        val error = shouldThrow<IllegalStateException> { generator.generateSchema(Results::class) }

        error.message shouldStartWith
            "Type name 'Success' is used by multiple declarations: $pkg.ResultA.Success, $pkg.ResultB.Success."
    }

    @Test
    fun `should fail when two subtypes of one sealed type share a discriminator value`() {
        val error = shouldThrow<IllegalStateException> { generator.generateSchema(Ambiguous::class) }

        error.message shouldStartWith
            "Type name 'Same' is used by multiple declarations: $pkg.Ambiguous.B, $pkg.Ambiguous.C."
    }

    @Test
    fun `should resolve every ref of a recursive type with a name override`() {
        val schema = generator.generateSchema(TreeNode::class).encodeToString()

        schema shouldEqualJson
            // language=json
            $$"""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "$id": "Node",
              "type": "object",
              "properties": {
                "children": { "type": "array", "items": { "$ref": "#/$defs/Node" } }
              },
              "required": ["children"],
              "additionalProperties": false,
              "$defs": {
                "Node": {
                  "type": "object",
                  "properties": {
                    "children": { "type": "array", "items": { "$ref": "#/$defs/Node" } }
                  },
                  "required": ["children"],
                  "additionalProperties": false
                }
              }
            }
            """.trimIndent()
    }
}
