package me.kpavlov.kt.schema.apt.integration.type;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;

/**
 * Verifies the kt-schema-apt processor emits built-in bounds for byte/short/char and
 * {@code format} for UUID and date types.
 */
class MeasurementSchemaTest {

    private static final String RESOURCE_PATH =
            "META-INF/kt-schema/schemas/me/kpavlov/kt/schema/apt/integration/type/Measurement.json";

    @Test
    void shouldEmitBuiltInBoundsAndFormats() throws IOException {
        // language=json
        assertThatJson(readGeneratedSchema()).isEqualTo("""
                {
                  "$id": "me.kpavlov.kt.schema.apt.integration.type.Measurement",
                  "$schema": "https://json-schema.org/draft/2020-12/schema",
                  "type": "object",
                  "properties": {
                    "level": { "type": "integer", "minimum": -128, "maximum": 127 },
                    "code": { "type": "integer", "minimum": -32768, "maximum": 32767 },
                    "grade": { "type": "string", "minLength": 1, "maxLength": 1 },
                    "boxedLevel": { "type": "integer", "minimum": -128, "maximum": 127 },
                    "boxedCode": { "type": "integer", "minimum": -32768, "maximum": 32767 },
                    "boxedGrade": { "type": "string", "minLength": 1, "maxLength": 1 },
                    "id": { "type": "string", "format": "uuid" },
                    "at": { "type": "string", "format": "date-time" },
                    "day": { "type": "string", "format": "date" },
                    "clock": { "type": "string", "format": "time" },
                    "local": { "type": "string" }
                  },
                  "required": [
                    "level", "code", "grade", "boxedLevel", "boxedCode", "boxedGrade",
                    "id", "at", "day", "clock", "local"
                  ],
                  "additionalProperties": false
                }
                """);
    }

    private static String readGeneratedSchema() throws IOException {
        try (InputStream input = MeasurementSchemaTest.class.getClassLoader().getResourceAsStream(RESOURCE_PATH)) {
            if (input == null) {
                throw new IllegalStateException("Missing generated schema resource: " + RESOURCE_PATH);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
