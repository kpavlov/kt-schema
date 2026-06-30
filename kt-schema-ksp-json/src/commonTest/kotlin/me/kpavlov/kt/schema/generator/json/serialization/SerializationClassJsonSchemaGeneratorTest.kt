package me.kpavlov.kt.schema.generator.json.serialization

import io.kotest.assertions.json.shouldEqualJson
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import me.kpavlov.kt.schema.generator.core.defaultOpaqueTypeNames
import kotlin.test.Test

class SerializationClassJsonSchemaGeneratorTest {
    @Serializable
    @CustomDescription("Nested property class")
    data class NestedProperty(
        @property:CustomDescription("Nested foo property")
        val foo: String,
        @property:CustomDescription("Nested bar property")
        val bar: Int,
    )

    @Serializable
    @SerialName("TestClosedPolymorphism")
    @CustomDescription("A closed polymorphism")
    sealed class TestClosedPolymorphism {
        abstract val id: String

        @Serializable
        @CustomDescription("First subclass")
        @Suppress("unused")
        data class SubClass1(
            @property:CustomDescription("Subclass identifier")
            override val id: String,
            @property:CustomDescription("First property")
            val property1: String,
        ) : TestClosedPolymorphism()

        @Serializable
        @CustomDescription("Second subclass")
        @Suppress("unused")
        data class SubClass2(
            @property:CustomDescription("Subclass identifier")
            override val id: String,
            @property:CustomDescription("Second property")
            val property2: Int,
        ) : TestClosedPolymorphism()
    }

    @Serializable
    @CustomDescription("A test enum")
    @Suppress("unused")
    enum class TestEnum {
        One,
        Two,
    }

    @Serializable
    @CustomDescription("A test data object")
    data object TestObject

    @Serializable
    data class WithDescribedInlineValueClass(
        val distance: DescribedInlineValueClass,
        val optionalDistance: DescribedInlineValueClass?,
    )

    val generator =
        SerializationClassJsonSchemaGenerator(
            introspectorConfig =
                SerializationClassSchemaIntrospector.Config(
                    descriptionExtractor = { annotations ->
                        annotations.filterIsInstance<CustomDescription>().firstOrNull()?.value
                    },
                ),
        )

