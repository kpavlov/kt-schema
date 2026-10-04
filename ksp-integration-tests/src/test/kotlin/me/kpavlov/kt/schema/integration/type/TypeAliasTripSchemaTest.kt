package me.kpavlov.kt.schema.integration.type

import io.kotest.assertions.json.shouldEqualJson
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.jsonObject
import me.kpavlov.kt.schema.generator.json.ReflectionClassJsonSchemaGenerator
import me.kpavlov.kt.schema.json.encodeToJsonObject
import kotlin.test.Test

class TypeAliasTripSchemaTest {
    // language=json
    private val typeAliasSchema =
        $$"""
        {
          "$schema": "https://json-schema.org/draft/2020-12/schema",
          "$id": "me.kpavlov.kt.schema.integration.type.TypeAliasTrip",
          "type": "object",
          "properties": {
            "id": { "type": "string" },
            "optionalId": { "type": ["string", "null"] },
            "tags": { "type": "array", "items": { "type": "integer" } },
            "scoresByName": { "type": "object", "additionalProperties": { "type": "integer" } },
            "nestedScores": { "type": "object", "additionalProperties": { "type": "integer" } },
            "nullableScores": { "type": "object", "additionalProperties": { "type": ["integer", "null"] } }
          },
          "required": ["id", "optionalId", "tags", "scoresByName", "nestedScores", "nullableScores"],
          "additionalProperties": false
        }
        """.trimIndent()

    // language=json
    private val jvmCollectionsSchema =
        $$"""
        {
          "$schema": "https://json-schema.org/draft/2020-12/schema",
          "$id": "me.kpavlov.kt.schema.integration.type.JvmCollectionsTrip",
          "type": "object",
          "properties": {
            "scores": { "type": "object", "additionalProperties": { "type": "integer" } },
            "names": { "type": "array", "items": { "type": "string" } },
            "unique": { "type": "array", "items": { "type": "integer" } },
            "hashed": { "type": "array", "items": { "type": "string" } },
            "linked": { "type": "object", "additionalProperties": { "type": "integer" } },
            "initial": { "type": "string" }
          },
          "required": ["scores", "names", "unique", "hashed", "linked", "initial"],
          "additionalProperties": false
        }
        """.trimIndent()

    @Test
    fun `resolves typealiases to the type they stand for in jsonSchemaString`() {
        TypeAliasTrip::class.jsonSchemaString shouldEqualJson typeAliasSchema
    }

    @Test
    fun `resolves typealiases to the type they stand for in jsonSchema`() {
        TypeAliasTrip::class.jsonSchema.toString() shouldEqualJson typeAliasSchema
    }

    @Test
    fun `maps JVM collection classes and Char like the reflection generator in jsonSchemaString`() {
        JvmCollectionsTrip::class.jsonSchemaString shouldEqualJson jvmCollectionsSchema
    }

    @Test
    fun `maps JVM collection classes and Char like the reflection generator in jsonSchema`() {
        JvmCollectionsTrip::class.jsonSchema.toString() shouldEqualJson jvmCollectionsSchema
    }

    @Test
    fun `produces the same schemas as the reflection generator`() {
        val generator = ReflectionClassJsonSchemaGenerator()

        TypeAliasTrip::class.jsonSchema.toString() shouldEqualJson
            generator.generateSchema(TypeAliasTrip::class).encodeToJsonObject().toString()
        JvmCollectionsTrip::class.jsonSchema.toString() shouldEqualJson
            generator.generateSchema(JvmCollectionsTrip::class).encodeToJsonObject().toString()
    }

    @Test
    fun `resolves a star-projected alias argument without recursing`() {
        val nested = StarProjectedAliasTrip::class.jsonSchema.getValue("properties").jsonObject.getValue("nested")

        nested.toString() shouldEqualJson
            """{ "type": "array", "items": { "type": "array" } }"""
    }

    @Test
    fun `resolves a function type alias like the function type it stands for`() {
        val aliased = FunctionTypeAliasTrip::class.jsonSchema.getValue("properties").jsonObject.getValue("callback")
        val direct = DirectFunctionTypeTrip::class.jsonSchema.getValue("properties").jsonObject.getValue("callback")

        aliased shouldBe direct
    }

    @Test
    fun `keeps object schemas for platform types that merely implement Iterable`() {
        val properties = PlatformTypesTrip::class.jsonSchema.getValue("properties").jsonObject

        properties.getValue("path").jsonObject.keys shouldContain $$"$ref"
        properties.getValue("range").jsonObject.keys shouldContain $$"$ref"
    }

    @Test
    fun `explicit withSchemaObject=false overrides the global processor option`() {
        val generatedMethods =
            Class
                .forName("me.kpavlov.kt.schema.integration.type.AnnotationOptOutTripSchemaExtensionsKt")
                .declaredMethods
                .map { it.name }

        generatedMethods shouldContain "getJsonSchemaString"
        generatedMethods shouldNotContain "getJsonSchema"
    }
}
