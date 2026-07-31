package me.kpavlov.kt.schema.integration.type

import com.fasterxml.jackson.annotation.JsonClassDescription
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonPropertyDescription
import me.kpavlov.kt.schema.Schema

/**
 * Model with Jackson @JsonClassDescription, @JsonPropertyDescription, @JsonProperty
 * and @JsonIgnore annotations to test description and name extraction.
 */
@JsonClassDescription("A purchasable product using Jackson annotations.")
@Schema
data class JacksonModel(
    @JsonProperty("product_id")
    @JsonPropertyDescription("Unique identifier for the product")
    val id: Long,
    @JsonProperty("display_name")
    @JsonPropertyDescription("Human-readable product name")
    val name: String,
    @JsonPropertyDescription("Optional detailed description of the product")
    val description: String?,
    @JsonPropertyDescription("Unit price expressed as a decimal number")
    val price: Double,
    @JsonPropertyDescription("Whether the product is currently in stock")
    val inStock: Boolean = true,
    @JsonPropertyDescription("List of tags for categorization and search")
    val tags: List<String> = emptyList(),
    @JsonIgnore
    val internalNote: String = "hidden",
    @get:JsonIgnore
    val sessionId: String = "session",
    @get:JsonProperty("stock_keeping_unit")
    @get:JsonPropertyDescription("Stock keeping unit code")
    val sku: String = "sku",
)

@Schema
@Suppress("FunctionOnlyReturningConstant")
fun createJacksonModel(): JacksonModel? = null
