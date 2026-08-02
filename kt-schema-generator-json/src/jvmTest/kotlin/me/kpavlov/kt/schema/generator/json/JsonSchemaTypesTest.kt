package me.kpavlov.kt.schema.generator.json

import io.kotest.assertions.json.shouldEqualJson
import kotlinx.serialization.json.Json
import me.kpavlov.kt.schema.Schema
import me.kpavlov.kt.schema.generator.core.SchemaGenerator
import me.kpavlov.kt.schema.json.JsonSchema
import kotlin.reflect.KClass
import kotlin.test.Test

class JsonSchemaTypesTest {
    private val generator: SchemaGenerator<KClass<out Any>, JsonSchema> =
        ReflectionClassJsonSchemaGenerator(
            json = Json { encodeDefaults = false },
            config = JsonSchemaConfig.Default,
        )

    // Primitive Types Tests

    @Test
    fun `Should handle all numeric types correctly`() {
        val schema = generator.generateSchemaString(WithNumericTypes::class)

        schema shouldEqualJson
            // language=JSON
            $$"""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "$id": "WithNumericTypes",
              "description": "Class with numeric types",
              "type": "object",
              "properties": {
                "intVal": {
                  "type": "integer",
                  "description": "Int value"
                },
                "longVal": {
                  "type": "integer",
                  "description": "Long value"
                },
                "floatVal": {
                  "type": "number",
                  "description": "Float value"
                },
                "doubleVal": {
                  "type": "number",
                  "description": "Double value"
                },
                "nullableInt": {
                  "type": [
                    "integer",
                    "null"
                  ],
                  "description": "Nullable int"
                },
                "nullableLong": {
                  "type": [
                    "integer",
                    "null"
                  ],
                  "description": "Nullable long"
                },
                "nullableFloat": {
                  "type": [
                    "number",
                    "null"
                  ],
                  "description": "Nullable float"
                },
                "nullableDouble": {
                  "type": [
                    "number",
                    "null"
                  ],
                  "description": "Nullable double"
                }
              },
              "required": [
                "intVal",
                "longVal",
                "floatVal",
                "doubleVal"
              ],
              "additionalProperties": false
            }
            """
    }

    @Test
    fun `Should handle enum types`() {
        val schema = generator.generateSchemaString(WithEnum::class)

        schema shouldEqualJson
            // language=JSON
            $$"""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "$id": "WithEnum",
              "description": "Class with enum",
              "type": "object",
              "properties": {
                "status": {
                  "$ref": "#/$defs/Status",
                  "description": "Status"
                },
                "optStatus": {
                  "oneOf": [
                    {
                      "type": "null"
                    },
                    {
                      "$ref": "#/$defs/Status"
                    }
                  ],
                  "description": "Optional status"
                }
              },
              "additionalProperties": false,
              "required": [
                "status"
              ],
              "$defs": {
                "Status": {
                  "type": "string",
                  "enum": [
                    "ACTIVE",
                    "INACTIVE",
                    "PENDING"
                  ]
                }
              }
            } 
            """.trimIndent()
    }

    // Collection Types Tests

    @Test
    fun `Should handle collection types`() {
        val schema = generator.generateSchemaString(WithCollections::class)

        schema shouldEqualJson
            // language=JSON
            $$"""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "$id": "WithCollections",
              "description": "Class with collections",
              "type": "object",
              "properties": {
                "items": {
                  "type": "array",
                  "description": "String list",
                  "items": {
                    "type": "string"
                  }
                },
                "data": {
                  "type": "object",
                  "description": "String to int map",
                  "additionalProperties": {
                    "type": "integer"
                  }
                },
                "optList": {
                  "type": [
                    "array",
                    "null"
                  ],
                  "description": "Nullable list",
                  "items": {
                    "type": "string"
                  }
                },
                "optMap": {
                  "type": [
                    "object",
                    "null"
                  ],
                  "description": "Nullable map",
                  "additionalProperties": {
                    "type": "string"
                  }
                }
              },
              "required": [
                "items",
                "data"
              ],
              "additionalProperties": false
            }
            """
    }

    @Test
    fun `Should handle list of nested objects`() {
        val schema = generator.generateSchemaString(ListOfNested::class)

        schema shouldEqualJson
            // language=JSON
            $$"""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "$id": "ListOfNested",
              "description": "List of nested",
              "type": "object",
              "properties": {
                "items": {
                  "type": "array",
                  "description": "Items",
                  "items": {
                    "$ref": "#/$defs/Address"
                  }
                },
                "optionalItems": {
                  "type": [
                    "array",
                    "null"
                  ],
                  "description": "Optional items",
                  "items": {
                    "$ref": "#/$defs/Address"
                  }
                }
              },
              "required": [
                "items"
              ],
              "additionalProperties": false,
              "$defs": {
                "Address": {
                  "type": "object",
                  "description": "Nested object",
                  "properties": {
                    "street": { "type": "string", "description": "Street name" },
                    "city": { "type": "string", "description": "City name" }
                  },
                  "required": ["street", "city"],
                  "additionalProperties": false
                }
              }
            }
            """
    }

