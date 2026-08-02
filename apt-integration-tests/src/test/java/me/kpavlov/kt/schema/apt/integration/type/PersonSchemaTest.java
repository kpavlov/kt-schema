package me.kpavlov.kt.schema.apt.integration.type;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the kt-schema-apt processor generates a JSON Schema resource for a Java
 * record selected by the configured {@code rootPackage} compiler option.
 */
class PersonSchemaTest {

    private static final String RESOURCE_PATH =
            "META-INF/kt-schema/schemas/me/kpavlov/kt/schema/apt/integration/type/Person.json";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void shouldGenerateCompleteSchemaWithAllRequiredFields() throws IOException {
        JsonNode actual = readGeneratedSchema();

        // language=json
        JsonNode expected = MAPPER.readTree("""
                {
                  "$id": "Person",
                  "$schema": "https://json-schema.org/draft/2020-12/schema",
                  "type": "object",
                  "properties": {
                    "given_name": {
                      "type": "string",
                      "description": "Given name of the person"
                    },
                    "lastName": {
                      "type": "string",
                      "description": "Family name of the person"
                    },
                    "age": {
                      "type": ["integer","null"],
                      "description": "Age of the person in years"
                    }
                  },
                  "required": ["given_name", "lastName", "age"],
                  "additionalProperties": false,
                  "description": "A person with a first and last name and age."
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
