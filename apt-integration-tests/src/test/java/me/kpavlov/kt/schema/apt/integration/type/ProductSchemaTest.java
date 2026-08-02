package me.kpavlov.kt.schema.apt.integration.type;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the kt-schema-apt processor generates a JSON Schema resource for a Java
 * interface selected by the configured {@code rootPackage} compiler option.
 */
class ProductSchemaTest {

    private static final String RESOURCE_PATH =
            "META-INF/kt-schema/schemas/me/kpavlov/kt/schema/apt/integration/type/Product.json";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void shouldGenerateCompleteSchemaWithAllRequiredFields() throws IOException {
        JsonNode actual = readGeneratedSchema();

        // language=json
        JsonNode expected = MAPPER.readTree("""
                {
                  "$id": "Product",
                  "$schema": "https://json-schema.org/draft/2020-12/schema",
                  "type": "object",
                  "properties": {
                    "name": {
                      "type": "string",
                      "description": "Name of the product"
                    },
                    "price": {
                      "type": "integer",
                      "description": "Price of the product in cents"
                    }
                  },
                  "required": ["name", "price"],
                  "additionalProperties": false,
                  "description": "A product with a name and a price."
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
