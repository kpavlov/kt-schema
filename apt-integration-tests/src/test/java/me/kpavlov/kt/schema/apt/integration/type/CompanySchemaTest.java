package me.kpavlov.kt.schema.apt.integration.type;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;

/**
 * Verifies the kt-schema-apt processor generates a JSON Schema resource for a plain
 * (non-record) Java class selected by the configured {@code rootPackage} compiler option.
 */
class CompanySchemaTest {

    private static final String RESOURCE_PATH =
            "META-INF/kt-schema/schemas/me/kpavlov/kt/schema/apt/integration/type/Company.json";

    @Test
    void shouldGenerateCompleteSchemaWithAllRequiredFields() throws IOException {
        // language=json
        assertThatJson(readGeneratedSchema()).isEqualTo("""
                {
                  "$id": "me.kpavlov.kt.schema.apt.integration.type.Company",
                  "$schema": "https://json-schema.org/draft/2020-12/schema",
                  "type": "object",
                  "properties": {
                    "company_name": {
                      "type": "string",
                      "description": "Name of the company"
                    },
                    "founded": {
                      "type": "integer",
                      "description": "Year the company was founded"
                    }
                  },
                  "required": ["company_name", "founded"],
                  "additionalProperties": false,
                  "description": "A company with a name and a founding year."
                }
                """);
    }

    private static String readGeneratedSchema() throws IOException {
        try (InputStream input = CompanySchemaTest.class.getClassLoader().getResourceAsStream(RESOURCE_PATH)) {
            if (input == null) {
                throw new IllegalStateException("Missing generated schema resource: " + RESOURCE_PATH);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
