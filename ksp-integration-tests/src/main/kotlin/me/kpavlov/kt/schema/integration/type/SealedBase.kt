package me.kpavlov.kt.schema.integration.type

import me.kpavlov.kt.schema.Description
import me.kpavlov.kt.schema.Schema

@Schema(withSchemaObject = true)
sealed class SealedBase(
    @Description("Base property")
    val baseProp: String,
) {
    @Schema
    data class SubclassA(
        @Description("A's property")
        val propA: Int,
    ) : SealedBase("a-fixed")

    @Schema
    data object SubclassB : SealedBase("b-fixed")
}
