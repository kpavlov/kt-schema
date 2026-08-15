package me.kpavlov.kt.schema.apt.integration.type;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;

/**
 * Verifies the kt-schema-apt processor generates a JSON Schema resource for a Java
 * record containing BigInteger and BigDecimal fields.
 */
class PaymentSchemaTest {

    private static final String RESOURCE_PATH =
            "META-INF/kt-schema/schemas/me/kpavlov/kt/schema/apt/integration/type/Payment.json";

    @Test
    void shouldGenerateCompleteSchemaWithBigIntegerAndBigDecimal() throws IOException {
        // language=json
        assertThatJson(readGeneratedSchema()).isEqualTo("""
                {
                  "$id": "me.kpavlov.kt.schema.apt.integration.type.Payment",
                  "$schema": "https://json-schema.org/draft/2020-12/schema",
                  "type": "object",
                  "properties": {
                    "quantity": {
                      "type": "integer",
                      "description": "Quantity of items"
                    },
                    "amount": {
                      "type": "number",
                      "description": "Total amount of payment"
                    }
                  },
                  "required": ["quantity", "amount"],
                  "additionalProperties": false,
                  "description": "A payment with BigInteger quantity and BigDecimal amount."
                }
                """);
    }

    private static String readGeneratedSchema() throws IOException {
        try (InputStream input = PaymentSchemaTest.class.getClassLoader().getResourceAsStream(RESOURCE_PATH)) {
            if (input == null) {
                throw new IllegalStateException("Missing generated schema resource: " + RESOURCE_PATH);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
