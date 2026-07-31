package me.kpavlov.kt.schema.apt.integration.type;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the kt-schema-apt processor generates a JSON Schema resource for a Java
 * record referencing a nested object via a list and a map.
 */
class CatalogSchemaTest {

    private static final String RESOURCE_PATH =
            "META-INF/kt-schema/schemas/me/kpavlov/kt/schema/apt/integration/type/Catalog.json";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void shouldGenerateCompleteSchemaWithNestedRefs() throws IOException {
        JsonNode actual = readGeneratedSchema();

        // language=json
        JsonNode expected = MAPPER.readTree("""
                {
                  "$schema": "https://json-schema.org/draft/2020-12/schema",
                  "$id": "me.kpavlov.kt.schema.apt.integration.type.Catalog",
                  "description": "A catalog of addresses.",
                  "type": "object",
                  "properties": {
                    "addresses": {
                      "type": "array",
                      "description": "Addresses in the catalog",
                      "items": {
                        "$ref": "#/$defs/me.kpavlov.kt.schema.apt.integration.type.Address"
                      }
                    },
                    "byCity": {
                      "type": "object",
                      "description": "Addresses grouped by city",
                      "additionalProperties": {
                        "$ref": "#/$defs/me.kpavlov.kt.schema.apt.integration.type.Address"
                      }
                    }
                  },
                  "additionalProperties": false,
                  "required": ["addresses", "byCity"],
                  "$defs": {
                    "me.kpavlov.kt.schema.apt.integration.type.Address": {
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
                      "required": ["city", "street"],
                      "additionalProperties": false
                    }
                  }
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
