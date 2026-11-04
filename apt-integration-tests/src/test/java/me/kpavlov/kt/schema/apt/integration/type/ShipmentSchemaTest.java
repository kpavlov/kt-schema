package me.kpavlov.kt.schema.apt.integration.type;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;

/**
 * Verifies the kt-schema-apt processor emits Jakarta Bean Validation constraints.
 */
class ShipmentSchemaTest {

    private static final String RESOURCE_PATH =
            "META-INF/kt-schema/schemas/me/kpavlov/kt/schema/apt/integration/type/Shipment.json";

    @Test
    void shouldEmitJakartaConstraints() throws IOException {
        // language=json
        assertThatJson(readGeneratedSchema()).isEqualTo("""
                {
                  "$id": "me.kpavlov.kt.schema.apt.integration.type.Shipment",
                  "$schema": "https://json-schema.org/draft/2020-12/schema",
                  "type": "object",
                  "properties": {
                    "quantity": { "type": "integer", "minimum": 1, "maximum": 100 },
                    "price": { "type": "number", "minimum": 0.01, "exclusiveMaximum": 1000 },
                    "weight": { "type": "integer", "exclusiveMinimum": 0 },
                    "discount": { "type": "integer", "minimum": 0 },
                    "debit": { "type": "integer", "exclusiveMaximum": 0 },
                    "credit": { "type": "integer", "maximum": 0 },
                    "name": { "type": "string", "minLength": 2, "maxLength": 50 },
                    "tags": { "type": "array", "items": { "type": "string" }, "minItems": 1 },
                    "note": { "type": "string", "minLength": 1 },
                    "currency": { "type": "string", "pattern": "[A-Z]{3}" },
                    "contact": { "type": "string", "format": "email" },
                    "attributes": { "type": "object", "additionalProperties": { "type": "string" }, "maxProperties": 5 },
                    "level": { "type": "integer", "minimum": 0, "maximum": 127 }
                  },
                  "required": [
                    "quantity", "price", "weight", "discount", "debit", "credit", "name", "tags", "note",
                    "currency", "contact", "attributes", "level"
                  ],
                  "additionalProperties": false
                }
                """);
    }

    private static String readGeneratedSchema() throws IOException {
        try (InputStream input = ShipmentSchemaTest.class.getClassLoader().getResourceAsStream(RESOURCE_PATH)) {
            if (input == null) {
                throw new IllegalStateException("Missing generated schema resource: " + RESOURCE_PATH);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
