package me.kpavlov.kt.schema.apt.integration.type;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;

/**
 * Verifies the kt-schema-apt processor generates a JSON Schema resource for a Java
 * record used as a nested object reference by other types.
 */
class AddressSchemaTest {

    private static final String RESOURCE_PATH =
            "META-INF/kt-schema/schemas/me/kpavlov/kt/schema/apt/integration/type/Address.json";

    @Test
    void shouldGenerateCompleteSchemaWithAllRequiredFields() throws IOException {
        // language=json
        assertThatJson(readGeneratedSchema()).isEqualTo("""
                {
                  "$id": "Address",
                  "$schema": "https://json-schema.org/draft/2020-12/schema",
                  "type": "object",
                  "properties": {
                    "city": {
                      "type": "string",
                      "description": "City or town name"
                    },
                    "street": {
                      "type": "string",
                      "description": "Street name and number"
                    }
                  },
                  "additionalProperties": false,
                  "required": ["city", "street"]
                }
                """);
    }

    private static String readGeneratedSchema() throws IOException {
        try (InputStream input = AddressSchemaTest.class.getClassLoader().getResourceAsStream(RESOURCE_PATH)) {
            if (input == null) {
                throw new IllegalStateException("Missing generated schema resource: " + RESOURCE_PATH);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
