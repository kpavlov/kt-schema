package me.kpavlov.kt.schema.integration.type

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue
import com.fasterxml.jackson.annotation.JsonProperty
import me.kpavlov.kt.schema.Schema

// Enum class with a Jackson @JsonEnumDefaultValue-annotated constant.
@Schema
enum class Severity {
    LOW,

    @JsonEnumDefaultValue
    MEDIUM,
    HIGH,
}

// Model exercising default-value extraction from Jackson annotations: an enum type default
// (@JsonEnumDefaultValue) and a property default (@JsonProperty(defaultValue = "...")).
@Schema
data class JacksonDefaultValueModel(
    val severity: Severity,
    @JsonProperty(defaultValue = "30")
    val timeoutSeconds: Int,
)