    @Test
    fun `Should generate JsonSchema for complex class`() {
        val schema = generator.generateSchemaString(TestClass.serializer().descriptor)

        schema shouldEqualJson
            // language=JSON
            $$"""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "$id": "me.kpavlov.kt.schema.generator.json.serialization.TestClass",
              "description": "A test class",
              "type": "object",
              "properties": {
                "stringProperty": {
                  "type": "string",
                  "description": "A string property"
                },
                "intProperty": {
                  "type": "integer",
                  "description": "An int property"
                },
                "longProperty": {
                  "type": "integer",
                  "description": "A long property"
                },
                "doubleProperty": {
                  "type": "number",
                  "description": "A double property"
                },
                "floatProperty": {
                  "type": "number",
                  "description": "A float property"
                },
                "booleanNullableProperty": {
                  "type": [
                    "boolean",
                    "null"
                  ],
                  "description": "A nullable boolean property"
                },
                "nullableProperty": {
                  "type": [
                    "string",
                    "null"
                  ],
                  "description": "A nullable string property"
                },
                "listProperty": {
                  "type": "array",
                  "description": "A list of strings",
                  "items": {
                    "type": "string"
                  }
                },
                "mapProperty": {
                  "type": "object",
                  "description": "A map of integers",
                  "additionalProperties": {
                    "type": "integer"
                  }
                },
                "nestedProperty": {
                  "description": "A custom nested property",
                  "$ref": "#/$defs/me.kpavlov.kt.schema.generator.json.serialization.SerializationClassJsonSchemaGeneratorTest.NestedProperty"
                },
                "nestedNullableProperty": {
                  "oneOf": [
                    {
                      "type": "null"
                    },
                    {
                      "$ref": "#/$defs/me.kpavlov.kt.schema.generator.json.serialization.SerializationClassJsonSchemaGeneratorTest.NestedProperty"
                    }
                  ],
                  "description": "A custom nested nullable property"
                },
                "nestedListProperty": {
                  "type": "array",
                  "description": "A list of nested properties",
                  "items": {
                    "$ref": "#/$defs/me.kpavlov.kt.schema.generator.json.serialization.SerializationClassJsonSchemaGeneratorTest.NestedProperty"
                  }
                },
                "nestedNullableListProperty": {
                  "type": [
                    "array",
                    "null"
                  ],
                  "description": "A custom nested nullable list property",
                  "items": {
                    "$ref": "#/$defs/me.kpavlov.kt.schema.generator.json.serialization.SerializationClassJsonSchemaGeneratorTest.NestedProperty"
                  }
                },
                "nestedMapProperty": {
                  "type": "object",
                  "description": "A map of nested properties",
                  "additionalProperties": {
                    "$ref": "#/$defs/me.kpavlov.kt.schema.generator.json.serialization.SerializationClassJsonSchemaGeneratorTest.NestedProperty"
                  }
                },
                "polymorphicProperty": {
                  "description": "A custom polymorphic property",
                  "$ref": "#/$defs/TestClosedPolymorphism"
                },
                "enumProperty": {
                  "$ref": "#/$defs/me.kpavlov.kt.schema.generator.json.serialization.SerializationClassJsonSchemaGeneratorTest.TestEnum",
                  "description": "An enum property"
                },
                "objectProperty": {
                  "$ref": "#/$defs/me.kpavlov.kt.schema.generator.json.serialization.SerializationClassJsonSchemaGeneratorTest.TestObject",
                  "description": "A test object property"
                },
                "inlineValueClass": {
                  "type": "number",
                  "description": "A custom inline value class"
                },
                "inlineValueClassNullable": {
                  "type": [
                    "number",
                    "null"
                  ],
                  "description": "A custom inline value class nullable"
                }
              },
              "additionalProperties": false,
              "required": [
                "stringProperty",
                "intProperty",
                "longProperty",
                "doubleProperty",
                "floatProperty",
                "booleanNullableProperty"
              ],
              "$defs": {
                "me.kpavlov.kt.schema.generator.json.serialization.SerializationClassJsonSchemaGeneratorTest.NestedProperty": {
                  "type": "object",
                  "description": "Nested property class",
                  "properties": {
                    "foo": {
                      "type": "string",
                      "description": "Nested foo property"
                    },
                    "bar": {
                      "type": "integer",
                      "description": "Nested bar property"
                    }
                  },
                  "required": [
                    "foo",
                    "bar"
                  ],
                  "additionalProperties": false
                },
                "TestClosedPolymorphism": {
                  "description": "A closed polymorphism",
                  "oneOf": [
                    {
                      "$ref": "#/$defs/me.kpavlov.kt.schema.generator.json.serialization.SerializationClassJsonSchemaGeneratorTest.TestClosedPolymorphism.SubClass1"
                    },
                    {
                      "$ref": "#/$defs/me.kpavlov.kt.schema.generator.json.serialization.SerializationClassJsonSchemaGeneratorTest.TestClosedPolymorphism.SubClass2"
                    }
                  ]
                },
                "me.kpavlov.kt.schema.generator.json.serialization.SerializationClassJsonSchemaGeneratorTest.TestClosedPolymorphism.SubClass1": {
                  "type": "object",
                  "description": "First subclass",
                  "properties": {
                    "type": {
                      "type": "string",
                      "const": "me.kpavlov.kt.schema.generator.json.serialization.SerializationClassJsonSchemaGeneratorTest.TestClosedPolymorphism.SubClass1"
                    },
                    "id": {
                      "type": "string",
                      "description": "Subclass identifier"
                    },
                    "property1": {
                      "type": "string",
                      "description": "First property"
                    }
                  },
                  "required": [
                    "type",
                    "id",
                    "property1"
                  ],
                  "additionalProperties": false
                },
                "me.kpavlov.kt.schema.generator.json.serialization.SerializationClassJsonSchemaGeneratorTest.TestClosedPolymorphism.SubClass2": {
                  "type": "object",
                  "description": "Second subclass",
                  "properties": {
                    "type": {
                      "type": "string",
                      "const": "me.kpavlov.kt.schema.generator.json.serialization.SerializationClassJsonSchemaGeneratorTest.TestClosedPolymorphism.SubClass2"
                    },
                    "id": {
                      "type": "string",
                      "description": "Subclass identifier"
                    },
                    "property2": {
                      "type": "integer",
                      "description": "Second property"
                    }
                  },
                  "required": [
                    "type",
                    "id",
                    "property2"
                  ],
                  "additionalProperties": false
                },
                "me.kpavlov.kt.schema.generator.json.serialization.SerializationClassJsonSchemaGeneratorTest.TestEnum": {
                  "type": "string",
                  "description": "A test enum",
                  "enum": [
                    "One",
                    "Two"
                  ]
                },
                "me.kpavlov.kt.schema.generator.json.serialization.SerializationClassJsonSchemaGeneratorTest.TestObject": {
                  "type": "object",
                  "description": "A test data object",
                  "properties": {},
                  "required": [],
                  "additionalProperties": false
                }
              }
            }
            """.trimIndent()
    }

