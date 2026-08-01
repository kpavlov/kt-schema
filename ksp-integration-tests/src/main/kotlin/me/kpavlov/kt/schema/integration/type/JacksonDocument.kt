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

@Schema
sealed interface JacksonMemo {
    val id: String
}

@Schema
class JacksonNote(
    val body: String,
) : JacksonMemo {
    @get:JsonProperty("memo_id")
    override val id: String = "n-1"
}

@Schema
sealed class JacksonRecord {
    abstract val id: String
}

@Schema
data class JacksonEntry(
    @get:JsonProperty("entry_id") override val id: String,
    val label: String,
) : JacksonRecord()
