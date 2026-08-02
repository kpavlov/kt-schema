package com.example.orgchart;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;

/**
 * Verifies the schemas generated at compile time by {@code kt-schema-apt} from the
 * Jackson-annotated {@code orgchart} domain model. The annotation processor writes one
 * JSON Schema resource per type under {@code META-INF/kt-schema/schemas/}.
 */
class OrgChartSchemaTest {

    private static final String RESOURCE_ROOT = "META-INF/kt-schema/schemas/";

    @Test
    void shouldGenerateSchemaForComplexGraph() throws IOException {
        // language=json
        assertThatJson(schemaResource(Department.class)).isEqualTo("""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "$id": "com.example.orgchart.Department",
              "description": "A department in the organization.",
              "type": "object",
              "properties": {
                "name": {
                  "type": "string",
                  "description": "Name of the department"
                },
                "costCenter": {
                  "type": "string",
                  "description": "Cost center code"
                },
                "head": {
                  "$ref": "#/$defs/com.example.orgchart.Employee",
                  "description": "Employee heading the department"
                }
              },
              "additionalProperties": false,
              "required": ["name", "costCenter", "head"],
              "$defs": {
                "com.example.orgchart.Employee": {
                  "type": "object",
                  "description": "An employee of the organization.",
                  "properties": {
                    "employee_id": {
                      "type": "string",
                      "description": "Unique identifier of the employee"
                    },
                    "name": {
                      "type": "string",
                      "description": "Full name of the employee"
                    },
                    "active": {
                      "type": "boolean",
                      "description": "Whether the employee is currently active"
                    },
                    "contactInfo": {
                      "$ref": "#/$defs/com.example.orgchart.ContactInfo",
                      "description": "Contact details of the employee"
                    },
                    "compensation": {
                      "$ref": "#/$defs/com.example.orgchart.Compensation",
                      "description": "Compensation package of the employee"
                    },
                    "department": {
                      "$ref": "#/$defs/com.example.orgchart.Department",
                      "description": "Department the employee belongs to"
                    },
                    "manager": {
                      "$ref": "#/$defs/com.example.orgchart.Employee",
                      "description": "Direct reporting manager of the employee"
                    },
                    "reports": {
                      "type": "array",
                      "description": "Employees directly reporting to this employee",
                      "items": {
                        "$ref": "#/$defs/com.example.orgchart.Employee"
                      }
                    },
                    "skills": {
                      "type": "array",
                      "description": "Skills of the employee",
                      "items": {
                        "type": "string"
                      }
                    },
                    "attributes": {
                      "type": "object",
                      "description": "Free-form employee attributes",
                      "additionalProperties": {
                        "type": "string"
                      }
                    }
                  },
                  "required": [
                    "employee_id",
                    "name",
                    "active",
                    "contactInfo",
                    "compensation",
                    "department",
                    "manager",
                    "reports",
                    "skills",
                    "attributes"
                  ],
                  "additionalProperties": false
                },
                "com.example.orgchart.ContactInfo": {
                  "type": "object",
                  "description": "Contact information of an employee.",
                  "properties": {
                    "email": {
                      "type": "string",
                      "description": "Primary email address"
                    },
                    "phone": {
                      "type": "string",
                      "description": "Phone number in E.164 format"
                    },
                    "addresses": {
                      "type": "array",
                      "description": "Known postal addresses of the employee",
                      "items": {
                        "$ref": "#/$defs/com.example.orgchart.Address"
                      }
                    }
                  },
                  "required": ["email", "phone", "addresses"],
                  "additionalProperties": false
                },
                "com.example.orgchart.Address": {
                  "type": "object",
                  "description": "A postal address.",
                  "properties": {
                    "street": {
                      "type": "string",
                      "description": "Street and house number"
                    },
                    "city": {
                      "type": "string",
                      "description": "City or town"
                    },
                    "country": {
                      "type": "string",
                      "description": "Country"
                    },
                    "zipCode": {
                      "type": "string",
                      "description": "Postal code"
                    }
                  },
                  "required": ["street", "city", "country", "zipCode"],
                  "additionalProperties": false
                },
                "com.example.orgchart.Compensation": {
                  "type": "object",
                  "description": "Compensation package of an employee.",
                  "properties": {
                    "baseSalary": {
                      "type": "integer",
                      "description": "Annual base salary in the smallest currency unit"
                    },
                    "annualBonus": {
                      "type": "integer",
                      "description": "Annual bonus in the same currency unit"
                    },
                    "currency": {
                      "type": "string",
                      "description": "ISO 4217 currency code"
                    }
                  },
                  "required": ["baseSalary", "annualBonus", "currency"],
                  "additionalProperties": false
                },
                "com.example.orgchart.Department": {
                  "type": "object",
                  "description": "A department in the organization.",
                  "properties": {
                    "name": {
                      "type": "string",
                      "description": "Name of the department"
                    },
                    "costCenter": {
                      "type": "string",
                      "description": "Cost center code"
                    },
                    "head": {
                      "$ref": "#/$defs/com.example.orgchart.Employee",
                      "description": "Employee heading the department"
                    }
                  },
                  "required": ["name", "costCenter", "head"],
                  "additionalProperties": false
                }
              }
            }
            """);
    }

    private static String schemaResource(Class<?> type) throws IOException {
        String path = RESOURCE_ROOT + type.getName().replace('.', '/') + ".json";
        try (InputStream input = OrgChartSchemaTest.class.getClassLoader().getResourceAsStream(path)) {
            if (input == null) {
                throw new IllegalStateException("Missing generated schema resource: " + path);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
