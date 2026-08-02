package me.kpavlov.kt.schema.generator.json

import me.kpavlov.kt.schema.Description
import me.kpavlov.kt.schema.generator.core.SchemaGeneratorService
import me.kpavlov.kt.schema.json.JsonSchema
import kotlin.reflect.KClass
import kotlin.test.Test

class JsonSchemaHierarchyTest {
    @Description("Represents an animal")
    @Suppress("unused")
    sealed interface Animal {
        @Description("Animal's name")
        val name: String

        @Description("Represents a dog")
        data class Dog(
            override val name: String,
            @property:Description("Dog's breed")
            val breed: String,
            @property:Description("Trained or not")
            val isTrained: Boolean = false,
        ) : Animal

        @Description("Represents a cat")
        data class Cat(
            override val name: String,
            @property:Description("Cat's color")
            val color: String,
            @property:Description("Lives left")
            val lives: Int = 9,
        ) : Animal
    }

    private val generator =
        requireNotNull(
            SchemaGeneratorService.getGenerator(
                KClass::class,
                JsonSchema::class,
            ),
        ) {
            "ReflectionClassJsonSchemaGenerator must be registered"
        }

    @Test
    @Suppress("LongMethod")
    fun `Should generate schema for sealed hierarchy`() {
        val schema = generator.generateSchema(Animal::class)

        // language=json
        val expectedSchema =
            $$"""
        {
          "$schema": "https://json-schema.org/draft/2020-12/schema",
          "$id": "Animal",
          "description": "Represents an animal",
          "type": "object",
          "additionalProperties": false,
          "oneOf": [
            {
              "$ref": "#/$defs/Cat"
            },
            {
              "$ref": "#/$defs/Dog"
            }
          ],
          "$defs": {
            "Cat": {
              "type": "object",
              "description": "Represents a cat",
              "properties": {
                "type": {
                  "type": "string",
                  "const": "Cat"
                },
                "name": {
                  "type": "string",
                  "description": "Animal's name"
                },
                "color": {
                  "type": "string",
                  "description": "Cat's color"
                },
                "lives": {
                  "type": "integer",
                  "description": "Lives left"
                }
              },
              "required": [
                "type",
                "name",
                "color",
                "lives"
              ],
              "additionalProperties": false
            },
            "Dog": {
              "type": "object",
              "description": "Represents a dog",
              "properties": {
                "type": {
                  "type": "string",
                  "const": "Dog"
                },
                "name": {
                  "type": "string",
                  "description": "Animal's name"
                },
                "breed": {
                  "type": "string",
                  "description": "Dog's breed"
                },
                "isTrained": {
                  "type": "boolean",
                  "description": "Trained or not"
                }
              },
              "required": [
                "type",
                "name",
                "breed",
                "isTrained"
              ],
              "additionalProperties": false
            }
          }
        }
            """.trimIndent()
        verifySchema(schema, expectedSchema)
    }

    @Description("Container with nullable animal")
    data class AnimalContainer(
        @property:Description("Optional animal")
        val animal: Animal?,
    )

    @Test
    @Suppress("LongMethod")
    fun `Should generate schema for nullable sealed hierarchy`() {
        val schema = generator.generateSchema(AnimalContainer::class)

        // language=json
        val expectedSchema =
            $$"""
        {
          "$schema": "https://json-schema.org/draft/2020-12/schema",
          "$id": "AnimalContainer",
          "description": "Container with nullable animal",
          "type": "object",
          "properties": {
            "animal": {
              "oneOf": [
                { "type": "null" },
                { "$ref": "#/$defs/Animal" }
              ],
              "description": "Optional animal"
            }
          },
          "additionalProperties": false,
          "required": [ "animal" ],
          "$defs": {
            "Animal": {
              "oneOf": [
                { "$ref": "#/$defs/Cat" },
                { "$ref": "#/$defs/Dog" }
              ],
              "description": "Represents an animal"
            },
            "Cat": {
              "type": "object",
              "description": "Represents a cat",
              "properties": {
                "type": {
                  "type": "string",
                  "const": "Cat"
                },
                "name": { "type": "string", "description": "Animal's name" },
                "color": { "type": "string", "description": "Cat's color" },
                "lives": { "type": "integer", "description": "Lives left" }
              },
              "required": [ "type", "name", "color", "lives" ],
              "additionalProperties": false
            },
            "Dog": {
              "type": "object",
              "description": "Represents a dog",
              "properties": {
                "type": {
                  "type": "string",
                  "const": "Dog"
                },
                "name": { "type": "string", "description": "Animal's name" },
                "breed": { "type": "string", "description": "Dog's breed" },
                "isTrained": { "type": "boolean", "description": "Trained or not" }
              },
              "required": [ "type", "name", "breed", "isTrained" ],
              "additionalProperties": false
            }
          }
        }
            """.trimIndent()

        verifySchema(schema, expectedSchema)
    }

    @Test
    @Suppress("LongMethod")
    fun `Should generate schema with discriminator when includeDiscriminator is true`() {
        val json = kotlinx.serialization.json.Json { encodeDefaults = false }
        val generatorWithDiscriminator =
            ReflectionClassJsonSchemaGenerator(
                json = json,
                config = JsonSchemaConfig.OpenAPI,
            )

        val schema = generatorWithDiscriminator.generateSchema(Animal::class)

        // The OpenAPI discriminator mapping key MUST equal the "type" const value in each subtype.
        // Both the mapping key and the const value use the short class names "Cat" and "Dog" so
        // that clients can dispatch correctly. The mapping value is the $ref path to the $defs entry.
        // language=json
        val expectedSchema = $$"""
        {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "$id": "Animal",
              "type": "object",
              "additionalProperties": false,
              "description": "Represents an animal",
              "oneOf": [
                {
                  "$ref": "#/$defs/Cat"
                },
                {
                  "$ref": "#/$defs/Dog"
                }
              ],
              "discriminator": {
                "propertyName": "type",
                "mapping": {
                  "Cat": "#/$defs/Cat",
                  "Dog": "#/$defs/Dog"
                }
              },
              "$defs": {
                "Cat": {
                  "type": "object",
                  "description": "Represents a cat",
                  "properties": {
                    "type": {
                      "type": "string",
                      "const": "Cat"
                    },
                    "name": {
                      "type": "string",
                      "description": "Animal's name"
                    },
                    "color": {
                      "type": "string",
                      "description": "Cat's color"
                    },
                    "lives": {
                      "type": "integer",
                      "description": "Lives left",
                      "default": 9
                    }
                  },
                  "required": ["type", "name", "color"],
                  "additionalProperties": false
                },
                "Dog": {
                  "type": "object",
                  "description": "Represents a dog",
                  "properties": {
                    "type": {
                      "type": "string",
                      "const": "Dog"
                    },
                    "name": {
                      "type": "string",
                      "description": "Animal's name"
                    },
                    "breed": {
                      "type": "string",
                      "description": "Dog's breed"
                    },
                    "isTrained": {
                      "type": "boolean",
                      "description": "Trained or not",
                      "default": false
                    }
                  },
                  "required": ["type", "name", "breed"],
                  "additionalProperties": false
                }
              }
        }
        """
        verifySchema(schema, expectedSchema)
    }
}
