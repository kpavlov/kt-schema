package me.kpavlov.kt.schema.apt.integration.type;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the kt-schema-apt processor generates a JSON Schema resource for a plain
 * (non-record) Java class selected by the configured {@code rootPackage} compiler option.
 */
class CompanySchemaTest {

    private static final String RESOURCE_PATH =
            "META-INF/kt-schema/schemas/me/kpavlov/kt/schema/apt/integration/type/Company.json";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void shouldGenerateCompleteSchemaWithAllRequiredFields() throws IOException {
        JsonNode actual = readGeneratedSchema();

        JsonNode expected = MAPPER.readTree("""
                {
                  "$id": "me.kpavlov.kt.schema.apt.integration.type.Company",
                  "$schema": "https://json-schema.org/draft/2020-12/schema",
                  "type": "object",
                  "properties": {
                    "name": {
                      "type": "string",
                      "description": "Name of the company"
                    },
                    "founded": {
                      "type": "integer",
                      "description": "Year the company was founded"
                    }
                  },
                  "required": ["name", "founded"],
                  "additionalProperties": false,
                  "description": "A company with a name and a founding year."
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
