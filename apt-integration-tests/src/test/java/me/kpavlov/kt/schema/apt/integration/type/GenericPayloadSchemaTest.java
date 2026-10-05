package me.kpavlov.kt.schema.apt.integration.type;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;

/**
 * Verifies the kt-schema-apt processor keeps a definition per applied type argument of a generic
 * record and a recursive generic class.
 */
class GenericPayloadSchemaTest {

    private static final String RESOURCE_PATH =
        "META-INF/kt-schema/schemas/me/kpavlov/kt/schema/apt/integration/type/GenericPayload.json";

    @Test
    void shouldResolveTypeArgumentsPerApplication() throws IOException {
        // language=json
        assertThatJson(readGeneratedSchema()).isEqualTo("""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "$id": "me.kpavlov.kt.schema.apt.integration.type.GenericPayload",
              "type": "object",
              "properties": {
                "text": { "$ref": "#/$defs/me.kpavlov.kt.schema.apt.integration.type.GenericBox_of_String" },
                "count": { "$ref": "#/$defs/me.kpavlov.kt.schema.apt.integration.type.GenericBox_of_Integer" },
                "tree": { "$ref": "#/$defs/me.kpavlov.kt.schema.apt.integration.type.GenericNode_of_String" }
              },
              "additionalProperties": false,
              "required": ["text", "count", "tree"],
              "$defs": {
                "me.kpavlov.kt.schema.apt.integration.type.GenericBox_of_String": {
                  "type": "object",
                  "properties": { "value": { "type": "string" } },
                  "required": ["value"],
                  "additionalProperties": false
                },
                "me.kpavlov.kt.schema.apt.integration.type.GenericBox_of_Integer": {
                  "type": "object",
                  "properties": { "value": { "type": "integer" } },
                  "required": ["value"],
                  "additionalProperties": false
                },
                "me.kpavlov.kt.schema.apt.integration.type.GenericNode_of_String": {
                  "type": "object",
                  "properties": {
                    "value": { "type": "string" },
                    "children": {
                      "type": "array",
                      "items": { "$ref": "#/$defs/me.kpavlov.kt.schema.apt.integration.type.GenericNode_of_String" }
                    }
                  },
                  "required": ["value", "children"],
                  "additionalProperties": false
                }
              }
            }
            """);
    }

    private static String readGeneratedSchema() throws IOException {
        try (InputStream input = GenericPayloadSchemaTest.class.getClassLoader().getResourceAsStream(RESOURCE_PATH)) {
            if (input == null) {
                throw new IllegalStateException("Missing generated schema resource: " + RESOURCE_PATH);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