    @Test
    fun `Should handle map of nested objects`() {
        val schema = generator.generateSchemaString(MapOfNested::class)

        schema shouldEqualJson
            // language=JSON
            $$"""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "$id": "MapOfNested",
              "description": "Map of nested",
              "type": "object",
              "properties": {
                "data": {
                  "type": "object",
                  "description": "Data",
                  "additionalProperties": {
                    "$ref": "#/$defs/Address"
                  }
                },
                "optionalData": {
                  "type": ["object", "null"],
                  "description": "Optional data",
                  "additionalProperties": {
                    "$ref": "#/$defs/Address"
                  }
                }
              },
              "required": ["data"],
              "additionalProperties": false,
              "$defs": {
                "Address": {
                  "type": "object",
                  "description": "Nested object",
                  "properties": {
                    "street": { "type": "string", "description": "Street name" },
                    "city": { "type": "string", "description": "City name" }
                  },
                  "required": ["street", "city"],
                  "additionalProperties": false
                }
              }
            }
            """
    }

    // Nested Object Tests

    @Test
    fun `Should handle nested objects`() {
        val schema = generator.generateSchemaString(WithNested::class)

        schema shouldEqualJson
            // language=JSON
            $$"""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "$id": "WithNested",
              "description": "Class with nested object",
              "type": "object",
              "properties": {
                "name": {
                  "type": "string",
                  "description": "Name"
                },
                "address": {
                  "$ref": "#/$defs/Address",
                  "description": "Address"
                },
                "optAddress": {
                  "oneOf": [
                    {
                      "type": "null"
                    },
                    {
                      "$ref": "#/$defs/Address"
                    }
                  ],
                  "description": "Optional address"
                }
              },
              "additionalProperties": false,
              "required": [
                "name",
                "address"
              ],
              "$defs": {
                "Address": {
                  "type": "object",
                  "description": "Nested object",
                  "properties": {
                    "street": {
                      "type": "string",
                      "description": "Street name"
                    },
                    "city": {
                      "type": "string",
                      "description": "City name"
                    }
                  },
                  "required": [
                    "street",
                    "city"
                  ],
                  "additionalProperties": false
                }
              }
            }            
            """.trimIndent()
    }

    @Test
    fun `Should handle deeply nested structures`() {
        val schema = generator.generateSchemaString(DeepNested::class)

        schema shouldEqualJson
            // language=JSON
            $$"""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "$id": "DeepNested",
              "description": "Deep nested structure",
              "type": "object",
              "properties": {
                "level1": {
                  "$ref": "#/$defs/Level1",
                  "description": "Level 1"
                }
              },
              "additionalProperties": false,
              "required": [
                "level1"
              ],
              "$defs": {
                "Level1": {
                  "type": "object",
                  "description": "Level 1",
                  "properties": {
                    "level2": {
                      "$ref": "#/$defs/Level2",
                      "description": "Level 2"
                    },
                    "value": {
                      "type": "string",
                      "description": "Value"
                    }
                  },
                  "required": [
                    "level2",
                    "value"
                  ],
                  "additionalProperties": false
                },
                "Level2": {
                  "type": "object",
                  "description": "Level 2",
                  "properties": {
                    "value": {
                      "type": "integer",
                      "description": "Value"
                    },
                    "level3": {
                      "$ref": "#/$defs/Level3",
                      "description": "Level 3"
                    },
                    "optional": {
                      "type": [
                        "string",
                        "null"
                      ],
                      "description": "Optional value"
                    }
                  },
                  "required": [
                    "value",
                    "level3"
                  ],
                  "additionalProperties": false
                },
                "Level3": {
                  "type": "object",
                  "description": "Level 3",
                  "properties": {
                    "value": {
                      "type": "string",
                      "description": "Value"
                    }
                  },
                  "required": [
                    "value"
                  ],
                  "additionalProperties": false
                }
              }
            } 
            """.trimIndent()
    }

    // Required Fields Tests

