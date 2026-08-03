package me.kpavlov.kt.schema.apt

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.json.shouldEqualJson
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlin.test.assertFailsWith
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.MethodSource
import java.io.File
import java.io.StringWriter
import java.nio.file.Path
import java.util.stream.Stream
import javax.tools.DiagnosticCollector
import javax.tools.JavaFileObject
import javax.tools.ToolProvider

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JsonSchemaProcessorTest {

    //region test cases
    @Test
    fun `should generate schema for annotated record`(
        @TempDir tempDir: Path,
    ) {
        val source = """
            package com.example;

            import me.kpavlov.kt.schema.Schema;

            @Schema
            public record Person(String name, int age) {}
        """.trimIndent()

        val outputDir = compile(source, tempDir)

        // language=json
        outputDir.readSchema("com.example.Person") shouldEqualJson $$"""
            {
                "$schema": "https://json-schema.org/draft/2020-12/schema",
                "$id": "com.example.Person",
                "type": "object",
                "properties": {
                    "name": { "type": "string" },
                    "age": { "type": "integer" }
                },
                "additionalProperties": false,
                "required": ["name", "age"]
            }
        """.trimIndent()
    }

    @ParameterizedTest(name = "should resolve record component description from {0} annotation")
    @MethodSource("descriptionTargets")
    fun `should resolve record component description`(
        targets: String,
        expectedDescription: String,
        @TempDir tempDir: Path,
    ) {
        // language=java
        val annotationSource = """
            package com.example;

            import java.lang.annotation.ElementType;
            import java.lang.annotation.Retention;
            import java.lang.annotation.RetentionPolicy;
            import java.lang.annotation.Target;

            @Target($targets)
            @Retention(RetentionPolicy.RUNTIME)
            public @interface Description {
                String value();
            }
        """.trimIndent()

        // language=java
        val recordSource = """
            package com.example;

            public record Foo(@Description("$expectedDescription") String name) {}
        """.trimIndent()

        val outputDir = compile(
            sources = listOf(annotationSource, recordSource),
            tempDir = tempDir,
            options = listOf(
                "-A${JsonSchemaProcessor.ROOT_PACKAGE_OPTION}=com.example",
                "-A${JsonSchemaProcessor.INCLUDE_OPTION}=com.example.*",
            ),
        )

        // language=json
        outputDir.readSchema("com.example.Foo") shouldEqualJson $$"""
            {
                "$schema": "https://json-schema.org/draft/2020-12/schema",
                "$id": "com.example.Foo",
                "type": "object",
                "properties": {
                    "name": {
                        "type": "string",
                        "description": "$$expectedDescription"
                    }
                },
                "additionalProperties": false,
                "required": ["name"]
            }
        """.trimIndent()
    }

    @Test
    fun `should generate schema for unannotated record via root package option`(
        @TempDir tempDir: Path,
    ) {
        // language=java
        val source = """
            package com.example;

            public record Person(String name, int age) {}
        """.trimIndent()

        val outputDir = compile(
            source = source,
            tempDir = tempDir,
            options = listOf(
                "-A${JsonSchemaProcessor.ROOT_PACKAGE_OPTION}=com.example",
                "-A${JsonSchemaProcessor.INCLUDE_OPTION}=com.example.*",
            ),
        )

        // language=json
        outputDir.readSchema("com.example.Person") shouldEqualJson $$"""
            {
                "$schema": "https://json-schema.org/draft/2020-12/schema",
                "$id": "com.example.Person",
                "type": "object",
                "properties": {
                    "name": { "type": "string" },
                    "age": { "type": "integer" }
                },
                "additionalProperties": false,
                "required": ["name", "age"]
            }
        """.trimIndent()
    }

    @Test
    fun `should generate schema for unannotated record via include glob without root package`(
        @TempDir tempDir: Path,
    ) {
        // language=java
        val source = """
            package com.example;

            public record Person(String name, int age) {}
        """.trimIndent()

        val outputDir = compile(
            source = source,
            tempDir = tempDir,
            options = listOf("-A${JsonSchemaProcessor.INCLUDE_OPTION}=com.example.*"),
        )

        // language=json
        outputDir.readSchema("com.example.Person") shouldEqualJson $$"""
            {
                "$schema": "https://json-schema.org/draft/2020-12/schema",
                "$id": "com.example.Person",
                "type": "object",
                "properties": {
                    "name": { "type": "string" },
                    "age": { "type": "integer" }
                },
                "additionalProperties": false,
                "required": ["name", "age"]
            }
        """.trimIndent()
    }

    @Test
    fun `should generate schema via include glob and drop only excluded types without root package`(
        @TempDir tempDir: Path,
    ) {
        // language=java
        val personSource = """
            package com.example;

            public record Person(String name) {}
        """.trimIndent()

        // language=java
        val addressSource = """
            package com.example;

            public record Address(String city) {}
        """.trimIndent()

        val outputDir = compile(
            sources = listOf(personSource, addressSource),
            tempDir = tempDir,
            options = listOf(
                "-A${JsonSchemaProcessor.INCLUDE_OPTION}=com.example.**",
                "-A${JsonSchemaProcessor.EXCLUDE_OPTION}=**.Person",
            ),
        )

        outputDir.hasSchema("com.example.Address") shouldBe true
        outputDir.hasSchema("com.example.Person") shouldBe false
    }

    @ParameterizedTest(name = "should not generate schema when {0}")
    @MethodSource("noSchemaCases")
    @Suppress("UnusedParameter")
    fun `should not generate schema`(
        scenario: String,
        source: String,
        options: List<String>,
        @TempDir tempDir: Path,
    ) {
        val outputDir = compile(source, tempDir, options)

        outputDir.schemaFiles() shouldBe emptyList()
    }

    @Test
    fun `should fail the build when a schema references an unsupported type`(
        @TempDir tempDir: Path,
    ) {
        // language=java
        val tagSource = """
            package com.example;

            public @interface Tag {}
        """.trimIndent()

        // language=java
        val itemSource = """
            package com.example;

            import me.kpavlov.kt.schema.Schema;

            @Schema
            public record Item(Tag tag) {}
        """.trimIndent()

        val exception =
            assertFailsWith<IllegalStateException> {
                compile(listOf(tagSource, itemSource), tempDir)
            }

        assertSoftly(exception) {
            message shouldContain "Unsupported type for kt-schema-apt"
            message shouldContain "com.example.Tag"
        }
    }

    @Test
    fun `should fail the build when an enum has no constants`(
        @TempDir tempDir: Path,
    ) {
        // language=java
        val source = """
            package com.example;

            import me.kpavlov.kt.schema.Schema;

            @Schema
            public enum Empty {
            }
        """.trimIndent()

        val exception =
            assertFailsWith<IllegalStateException> {
                compile(source, tempDir)
            }

        assertSoftly(exception) {
            message shouldContain "com.example.Empty"
            message shouldContain "no constants"
        }
    }

    @Test
    fun `should generate schema for annotated enum`(
        @TempDir tempDir: Path,
    ) {
        // language=java
        val source = """
            package com.example;

            import me.kpavlov.kt.schema.Schema;

            @Schema
            public enum Color {
                RED, GREEN, BLUE
            }
        """.trimIndent()

        val outputDir = compile(source, tempDir)

        // language=json
        outputDir.readSchema("com.example.Color") shouldEqualJson $$"""
            {
                "$schema": "https://json-schema.org/draft/2020-12/schema",
                "$id": "com.example.Color",
                "type": "string",
                "enum": ["RED", "GREEN", "BLUE"]
            }
        """.trimIndent()
    }

    @Test
    fun `should generate schema for unannotated enum via root package and include options`(
        @TempDir tempDir: Path,
    ) {
        // language=java
        val source = """
            package com.example;

            public enum Color {
                RED, GREEN, BLUE
            }
        """.trimIndent()

        val outputDir = compile(
            source = source,
            tempDir = tempDir,
            options = listOf(
                "-A${JsonSchemaProcessor.ROOT_PACKAGE_OPTION}=com.example",
                "-A${JsonSchemaProcessor.INCLUDE_OPTION}=com.example.*",
            ),
        )

        // language=json
        outputDir.readSchema("com.example.Color") shouldEqualJson $$"""
            {
                "$schema": "https://json-schema.org/draft/2020-12/schema",
                "$id": "com.example.Color",
                "type": "string",
                "enum": ["RED", "GREEN", "BLUE"]
            }
        """.trimIndent()
    }

    @Test
    fun `should generate schema for record referencing an enum field`(
        @TempDir tempDir: Path,
    ) {
        // language=java
        val colorSource = """
            package com.example;

            public enum Color {
                RED, GREEN, BLUE
            }
        """.trimIndent()

        // language=java
        val carSource = """
            package com.example;

            import me.kpavlov.kt.schema.Schema;

            @Schema
            public record Car(Color color) {}
        """.trimIndent()

        val outputDir = compile(listOf(colorSource, carSource), tempDir)

        // language=json
        outputDir.readSchema("com.example.Car") shouldEqualJson $$"""
            {
                "$schema": "https://json-schema.org/draft/2020-12/schema",
                "$id": "com.example.Car",
                "type": "object",
                "properties": {
                    "color": { "$ref": "#/$defs/com.example.Color" }
                },
                "additionalProperties": false,
                "required": ["color"],
                "$defs": {
                    "com.example.Color": {
                        "type": "string",
                        "enum": ["RED", "GREEN", "BLUE"]
                    }
                }
            }
        """.trimIndent()
    }

    @Test
    fun `should generate schemas for enum field shared by multiple roots`(
        @TempDir tempDir: Path,
    ) {
        // language=java
        val colorSource = """
            package com.example;

            public enum Color {
                RED, GREEN, BLUE
            }
        """.trimIndent()

        // language=java
        val carSource = """
            package com.example;

            import me.kpavlov.kt.schema.Schema;

            @Schema
            public record Car(Color color) {}
        """.trimIndent()

        // language=java
        val bikeSource = """
            package com.example;

            import me.kpavlov.kt.schema.Schema;

            @Schema
            public record Bike(Color color) {}
        """.trimIndent()

        val outputDir = compile(listOf(colorSource, carSource, bikeSource), tempDir)

        // language=json
        val expectedSchema = $$"""
            {
                "$schema": "https://json-schema.org/draft/2020-12/schema",
                "$id": "com.example.Car",
                "type": "object",
                "properties": {
                    "color": { "$ref": "#/$defs/com.example.Color" }
                },
                "additionalProperties": false,
                "required": ["color"],
                "$defs": {
                    "com.example.Color": {
                        "type": "string",
                        "enum": ["RED", "GREEN", "BLUE"]
                    }
                }
            }
        """.trimIndent()

        outputDir.readSchema("com.example.Car") shouldEqualJson expectedSchema

        // language=json
        outputDir.readSchema("com.example.Bike") shouldEqualJson $$"""
            {
                "$schema": "https://json-schema.org/draft/2020-12/schema",
                "$id": "com.example.Bike",
                "type": "object",
                "properties": {
                    "color": { "$ref": "#/$defs/com.example.Color" }
                },
                "additionalProperties": false,
                "required": ["color"],
                "$defs": {
                    "com.example.Color": {
                        "type": "string",
                        "enum": ["RED", "GREEN", "BLUE"]
                    }
                }
            }
        """.trimIndent()
    }

    @Test
    fun `should generate schemas for nested record references`(
        @TempDir tempDir: Path,
    ) {
        // language=java
        val addressSource = """
            package com.example;

            import me.kpavlov.kt.schema.Schema;

            @Schema
            public record Address(String city, String street) {}
        """.trimIndent()

        // language=java
        val personSource = """
            package com.example;

            import me.kpavlov.kt.schema.Schema;

            @Schema
            public record Person(String name, int age, Address address) {}
        """.trimIndent()

        val outputDir = compile(listOf(addressSource, personSource), tempDir)

        // language=json
        outputDir.readSchema("com.example.Person") shouldEqualJson $$"""
            {
                "$schema": "https://json-schema.org/draft/2020-12/schema",
                "$id": "com.example.Person",
                "type": "object",
                "properties": {
                    "name": { "type": "string" },
                    "age": { "type": "integer" },
                    "address": {
                        "$ref": "#/$defs/com.example.Address"
                    }
                },
                "additionalProperties": false,
                "required": ["name", "age", "address"],
                "$defs": {
                    "com.example.Address": {
                        "type": "object",
                        "properties": {
                            "city": { "type": "string" },
                            "street": { "type": "string" }
                        },
                        "additionalProperties": false,
                        "required": ["city", "street"]
                    }
                }
            }
        """.trimIndent()

        // language=json
        outputDir.readSchema("com.example.Address") shouldEqualJson $$"""
            {
                "$schema": "https://json-schema.org/draft/2020-12/schema",
                "$id": "com.example.Address",
                "type": "object",
                "properties": {
                    "city": { "type": "string" },
                    "street": { "type": "string" }
                },
                "additionalProperties": false,
                "required": ["city", "street"]
            }
        """.trimIndent()
    }

    @Test
    fun `should generate schemas for nested record shared by multiple roots`(
        @TempDir tempDir: Path,
    ) {
        // language=java
        val vendorSource = """
            package com.example;

            public record Vendor(String name, String location) {}
        """.trimIndent()

        // language=java
        val lineItemSource = """
            package com.example;

            public record LineItem(String sku, int quantity, Vendor vendor) {}
        """.trimIndent()

        // language=java
        val orderSource = """
            package com.example;

            import me.kpavlov.kt.schema.Schema;

            @Schema
            public record Order(LineItem item) {}
        """.trimIndent()

        // language=java
        val invoiceSource = """
            package com.example;

            import me.kpavlov.kt.schema.Schema;

            @Schema
            public record Invoice(LineItem item) {}
        """.trimIndent()

        val outputDir =
            compile(listOf(vendorSource, lineItemSource, orderSource, invoiceSource), tempDir)

        // language=json
        val expectedSchema = $$"""
            {
                "$schema": "https://json-schema.org/draft/2020-12/schema",
                "$id": "com.example.Order",
                "type": "object",
                "properties": {
                    "item": {
                        "$ref": "#/$defs/com.example.LineItem"
                    }
                },
                "additionalProperties": false,
                "required": ["item"],
                "$defs": {
                    "com.example.LineItem": {
                        "type": "object",
                        "properties": {
                            "sku": { "type": "string" },
                            "quantity": { "type": "integer" },
                            "vendor": {
                                "$ref": "#/$defs/com.example.Vendor"
                            }
                        },
                        "additionalProperties": false,
                        "required": ["sku", "quantity", "vendor"]
                    },
                    "com.example.Vendor": {
                        "type": "object",
                        "properties": {
                            "name": { "type": "string" },
                            "location": { "type": "string" }
                        },
                        "additionalProperties": false,
                        "required": ["name", "location"]
                    }
                }
            }
        """.trimIndent()

        outputDir.readSchema("com.example.Order") shouldEqualJson expectedSchema

        // language=json
        outputDir.readSchema("com.example.Invoice") shouldEqualJson $$"""
            {
                "$schema": "https://json-schema.org/draft/2020-12/schema",
                "$id": "com.example.Invoice",
                "type": "object",
                "properties": {
                    "item": {
                        "$ref": "#/$defs/com.example.LineItem"
                    }
                },
                "additionalProperties": false,
                "required": ["item"],
                "$defs": {
                    "com.example.LineItem": {
                        "type": "object",
                        "properties": {
                            "sku": { "type": "string" },
                            "quantity": { "type": "integer" },
                            "vendor": {
                                "$ref": "#/$defs/com.example.Vendor"
                            }
                        },
                        "additionalProperties": false,
                        "required": ["sku", "quantity", "vendor"]
                    },
                    "com.example.Vendor": {
                        "type": "object",
                        "properties": {
                            "name": { "type": "string" },
                            "location": { "type": "string" }
                        },
                        "additionalProperties": false,
                        "required": ["name", "location"]
                    }
                }
            }
        """.trimIndent()
    }

    @Test
    fun `should generate schema for record with collections arrays and nested object`(
        @TempDir tempDir: Path,
    ) {
        // language=java
        val addressSource = """
            package com.example;

            public record Address(String city) {}
        """.trimIndent()

        // language=java
        val orderSource = """
            package com.example;

            import java.util.List;
            import java.util.Map;

            public record Order(List<String> tags, Map<String, Integer> counts, int[] values, Address address) {}
        """.trimIndent()

        val outputDir =
            compile(
                sources = listOf(addressSource, orderSource),
                tempDir = tempDir,
                options = listOf(
                "-A${JsonSchemaProcessor.ROOT_PACKAGE_OPTION}=com.example",
                "-A${JsonSchemaProcessor.INCLUDE_OPTION}=com.example.*",
            ),
            )

        // language=json
        outputDir.readSchema("com.example.Order") shouldEqualJson $$"""
            {
                "$schema": "https://json-schema.org/draft/2020-12/schema",
                "$id": "com.example.Order",
                "type": "object",
                "properties": {
                    "tags": {
                        "type": "array",
                        "items": {
                            "type": "string"
                        }
                    },
                    "counts": {
                        "type": "object",
                        "additionalProperties": {
                            "type": "integer"
                        }
                    },
                    "values": {
                        "type": "array",
                        "items": {
                            "type": "integer"
                        }
                    },
                    "address": {
                        "$ref": "#/$defs/com.example.Address"
                    }
                },
                "additionalProperties": false,
                "required": ["tags", "counts", "values", "address"],
                "$defs": {
                    "com.example.Address": {
                        "type": "object",
                        "properties": {
                            "city": {
                                "type": "string"
                            }
                        },
                        "additionalProperties": false,
                        "required": ["city"]
                    }
                }
            }
        """.trimIndent()
    }

    @Test
    fun `should generate schema for record with set and collection fields`(
        @TempDir tempDir: Path,
    ) {
        // language=java
        val source = """
            package com.example;

            import me.kpavlov.kt.schema.Schema;

            @Schema
            public record Bundle(
                java.util.Set<String> tags,
                java.util.Collection<Integer> scores
            ) {}
        """.trimIndent()

        val outputDir = compile(source, tempDir)

        // language=json
        outputDir.readSchema("com.example.Bundle") shouldEqualJson $$"""
            {
                "$schema": "https://json-schema.org/draft/2020-12/schema",
                "$id": "com.example.Bundle",
                "type": "object",
                "properties": {
                    "tags": {
                        "type": "array",
                        "items": {
                            "type": "string"
                        }
                    },
                    "scores": {
                        "type": "array",
                        "items": {
                            "type": "integer"
                        }
                    }
                },
                "additionalProperties": false,
                "required": ["tags", "scores"]
            }
        """.trimIndent()
    }

    @Test
    fun `should generate schema for record with multi-dimensional array field`(
        @TempDir tempDir: Path,
    ) {
        // language=java
        val source = """
            package com.example;

            import me.kpavlov.kt.schema.Schema;

            @Schema
            public record ArraysHolder(double[][] matrix) {}
        """.trimIndent()

        val outputDir = compile(source, tempDir)

        // language=json
        outputDir.readSchema("com.example.ArraysHolder") shouldEqualJson $$"""
            {
                "$schema": "https://json-schema.org/draft/2020-12/schema",
                "$id": "com.example.ArraysHolder",
                "type": "object",
                "properties": {
                    "matrix": {
                        "type": "array",
                        "items": {
                            "type": "array",
                            "items": {
                                "type": "number"
                            }
                        }
                    }
                },
                "additionalProperties": false,
                "required": ["matrix"]
            }
        """.trimIndent()
    }

    @Test
    fun `should generate schema for record with nested collections`(
        @TempDir tempDir: Path,
    ) {
        // language=java
        val source = """
            package com.example;

            import me.kpavlov.kt.schema.Schema;

            @Schema
            public record Nested(
                java.util.List<java.util.List<String>> matrix,
                java.util.Map<String, java.util.List<Integer>> grouped,
                java.util.List<java.util.Map<String, java.lang.Boolean>> flags
            ) {}
        """.trimIndent()

        val outputDir = compile(source, tempDir)

        // language=json
        outputDir.readSchema("com.example.Nested") shouldEqualJson $$"""
            {
                "$schema": "https://json-schema.org/draft/2020-12/schema",
                "$id": "com.example.Nested",
                "type": "object",
                "properties": {
                    "matrix": {
                        "type": "array",
                        "items": {
                            "type": "array",
                            "items": {
                                "type": "string"
                            }
                        }
                    },
                    "grouped": {
                        "type": "object",
                        "additionalProperties": {
                            "type": "array",
                            "items": {
                                "type": "integer"
                            }
                        }
                    },
                    "flags": {
                        "type": "array",
                        "items": {
                            "type": "object",
                            "additionalProperties": {
                                "type": "boolean"
                            }
                        }
                    }
                },
                "additionalProperties": false,
                "required": ["matrix", "grouped", "flags"]
            }
        """.trimIndent()
    }

    @Test
    fun `should generate schema for record with java lang Object component`(
        @TempDir tempDir: Path,
    ) {
        // language=java
        val source = """
            package com.example;

            import me.kpavlov.kt.schema.Schema;

            @Schema
            public record Wrapper(Object payload) {}
        """.trimIndent()

        val outputDir = compile(source, tempDir)

        // language=json
        outputDir.readSchema("com.example.Wrapper") shouldEqualJson $$"""
            {
                "$schema": "https://json-schema.org/draft/2020-12/schema",
                "$id": "com.example.Wrapper",
                "type": "object",
                "properties": {
                    "payload": {}
                },
                "additionalProperties": false,
                "required": ["payload"]
            }
        """.trimIndent()
    }

    @Test
    fun `should generate schema for record with upper bounded type variable`(
        @TempDir tempDir: Path,
    ) {
        // language=java
        val source = """
            package com.example;

            import me.kpavlov.kt.schema.Schema;

            @Schema
            public record Box<T extends Number>(T value) {}
        """.trimIndent()

        val outputDir = compile(source, tempDir)

        // language=json
        outputDir.readSchema("com.example.Box") shouldEqualJson $$"""
            {
                "$schema": "https://json-schema.org/draft/2020-12/schema",
                "$id": "com.example.Box",
                "type": "object",
                "properties": {
                    "value": {
                        "$ref": "#/$defs/java.lang.Number"
                    }
                },
                "additionalProperties": false,
                "required": ["value"],
                "$defs": {
                    "java.lang.Number": {
                        "type": "object",
                        "properties": {},
                        "required": [],
                        "additionalProperties": false
                    }
                }
            }
        """.trimIndent()
    }

    @Test
    fun `should generate schema for record with unbounded type variable`(
        @TempDir tempDir: Path,
    ) {
        // language=java
        val source = """
            package com.example;

            import me.kpavlov.kt.schema.Schema;

            @Schema
            public record Box<T>(T value) {}
        """.trimIndent()

        val outputDir = compile(source, tempDir)

        // language=json
        outputDir.readSchema("com.example.Box") shouldEqualJson $$"""
            {
                "$schema": "https://json-schema.org/draft/2020-12/schema",
                "$id": "com.example.Box",
                "type": "object",
                "properties": {
                    "value": {}
                },
                "additionalProperties": false,
                "required": ["value"]
            }
        """.trimIndent()
    }

    @Test
    fun `should generate schema for record with iterable subclass component`(
        @TempDir tempDir: Path,
    ) {
        // language=java
        val namesSource = """
            package com.example;

            class Names extends java.util.ArrayList<String> {}
        """.trimIndent()

        // language=java
        val orderSource = """
            package com.example;

            import me.kpavlov.kt.schema.Schema;

            @Schema
            public record Order(Names names) {}
        """.trimIndent()

        val outputDir = compile(listOf(namesSource, orderSource), tempDir)

        // language=json
        outputDir.readSchema("com.example.Order") shouldEqualJson $$"""
            {
                "$schema": "https://json-schema.org/draft/2020-12/schema",
                "$id": "com.example.Order",
                "type": "object",
                "properties": {
                    "names": {
                        "type": "array",
                        "items": {
                            "type": "string"
                        }
                    }
                },
                "additionalProperties": false,
                "required": ["names"]
            }
        """.trimIndent()
    }

    @ParameterizedTest(name = "should generate schema for record with {0} of object components")
    @MethodSource("objectCollectionFields")
    @Suppress("UnusedParameter")
    fun `should generate schema for record with collection of object components`(
        kind: String,
        fieldDeclaration: String,
        requiredProperty: String,
        propertySchema: String,
        @TempDir tempDir: Path,
    ) {
        // language=java
        val addressSource = """
            package com.example;

            import me.kpavlov.kt.schema.Schema;

            @Schema
            public record Address(String city) {}
        """.trimIndent()

        // language=java
        val catalogSource = """
            package com.example;

            import me.kpavlov.kt.schema.Schema;

            @Schema
            public record Catalog($fieldDeclaration) {}
        """.trimIndent()

        val outputDir = compile(listOf(addressSource, catalogSource), tempDir)

        // language=json
        outputDir.readSchema("com.example.Catalog") shouldEqualJson $$"""
            {
                "$schema": "https://json-schema.org/draft/2020-12/schema",
                "$id": "com.example.Catalog",
                "type": "object",
                "properties": {
                    $$propertySchema
                },
                "additionalProperties": false,
                "required": ["$$requiredProperty"],
                "$defs": {
                    "com.example.Address": {
                        "type": "object",
                        "properties": {
                            "city": {
                                "type": "string"
                            }
                        },
                        "required": ["city"],
                        "additionalProperties": false
                    }
                }
            }
        """.trimIndent()
    }

    @Test
    fun `should generate schema for plain class via root package option`(
        @TempDir tempDir: Path,
    ) {
        // language=java
        val source = """
            package com.example;

            public class Company {
                private final String name;
                private final int founded;

                public Company(String name, int founded) {
                    this.name = name;
                    this.founded = founded;
                }
            }
        """.trimIndent()

        val outputDir = compile(
            source = source,
            tempDir = tempDir,
            options = listOf(
                "-A${JsonSchemaProcessor.ROOT_PACKAGE_OPTION}=com.example",
                "-A${JsonSchemaProcessor.INCLUDE_OPTION}=com.example.*",
            ),
        )

        // language=json
        outputDir.readSchema("com.example.Company") shouldEqualJson $$"""
            {
                "$schema": "https://json-schema.org/draft/2020-12/schema",
                "$id": "com.example.Company",
                "type": "object",
                "properties": {
                    "name": { "type": "string" },
                    "founded": { "type": "integer" }
                },
                "additionalProperties": false,
                "required": ["name", "founded"]
            }
        """.trimIndent()
    }

    @Test
    fun `should generate schema for annotated interface`(
        @TempDir tempDir: Path,
    ) {
        // language=java
        val source = """
            package com.example;

            import me.kpavlov.kt.schema.Description;
            import me.kpavlov.kt.schema.Schema;

            @Schema
            public interface Person {
                @Description("Name of the person")
                String getName();

                @Description("Age of the person")
                int getAge();
            }
        """.trimIndent()

        val outputDir = compile(source, tempDir)

        // language=json
        outputDir.readSchema("com.example.Person") shouldEqualJson $$"""
            {
                "$schema": "https://json-schema.org/draft/2020-12/schema",
                "$id": "com.example.Person",
                "type": "object",
                "properties": {
                    "name": {
                        "type": "string",
                        "description": "Name of the person"
                    },
                    "age": {
                        "type": "integer",
                        "description": "Age of the person"
                    }
                },
                "additionalProperties": false,
                "required": ["name", "age"]
            }
        """.trimIndent()
    }

    @Test
    fun `should decapitalize bean accessors following JavaBeans convention`(
        @TempDir tempDir: Path,
    ) {
        // language=java
        val source = """
            package com.example;

            public interface Resource {
                String getName();
                String getURL();
                String getOK();
                String getUrlPath();
                boolean isActive();
            }
        """.trimIndent()

        val outputDir = compile(
            source = source,
            tempDir = tempDir,
            options = listOf(
                "-A${JsonSchemaProcessor.ROOT_PACKAGE_OPTION}=com.example",
                "-A${JsonSchemaProcessor.INCLUDE_OPTION}=com.example.*",
            ),
        )

        // language=json
        outputDir.readSchema("com.example.Resource") shouldEqualJson $$"""
            {
                "$schema": "https://json-schema.org/draft/2020-12/schema",
                "$id": "com.example.Resource",
                "type": "object",
                "properties": {
                    "name": { "type": "string" },
                    "URL": { "type": "string" },
                    "OK": { "type": "string" },
                    "urlPath": { "type": "string" },
                    "active": { "type": "boolean" }
                },
                "additionalProperties": false,
                "required": ["name", "URL", "OK", "urlPath", "active"]
            }
        """.trimIndent()
    }

    @Test
    fun `should generate schema for interface via root package option`(
        @TempDir tempDir: Path,
    ) {
        // language=java
        val source = """
            package com.example;

            public interface Product {
                String name();
                int price();
            }
        """.trimIndent()

        val outputDir = compile(
            source = source,
            tempDir = tempDir,
            options = listOf(
                "-A${JsonSchemaProcessor.ROOT_PACKAGE_OPTION}=com.example",
                "-A${JsonSchemaProcessor.INCLUDE_OPTION}=com.example.*",
            ),
        )

        // language=json
        outputDir.readSchema("com.example.Product") shouldEqualJson $$"""
            {
                "$schema": "https://json-schema.org/draft/2020-12/schema",
                "$id": "com.example.Product",
                "type": "object",
                "properties": {
                    "name": { "type": "string" },
                    "price": { "type": "integer" }
                },
                "additionalProperties": false,
                "required": ["name", "price"]
            }
        """.trimIndent()
    }

    @Test
    fun `should generate schema for record in nested subpackage via root package option`(
        @TempDir tempDir: Path,
    ) {
        // language=java
        val source = """
            package com.example.sub.sub;

            public record DeepRecord(String value) {}
        """.trimIndent()

        val outputDir = compile(
            source = source,
            tempDir = tempDir,
            options = listOf(
                "-A${JsonSchemaProcessor.ROOT_PACKAGE_OPTION}=com.example",
                "-A${JsonSchemaProcessor.INCLUDE_OPTION}=com.example.**",
            ),
        )

        // language=json
        outputDir.readSchema("com.example.sub.sub.DeepRecord") shouldEqualJson $$"""
            {
                "$schema": "https://json-schema.org/draft/2020-12/schema",
                "$id": "com.example.sub.sub.DeepRecord",
                "type": "object",
                "properties": {
                    "value": { "type": "string" }
                },
                "additionalProperties": false,
                "required": ["value"]
            }
        """.trimIndent()
    }

    @ParameterizedTest(name = "should map {0} to JSON Schema type {1}")
    @CsvSource(
        "String, string",
        "int, integer",
        "boolean, boolean",
        "byte, integer",
        "short, integer",
        "long, integer",
        "float, number",
        "double, number",
        "char, string",
        "Boolean, boolean",
        "Byte, integer",
        "Short, integer",
        "Integer, integer",
        "Long, integer",
        "Float, number",
        "Double, number",
        "Character, string",
    )
    fun `should map scalar component types to JSON Schema types`(
        javaType: String,
        expectedJsonType: String,
        @TempDir tempDir: Path,
    ) {
        // language=java
        val source = """
            package com.example;

            import me.kpavlov.kt.schema.Schema;

            @Schema
            public record Scalars($javaType value) {}
        """.trimIndent()

        val outputDir = compile(source, tempDir)

        // language=json
        outputDir.readSchema("com.example.Scalars") shouldEqualJson $$"""
            {
                "$schema": "https://json-schema.org/draft/2020-12/schema",
                "$id": "com.example.Scalars",
                "type": "object",
                "properties": {
                    "value": { "type": "$$expectedJsonType" }
                },
                "additionalProperties": false,
                "required": ["value"]
            }
        """.trimIndent()
    }

    //endregion

    //region test data

    private fun descriptionTargets(): Stream<Arguments> =
        Stream.of(
            Arguments.of("ElementType.RECORD_COMPONENT", "component description"),
            Arguments.of("ElementType.METHOD", "accessor description"),
            Arguments.of("ElementType.FIELD", "field description"),
            Arguments.of(
                "{ElementType.RECORD_COMPONENT, ElementType.METHOD, ElementType.FIELD}",
                "component description",
            ),
        )

    private fun noSchemaCases(): Stream<Arguments> =
        Stream.of(
            Arguments.of(
                "annotated record is outside root package",
                annotatedRecordOutsideRootPackage,
                listOf("-A${JsonSchemaProcessor.ROOT_PACKAGE_OPTION}=com.example"),
            ),
            Arguments.of(
                "exclude glob matches an annotated type",
                annotatedPerson,
                listOf("-A${JsonSchemaProcessor.EXCLUDE_OPTION}=com.example.*"),
            ),
            Arguments.of(
                "exclude glob wins over include glob",
                plainPerson,
                listOf(
                    "-A${JsonSchemaProcessor.INCLUDE_OPTION}=com.example.**",
                    "-A${JsonSchemaProcessor.EXCLUDE_OPTION}=**.Person",
                ),
            ),
            Arguments.of(
                "unannotated enum under root package without include glob",
                plainEnum,
                listOf(
                    "-A${JsonSchemaProcessor.ROOT_PACKAGE_OPTION}=com.example",
                ),
            ),
            Arguments.of(
                "no annotated or matching types are present",
                plainRecord,
                emptyList<String>(),
            ),
        )

    // language=java
    private val annotatedRecordOutsideRootPackage = """
        package com.other;

        import me.kpavlov.kt.schema.Schema;

        @Schema
        public record Person(String name, int age) {}
    """.trimIndent()

    // language=java
    private val annotatedPerson = """
        package com.example;

        import me.kpavlov.kt.schema.Schema;

        @Schema
        public record Person(String name) {}
    """.trimIndent()

    // language=java
    private val plainPerson = """
        package com.example;

        public record Person(String name) {}
    """.trimIndent()

    // language=java
    private val plainEnum = """
        package com.example;

        public enum Color {
            RED, GREEN, BLUE
        }
    """.trimIndent()

    // language=java
    private val plainRecord = """
        package com.example;

        public record PlainRecord(String value) {}
    """.trimIndent()

    private fun objectCollectionFields(): Stream<Arguments> =
        Stream.of(
            Arguments.of(
                "list",
                "java.util.List<Address> addresses",
                "addresses",
                $$"""
                    "addresses": {
                        "type": "array",
                        "items": {
                            "$ref": "#/$defs/com.example.Address"
                        }
                    }
                """.trimIndent(),
            ),
            Arguments.of(
                "set",
                "java.util.Set<Address> addresses",
                "addresses",
                $$"""
                    "addresses": {
                        "type": "array",
                        "items": {
                            "$ref": "#/$defs/com.example.Address"
                        }
                    }
                """.trimIndent(),
            ),
            Arguments.of(
                "map",
                "java.util.Map<String, Address> byCity",
                "byCity",
                $$"""
                    "byCity": {
                        "type": "object",
                        "additionalProperties": {
                            "$ref": "#/$defs/com.example.Address"
                        }
                    }
                """.trimIndent(),
            ),
        )

    //endregion

    //region compiler helpers

    private fun compile(
        source: String,
        tempDir: Path,
        options: List<String> = emptyList(),
    ): Path {
        return compile(listOf(source), tempDir, options)
    }

    private fun compile(
        sources: List<String>,
        tempDir: Path,
        options: List<String> = emptyList(),
    ): Path {
        val compiler = ToolProvider.getSystemJavaCompiler()
            ?: error("No system Java compiler available — run on JDK, not JRE")

        val diagnostics = DiagnosticCollector<JavaFileObject>()
        val outputDir = tempDir.resolve("classes")
        outputDir.toFile().mkdirs()

        val sourceFiles = sources.map(JavaSources::of)

        val allOptions = mutableListOf("-d", outputDir.toFile().absolutePath)
        allOptions.addAll(options)

        val writer = StringWriter()
        val task = compiler.getTask(
            writer,
            null,
            diagnostics,
            allOptions,
            null,
            sourceFiles,
        )
        task.setProcessors(listOf(JsonSchemaProcessor()))

        val success = task.call()

        if (!success) {
            val messages = diagnostics.diagnostics.joinToString("\n") { it.toString() }
            error("Compilation failed:\n$messages\nCompiler output:\n$writer")
        }

        return outputDir
    }

    private fun Path.readSchema(fqn: String): String {
        val file = schemaFile(fqn)
        file.exists() shouldBe true
        return file.readText()
    }

    private fun Path.hasSchema(fqn: String): Boolean =
        schemaFile(fqn).exists()

    private fun Path.schemaFiles(): List<File> =
        toFile().walkTopDown().filter { it.name.endsWith(".json") }.toList()

    private fun Path.schemaFile(fqn: String): File =
        resolve("META-INF/kt-schema/schemas/${fqn.replace('.', '/')}.json").toFile()
    //endregion
}