    @Test
    fun `Should generate schema for class with JsonObject field`() {
        @Serializable
        @SerialName("WithJsonObject")
        data class WithJsonObject(
            val settings: JsonObject,
        )

        val schema = generator.generateSchemaString(WithJsonObject.serializer().descriptor)

        // language=json
        schema shouldEqualJson
            $$"""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "$id": "WithJsonObject",
              "type": "object",
              "properties": {
                "settings": {
                  "type": "object",
                  "additionalProperties": {}
                }
              },
              "required": ["settings"],
              "additionalProperties": false
            }
            """.trimIndent()
    }

    @Test
    fun `Should generate schema for class with nullable JsonObject field`() {
        @Serializable
        @SerialName("WithNullableJsonObject")
        data class WithNullableJsonObject(
            val settings: JsonObject?,
        )

        val schema = generator.generateSchemaString(WithNullableJsonObject.serializer().descriptor)

        // language=json
        schema shouldEqualJson
            $$"""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "$id": "WithNullableJsonObject",
              "type": "object",
              "properties": {
                "settings": {
                  "type": ["object", "null"],
                  "additionalProperties": {}
                }
              },
              "required": ["settings"],
              "additionalProperties": false
            }
            """.trimIndent()
    }

    @Test
    fun `Should generate schema for class with JsonElement field`() {
        @Serializable
        @SerialName("WithJsonElement")
        data class WithJsonElement(
            val settings: JsonElement,
        )

        val schema = generator.generateSchemaString(WithJsonElement.serializer().descriptor)

        // language=json
        schema shouldEqualJson
            $$"""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "$id": "WithJsonElement",
              "type": "object",
              "properties": {
                "settings": {}
              },
              "required": ["settings"],
              "additionalProperties": false
            }
            """.trimIndent()
    }

    @Test
    fun `Should generate schema for class with nullable JsonElement field`() {
        @Serializable
        @SerialName("WithNullableJsonElement")
        data class WithNullableJsonElement(
            val settings: JsonElement?,
        )

        val schema = generator.generateSchemaString(WithNullableJsonElement.serializer().descriptor)

        // AnyNode ({}) already accepts any value including null — nullable flag has no effect
        // language=json
        schema shouldEqualJson
            $$"""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "$id": "WithNullableJsonElement",
              "type": "object",
              "properties": {
                "settings": {}
              },
              "required": ["settings"],
              "additionalProperties": false
            }
            """.trimIndent()
    }

    @Test
    fun `Should generate schema for class with JsonArray field`() {
        @Serializable
        @SerialName("WithJsonArray")
        data class WithJsonArray(
            val items: JsonArray,
        )

        val schema = generator.generateSchemaString(WithJsonArray.serializer().descriptor)

        // language=json
        schema shouldEqualJson
            $$"""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "$id": "WithJsonArray",
              "type": "object",
              "properties": {
                "items": {
                  "type": "array"
                }
              },
              "required": ["items"],
              "additionalProperties": false
            }
            """.trimIndent()
    }

    @Test
    fun `Should generate schema for class with nullable JsonArray field`() {
        @Serializable
        @SerialName("WithNullableJsonArray")
        data class WithNullableJsonArray(
            val items: JsonArray?,
        )

        val schema = generator.generateSchemaString(WithNullableJsonArray.serializer().descriptor)

        // AnyNode ({}) already accepts any value including null — nullable flag has no effect
        // language=json
        schema shouldEqualJson
            $$"""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "$id": "WithNullableJsonArray",
              "type": "object",
              "properties": {
                "items": {
                  "type": ["array", "null"]
                }
              },
              "required": ["items"],
              "additionalProperties": false
            }
            """.trimIndent()
    }

    @Test
    fun `Should generate schema for class with JsonPrimitive field`() {
        @Serializable
        @SerialName("WithJsonPrimitive")
        data class WithJsonPrimitive(
            val value: JsonPrimitive,
        )

        val schema = generator.generateSchemaString(WithJsonPrimitive.serializer().descriptor)

        // language=json
        schema shouldEqualJson
            $$"""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "$id": "WithJsonPrimitive",
              "type": "object",
              "properties": {
                "value": {}
              },
              "required": ["value"],
              "additionalProperties": false
            }
            """.trimIndent()
    }

