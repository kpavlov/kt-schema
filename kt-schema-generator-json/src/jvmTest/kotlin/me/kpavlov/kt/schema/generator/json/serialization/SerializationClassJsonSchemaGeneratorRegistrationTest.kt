package me.kpavlov.kt.schema.generator.json.serialization

import io.kotest.matchers.nulls.shouldNotBeNull
import me.kpavlov.kt.schema.generator.core.SchemaGeneratorService
import me.kpavlov.kt.schema.json.JsonSchema
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlin.test.Test

class SerializationClassJsonSchemaGeneratorRegistrationTest {
    @Test
    fun `Should register SerializationClassJsonSchemaGenerator`() {
        SchemaGeneratorService
            .getGenerator(SerialDescriptor::class, JsonSchema::class)
            .shouldNotBeNull()
    }
}
