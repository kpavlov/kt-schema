package me.kpavlov.kt.schema.apt.integration.type;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;

/**
 * Verifies the kt-schema-apt processor generates a JSON Schema resource for a Java
 * interface selected by the configured {@code rootPackage} compiler option.
 */
class ProductSchemaTest {

    private static final String RESOURCE_PATH =
            "META-INF/kt-schema/schemas/me/kpavlov/kt/schema/apt/integration/type/Product.json";

    @Test
    void shouldGenerateCompleteSchemaWithAllRequiredFields() throws IOException {
        // language=json
        assertThatJson(readGeneratedSchema()).isEqualTo("""
                {
                  "$id": "me.kpavlov.kt.schema.apt.integration.type.Product",
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
    }

    private static String readGeneratedSchema() throws IOException {
        try (InputStream input = ProductSchemaTest.class.getClassLoader().getResourceAsStream(RESOURCE_PATH)) {
            if (input == null) {
                throw new IllegalStateException("Missing generated schema resource: " + RESOURCE_PATH);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