    @Test
    fun `Should generate schema for class with nullable JsonPrimitive field`() {
        @Serializable
        @SerialName("WithNullableJsonPrimitive")
        data class WithNullableJsonPrimitive(
            val value: JsonPrimitive?,
        )

        val schema =
            generator.generateSchemaString(WithNullableJsonPrimitive.serializer().descriptor)

        // AnyNode ({}) already accepts any value including null — nullable flag has no effect
        // language=json
        schema shouldEqualJson
            $$"""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "$id": "WithNullableJsonPrimitive",
              "type": "object",
              "properties": {
                "value": {}
              },
              "required": ["value"],
              "additionalProperties": false
            }
            """.trimIndent()
    }

    @Test
    fun `Should generate schema for class with JsonNull field`() {
        @Serializable
        @SerialName("WithJsonNull")
        data class WithJsonNull(
            val value: JsonNull,
        )

        val schema = generator.generateSchemaString(WithJsonNull.serializer().descriptor)

        // language=json
        schema shouldEqualJson
            $$"""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "$id": "WithJsonNull",
              "type": "object",
              "properties": {
                "value": {}
              },
              "required": ["value"],
              "additionalProperties": false
            }
            """.trimIndent()
    }

    @Test
    fun `Should generate schema for class with nullable JsonNull field`() {
        @Serializable
        @SerialName("WithNullableJsonNull")
        data class WithNullableJsonNull(
            val value: JsonNull?,
        )

        val schema = generator.generateSchemaString(WithNullableJsonNull.serializer().descriptor)

        // AnyNode ({}) already accepts any value including null — nullable flag has no effect
        // language=json
        schema shouldEqualJson
            $$"""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "$id": "WithNullableJsonNull",
              "type": "object",
              "properties": {
                "value": {}
              },
              "required": ["value"],
              "additionalProperties": false
            }
            """.trimIndent()
    }

    @Test
    fun `default opaque serial names come from the canonical core function`() {
        // Single source of truth: the serialization defaults must be the same set the reflection
        // and KSP paths use, so the generators can never drift apart.
        SerializationClassSchemaIntrospector.Config().opaqueSerialNames shouldBe
            defaultOpaqueTypeNames()
    }

    @Test
    fun `Should fail with actionable error for non-standard sealed descriptor when not opaque`() {
        // JsonElement has a hand-written SEALED descriptor whose elements are heterogeneous
        // subtypes (JsonPrimitive, JsonNull, JsonObject, JsonArray, ...) rather than the standard
        // ['type', 'value'] wrapper. When it is NOT declared opaque, schema derivation must fail
        // with guidance pointing to opaqueSerialNames, never silently emit a wrong schema.
        val generatorWithoutOpaque =
            SerializationClassJsonSchemaGenerator(
                introspectorConfig =
                    SerializationClassSchemaIntrospector.Config(
                        opaqueSerialNames = emptySet(),
                    ),
            )

        val error =
            shouldThrow<IllegalStateException> {
                generatorWithoutOpaque.generateSchemaString(JsonElement.serializer().descriptor)
            }

        error.message
            .shouldNotBeNull()
            .shouldContain("opaqueSerialNames")
    }

    @Test
    fun `Should propagate inline value class description to flattened primitive in schema`() {
        val schema = generator.generateSchemaString(WithDescribedInlineValueClass.serializer().descriptor)

        schema shouldEqualJson
            // language=JSON
            $$"""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "$id": "me.kpavlov.kt.schema.generator.json.serialization.SerializationClassJsonSchemaGeneratorTest.WithDescribedInlineValueClass",
              "type": "object",
              "properties": {
                "distance": {
                  "type": "number",
                  "description": "Distance in meters"
                },
                "optionalDistance": {
                  "type": [
                    "number",
                    "null"
                  ],
                  "description": "Distance in meters"
                }
              },
              "required": [
                "distance",
                "optionalDistance"
              ],
              "additionalProperties": false
            }
            """.trimIndent()
    }

    @Test
    fun `property-level description overrides inline value class description in schema`() {
        @Serializable
        @SerialName("WithPropertyOverridesInlineDescription")
        data class WithPropertyOverridesInlineDescription(
            @property:CustomDescription("Override description")
            val distance: DescribedInlineValueClass,
        )

        val schema = generator.generateSchemaString(WithPropertyOverridesInlineDescription.serializer().descriptor)

        schema shouldEqualJson
            // language=JSON
            $$"""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "$id": "WithPropertyOverridesInlineDescription",
              "type": "object",
              "properties": {
                "distance": {
                  "type": "number",
                  "description": "Override description"
                }
              },
              "required": [
                "distance"
              ],
              "additionalProperties": false
            }
            """.trimIndent()
    }
}
