package me.kpavlov.kt.schema.integration.type

import io.kotest.assertions.json.shouldEqualJson
import kotlin.test.Test

/**
 * Tests for JacksonDefaultValueModel schema generation - default-value extraction from
 * Jackson's @JsonEnumDefaultValue (enum type default) and @JsonProperty(defaultValue = "...")
 * (property default).
 */
class JacksonDefaultValueModelSchemaTest {
    @Test
    fun `generates enum default from JsonEnumDefaultValue and property default from JsonProperty defaultValue`() {
        val schema = JacksonDefaultValueModel::class.jsonSchemaString

        // KSP always marks every property required and never shows property-level defaults
        // (JsonSchemaConfig doc: "Does not work reliably with KSP because KSP cannot detect
        // default values in the same compilation unit"). The enum's own type-level default is
        // unaffected by that, since it's emitted unconditionally on the enum's own $defs schema.
        // language=json
        schema shouldEqualJson
            $$"""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "$id": "me.kpavlov.kt.schema.integration.type.JacksonDefaultValueModel",
              "type": "object",
              "properties": {
                "severity": { "$ref": "#/$defs/me.kpavlov.kt.schema.integration.type.Severity" },
                "timeoutSeconds": { "type": "integer" }
              },
              "required": ["severity", "timeoutSeconds"],
              "additionalProperties": false,
              "$defs": {
                "me.kpavlov.kt.schema.integration.type.Severity": {
                  "type": "string",
                  "enum": ["LOW", "MEDIUM", "HIGH"],
                  "default": "MEDIUM"
                }
              }
            }
            """.trimIndent()
    }
}
