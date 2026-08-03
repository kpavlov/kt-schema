package me.kpavlov.kt.schema.apt.integration.type;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;

/**
 * Verifies the kt-schema-apt processor honors JsonTypeName/JsonProperty name overrides
 * on a Java enum, generated from the file path derived from the class's own simple name.
 */
class PrioritySchemaTest {

    private static final String RESOURCE_PATH =
            "META-INF/kt-schema/schemas/me/kpavlov/kt/schema/apt/integration/type/Priority.json";

    @Test
    void shouldGenerateEnumSchemaWithJsonTypeNameAndJsonPropertyOverrides() throws IOException {
        // language=json
        assertThatJson(readGeneratedSchema()).isEqualTo("""
                {
                  "$id": "PriorityLevel",
                  "$schema": "https://json-schema.org/draft/2020-12/schema",
                  "type": "string",
                  "enum": ["low", "medium", "high"]
                }
                """);
    }

    private static String readGeneratedSchema() throws IOException {
        try (InputStream input = PrioritySchemaTest.class.getClassLoader().getResourceAsStream(RESOURCE_PATH)) {
            if (input == null) {
                throw new IllegalStateException("Missing generated schema resource: " + RESOURCE_PATH);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
