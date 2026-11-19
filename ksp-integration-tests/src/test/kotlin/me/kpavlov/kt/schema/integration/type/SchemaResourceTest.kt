package me.kpavlov.kt.schema.integration.type

import io.kotest.assertions.json.shouldEqualJson
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import me.kpavlov.kt.schema.Schema
import kotlin.test.Test

// Compiled with the lenient config, like the rest of the test source set (see build.gradle.kts)
@Schema
data class LenientNote(
    val title: String,
    val body: String? = null,
)

class NotAnnotatedNote(
    val title: String,
)

class SchemaResourceTest {
    private fun schemaResource(type: Class<*>): String? =
        Thread
            .currentThread()
            .contextClassLoader
            .getResourceAsStream("META-INF/kt-schema/schemas/${type.name.replace('.', '/')}.json")
            ?.use { it.readBytes().decodeToString() }

    @Test
    fun `should emit schema resource for top-level class`() {
        val resource = schemaResource(Person::class.java)

        resource.shouldNotBeNull { this shouldEqualJson Person::class.jsonSchemaString }
    }

    @Test
    fun `should emit schema resource for nested class under its binary name`() {
        val resource = schemaResource(Animal.Dog::class.java)

        resource.shouldNotBeNull { this shouldEqualJson Animal.Dog::class.jsonSchemaString }
    }

    @Test
    fun `should emit schema resource using the configured preset`() {
        val resource = schemaResource(LenientNote::class.java)

        // language=json
        resource.shouldNotBeNull {
            this shouldEqualJson
                $$"""
                {
                  "$id": "LenientNote",
                  "$schema": "https://json-schema.org/draft/2020-12/schema",
                  "type": "object",
                  "properties": {
                    "title": { "type": "string" },
                    "body": { "type": "string" }
                  },
                  "required": ["title"]
                }
                """.trimIndent()
        }
    }

    @Test
    fun `should not emit schema resource for non-annotated class`() {
        val resource = schemaResource(NotAnnotatedNote::class.java)

        resource.shouldBeNull()
    }
}
