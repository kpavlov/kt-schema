package me.kpavlov.kt.schema.integration.type

import com.fasterxml.jackson.annotation.JsonProperty
import me.kpavlov.kt.schema.Schema

@Schema
sealed class JacksonDocument {
    @get:JsonProperty("document_id")
    abstract val id: String
}

@Schema
class JacksonReport(
    val body: String,
) : JacksonDocument() {
    override val id: String = "r-1"
}
