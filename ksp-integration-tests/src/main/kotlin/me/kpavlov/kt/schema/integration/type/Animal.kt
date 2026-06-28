package me.kpavlov.kt.schema.integration.type

import me.kpavlov.kt.schema.Description
import me.kpavlov.kt.schema.Schema

/**
 * Multicellular eukaryotic organism of the kingdom Metazoa
 */
@Description("Multicellular eukaryotic organism of the kingdom Metazoa")
@Schema
sealed class Animal {
    /**
     * Animal's name
     */
    @Description("Animal's name")
    abstract val name: String

    @Schema(withSchemaObject = true)
    data class Dog(
        @Description("Animal's name")
        override val name: String,
    ) : Animal()

    @Schema(withSchemaObject = true)
    data class Cat(
        @Description("Animal's name")
        override val name: String,
    ) : Animal()
}
