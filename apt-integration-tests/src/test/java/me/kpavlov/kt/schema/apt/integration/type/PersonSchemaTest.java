package me.kpavlov.kt.schema.apt.integration.type;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;

/**
 * Verifies the kt-schema-apt processor generates a JSON Schema resource for a Java
 * record selected by the configured {@code rootPackage} compiler option.
 */
class PersonSchemaTest {

    private static final String RESOURCE_PATH =
            "META-INF/kt-schema/schemas/me/kpavlov/kt/schema/apt/integration/type/Person.json";

    @Test
    void shouldGenerateCompleteSchemaWithAllRequiredFields() throws IOException {
        // language=json
        assertThatJson(readGeneratedSchema()).isEqualTo("""
                {
                  "$id": "me.kpavlov.kt.schema.apt.integration.type.Person",
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
    }

    private static String readGeneratedSchema() throws IOException {
        try (InputStream input = PersonSchemaTest.class.getClassLoader().getResourceAsStream(RESOURCE_PATH)) {
            if (input == null) {
                throw new IllegalStateException("Missing generated schema resource: " + RESOURCE_PATH);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