    @Test
    fun `Should correctly distinguish required vs optional vs default`() {
        val schema = generator.generateSchemaString(MixedRequiredOptional::class)

        schema shouldEqualJson
            // language=JSON
            $$"""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "$id": "MixedRequiredOptional",
              "description": "Mixed required and optional",
              "type": "object",
              "properties": {
                "req1": {
                  "type": "string",
                  "description": "Required string"
                },
                "req2": {
                  "type": "integer",
                  "description": "Required int"
                },
                "opt1": {
                  "type": [
                    "string",
                    "null"
                  ],
                  "description": "Optional string"
                },
                "opt2": {
                  "type": [
                    "integer",
                    "null"
                  ],
                  "description": "Optional int"
                },
                "def1": {
                  "type": "string",
                  "description": "Default string",
                  "default": "default"
                },
                "def2": {
                  "type": "integer",
                  "description": "Default int",
                  "default": 42
                }
              },
              "required": [
                "req1",
                "req2"
              ],
              "additionalProperties": false
            }
            """
    }

    @Test
    fun `Should handle class with all optional fields`() {
        val schema = generator.generateSchemaString(EmptyClass::class)

        schema shouldEqualJson
            // language=JSON
            $$"""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "$id": "EmptyClass",
              "description": "Empty class",
              "type": "object",
              "properties": {
                "dummy": {
                  "type": [
                    "string",
                    "null"
                  ],
                  "default": "ignored"
                }
              },
              "additionalProperties": false
            }
            """
    }

    @Test
    fun `Should handle class with single required field`() {
        val schema = generator.generateSchemaString(SingleRequired::class)

        schema shouldEqualJson
            // language=JSON
            $$"""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "$id": "SingleRequired",
              "description": "Single required field",
              "type": "object",
              "properties": {
                "value": {
                  "type": "string",
                  "description": "Only field"
                }
              },
              "required": [
                "value"
              ],
              "additionalProperties": false
            }
            """
    }

    // Description Preservation Tests

    @Test
    fun `Should preserve descriptions through transformations`() {
        val schema = generator.generateSchemaString(MixedRequiredOptional::class)

        schema shouldEqualJson
            // language=JSON
            $$"""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "$id": "MixedRequiredOptional",
              "description": "Mixed required and optional",
              "type": "object",
              "properties": {
                "req1": {
                  "type": "string",
                  "description": "Required string"
                },
                "req2": {
                  "type": "integer",
                  "description": "Required int"
                },
                "opt1": {
                  "type": [
                    "string",
                    "null"
                  ],
                  "description": "Optional string"
                },
                "opt2": {
                  "type": [
                    "integer",
                    "null"
                  ],
                  "description": "Optional int"
                },
                "def1": {
                  "type": "string",
                  "description": "Default string",
                  "default": "default"
                },
                "def2": {
                  "type": "integer",
                  "description": "Default int",
                  "default": 42
                }
              },
              "required": [
                "req1",
                "req2"
              ],
              "additionalProperties": false
            }
            """
    }

    // Nullable Optional Fields Tests

    @Test
    fun `Should handle nullable optional fields with default config`() {
        val schema = generator.generateSchemaString(PersonWithOptionals::class)

        schema shouldEqualJson
            // language=JSON
            $$"""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "$id": "PersonWithOptionals",
              "description": "Person with various optional fields",
              "type": "object",
              "properties": {
                "name": {
                  "type": "string",
                  "description": "Name"
                },
                "age": {
                  "type": [
                    "integer",
                    "null"
                  ],
                  "description": "Age"
                },
                "email": {
                  "type": [
                    "string",
                    "null"
                  ],
                  "description": "Email"
                },
                "score": {
                  "type": [
                    "number",
                    "null"
                  ],
                  "description": "Score"
                },
                "active": {
                  "type": [
                    "boolean",
                    "null"
                  ],
                  "description": "Active"
                }
              },
              "required": [
                "name"
              ],
              "additionalProperties": false
            }
            """
    }

    @Schema
    @Suppress("MayBeConstant")
    data object ObjectWithProps {
        val foo = "bar"
        val num = 42
    }

    @Test
    fun `Should handle kotlin Any typed properties as empty schema`() {
        val schema = generator.generateSchemaString(WithAnyProperties::class)

        schema shouldEqualJson
            // language=JSON
            $$"""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "$id": "WithAnyProperties",
              "description": "Class with Any typed properties",
              "type": "object",
              "properties": {
                "content": {
                  "description": "Unconstrained content"
                },
                "optContent": {},
                "metadata": {
                  "type": "object",
                  "description": "Metadata map",
                  "additionalProperties": {}
                }
              },
              "required": ["content"],
              "additionalProperties": false
            }
            """.trimIndent()
    }

    @Test
    fun `should handle data object schema with constant values`() {
        val schema = ReflectionClassJsonSchemaGenerator().generateSchemaString(ObjectWithProps::class)
        schema shouldEqualJson $$"""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "$id": "ObjectWithProps",
              "type": "object",
              "properties": {
                "foo": {
                  "type": "string",
                  "const": "bar"
                },
                "num": {
                  "type": "integer",
                  "const": 42
                }
              },
              "required": [
                "foo",
                "num"
              ],
              "additionalProperties": false
            }
            """
    }
}
