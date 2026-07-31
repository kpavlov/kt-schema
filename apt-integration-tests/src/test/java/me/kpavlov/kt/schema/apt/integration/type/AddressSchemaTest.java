package me.kpavlov.kt.schema.apt.integration.type;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the kt-schema-apt processor generates a JSON Schema resource for a Java
 * record used as a nested object reference by other types.
 */
class AddressSchemaTest {

    private static final String RESOURCE_PATH =
            "META-INF/kt-schema/schemas/me/kpavlov/kt/schema/apt/integration/type/Address.json";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void shouldGenerateCompleteSchemaWithAllRequiredFields() throws IOException {
        JsonNode actual = readGeneratedSchema();

        // language=json
        JsonNode expected = MAPPER.readTree("""
                {
                  "$id": "me.kpavlov.kt.schema.apt.integration.type.Address",
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

        assertThat(actual).isEqualTo(expected);
    }

    private JsonNode readGeneratedSchema() throws IOException {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(RESOURCE_PATH)) {
            assertThat(input).as("generated schema resource: %s", RESOURCE_PATH).isNotNull();
            return MAPPER.readTree(input);
        }
    }
}
