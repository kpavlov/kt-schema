package me.kpavlov.kt.schema.apt.integration.type;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;

/**
 * Verifies the kt-schema-apt processor generates a JSON Schema resource for a Java
 * record referencing a nested enum field.
 */
class TicketSchemaTest {

    private static final String RESOURCE_PATH =
            "META-INF/kt-schema/schemas/me/kpavlov/kt/schema/apt/integration/type/Ticket.json";

    @Test
    void shouldGenerateSchemaWithNestedEnumRef() throws IOException {
        // language=json
        assertThatJson(readGeneratedSchema()).isEqualTo("""
                {
                  "$schema": "https://json-schema.org/draft/2020-12/schema",
                  "$id": "me.kpavlov.kt.schema.apt.integration.type.Ticket",
                  "description": "A support ticket with a lifecycle status.",
                  "type": "object",
                  "properties": {
                    "id": {
                      "type": "string",
                      "description": "Unique ticket identifier"
                    },
                    "status": {
                      "$ref": "#/$defs/me.kpavlov.kt.schema.apt.integration.type.Status",
                      "description": "Current status of the ticket"
                    }
                  },
                  "additionalProperties": false,
                  "required": ["id", "status"],
                  "$defs": {
                    "me.kpavlov.kt.schema.apt.integration.type.Status": {
                      "type": "string",
                      "enum": ["ACTIVE", "INACTIVE", "PENDING"],
                      "description": "Current lifecycle status of an entity."
                    }
                  }
                }
                """);
    }

    private static String readGeneratedSchema() throws IOException {
        try (InputStream input = TicketSchemaTest.class.getClassLoader().getResourceAsStream(RESOURCE_PATH)) {
            if (input == null) {
                throw new IllegalStateException("Missing generated schema resource: " + RESOURCE_PATH);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
