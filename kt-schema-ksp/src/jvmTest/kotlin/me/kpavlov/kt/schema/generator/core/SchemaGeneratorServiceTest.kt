package me.kpavlov.kt.schema.generator.core

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import org.junit.jupiter.api.Test

class SchemaGeneratorServiceTest {

    @Test
    fun `Should detect no generators by default`() {
        val generators = SchemaGeneratorService.registeredGenerators()

        generators.shouldHaveSize(0)
    }

    @Test
    fun `Should return null for unknown generator type`() {
        val generator =
            SchemaGeneratorService
                .getGenerator<Int, String>(
                    targetType = Int::class,
                    schemaType = String::class,
                )
        generator.shouldBeNull()
    }
}
