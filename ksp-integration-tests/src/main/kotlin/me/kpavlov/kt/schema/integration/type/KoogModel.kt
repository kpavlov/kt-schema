package me.kpavlov.kt.schema.integration.type

import ai.koog.agents.core.tools.annotations.LLMDescription
import me.kpavlov.kt.schema.Schema

/**
 * Model with optional fields and different types
 */
@LLMDescription("A purchasable product with pricing and inventory info.")
@Schema
data class KoogModel(
    @LLMDescription("Unique identifier for the product")
    val id: Long,
    @LLMDescription("Human-readable product name")
    val name: String,
    @LLMDescription("Optional detailed description of the product")
    val description: String?,
    @LLMDescription("Unit price expressed as a decimal number")
    val price: Double,
    @LLMDescription("Whether the product is currently in stock")
    val inStock: Boolean = true,
    @LLMDescription("List of tags for categorization and search")
    val tags: List<String> = emptyList(),
)
