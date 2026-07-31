package me.kpavlov.kt.schema.apt

import io.kotest.assertions.json.shouldEqualJson
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.io.StringWriter
import java.net.URI
import java.nio.file.Path
import javax.tools.DiagnosticCollector
import javax.tools.JavaFileObject
import javax.tools.SimpleJavaFileObject
import javax.tools.ToolProvider

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

        val schemaFile = outputDir.resolve("META-INF/kt-schema/schemas/com/example/Person.json")
        schemaFile.toFile().exists() shouldBe true
        // language=json
        schemaFile.toFile().readText() shouldEqualJson """
            {
                "${'$'}schema": "https://json-schema.org/draft/2020-12/schema",
                "${'$'}id": "com.example.Person",
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
    fun `should generate schema for record via root package option`(
        @TempDir tempDir: Path,
    ) {
        val source = """
            package com.example;

            import me.kpavlov.kt.schema.Schema;

            @Schema
            public record Foo(String value) {}
        """.trimIndent()

        val outputDir = compile(
            source = source,
            tempDir = tempDir,
            options = listOf("-A${JsonSchemaProcessor.ROOT_PACKAGE_OPTION}=com.example"),
        )

        val schemaFile = outputDir.resolve("META-INF/kt-schema/schemas/com/example/Foo.json")
        schemaFile.toFile().exists() shouldBe true
        // language=json
        schemaFile.toFile().readText() shouldEqualJson $$"""
            {
                "$schema": "https://json-schema.org/draft/2020-12/schema",
                "$id": "com.example.Foo",
                "type": "object",
                "properties": {
                    "value": { "type": "string" }
                },
                "additionalProperties": false,
                "required": ["value"]
            }
        """.trimIndent()
    }

    @Test
    fun `should generate schema for unannotated record via root package option`(
        @TempDir tempDir: Path,
    ) {
        val source = """
            package com.example;

            public record Person(String name, int age) {}
        """.trimIndent()

        val outputDir = compile(
            source = source,
            tempDir = tempDir,
            options = listOf("-A${JsonSchemaProcessor.ROOT_PACKAGE_OPTION}=com.example"),
        )

        val schemaFile = outputDir.resolve("META-INF/kt-schema/schemas/com/example/Person.json")
        schemaFile.toFile().exists() shouldBe true
        // language=json
        schemaFile.toFile().readText() shouldEqualJson $$"""
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
    fun `should generate schemas for nested record references`(
        @TempDir tempDir: Path,
    ) {
        val addressSource = """
            package com.example;

            import me.kpavlov.kt.schema.Schema;

            @Schema
            public record Address(String city, String street) {}
        """.trimIndent()

        val personSource = """
            package com.example;

            import me.kpavlov.kt.schema.Schema;

            @Schema
            public record Person(String name, int age, Address address) {}
        """.trimIndent()

        val outputDir = compile(listOf(addressSource, personSource), tempDir)

        val personSchema =
            outputDir.resolve("META-INF/kt-schema/schemas/com/example/Person.json").toFile()
        val addressSchema =
            outputDir.resolve("META-INF/kt-schema/schemas/com/example/Address.json").toFile()

        personSchema.exists() shouldBe true
        // language=json
        personSchema.readText() shouldEqualJson $$"""
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

        addressSchema.exists() shouldBe true
        // language=json
        addressSchema.readText() shouldEqualJson $$"""
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
    fun `should not generate schema when no annotated types`(
        @TempDir tempDir: Path,
    ) {
        val source = """
            package com.example;

            public record PlainRecord(String value) {}
        """.trimIndent()

        val outputDir = compile(source, tempDir)

        val schemaFiles =
            outputDir.toFile().walkTopDown().filter { it.name.endsWith(".json") }.toList()
        schemaFiles shouldBe emptyList()
    }

    @Test
    fun `should generate schema for plain class via root package option`(
        @TempDir tempDir: Path,
    ) {
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
            options = listOf("-A${JsonSchemaProcessor.ROOT_PACKAGE_OPTION}=com.example"),
        )

        val schemaFile = outputDir.resolve("META-INF/kt-schema/schemas/com/example/Company.json")
        schemaFile.toFile().exists() shouldBe true
        // language=json
        schemaFile.toFile().readText() shouldEqualJson """
            {
                "${'$'}schema": "https://json-schema.org/draft/2020-12/schema",
                "${'$'}id": "com.example.Company",
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
    fun `should not generate schema for interface under root package`(
        @TempDir tempDir: Path,
    ) {
        val source = """
            package com.example;

            public interface Marker {}
        """.trimIndent()

        val outputDir = compile(
            source = source,
            tempDir = tempDir,
            options = listOf("-A${JsonSchemaProcessor.ROOT_PACKAGE_OPTION}=com.example"),
        )

        val schemaFiles =
            outputDir.toFile().walkTopDown().filter { it.name.endsWith(".json") }.toList()
        schemaFiles shouldBe emptyList()
    }

    @Test
    fun `should generate schema for record in nested subpackage via root package option`(
        @TempDir tempDir: Path,
    ) {
        val source = """
            package com.example.sub.sub;

            public record DeepRecord(String value) {}
        """.trimIndent()

        val outputDir = compile(
            source = source,
            tempDir = tempDir,
            options = listOf("-A${JsonSchemaProcessor.ROOT_PACKAGE_OPTION}=com.example"),
        )

        val schemaFile =
            outputDir.resolve("META-INF/kt-schema/schemas/com/example/sub/sub/DeepRecord.json")
        schemaFile.toFile().exists() shouldBe true
        // language=json
        schemaFile.toFile().readText() shouldEqualJson """
            {
                "${'$'}schema": "https://json-schema.org/draft/2020-12/schema",
                "${'$'}id": "com.example.sub.sub.DeepRecord",
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
        val source = """
            package com.example;

            import me.kpavlov.kt.schema.Schema;

            @Schema
            public record Scalars($javaType value) {}
        """.trimIndent()

        val outputDir = compile(source, tempDir)

        val schemaFile = outputDir.resolve("META-INF/kt-schema/schemas/com/example/Scalars.json")
        schemaFile.toFile().exists() shouldBe true
        // language=json
        schemaFile.toFile().readText() shouldEqualJson """
            {
                "${'$'}schema": "https://json-schema.org/draft/2020-12/schema",
                "${'$'}id": "com.example.Scalars",
                "type": "object",
                "properties": {
                    "value": { "type": "$expectedJsonType" }
                },
                "additionalProperties": false,
                "required": ["value"]
            }
        """.trimIndent()
    }

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

        val sourceFiles = sources.map { code ->
            val pkg = Regex("""package\s+(\S+);""").find(code)?.groupValues?.get(1) ?: ""
            val cls =
                Regex("""(?:public\s+)?(?:record|class|interface)\s+(\w+)""").find(code)?.groupValues?.get(1)
                    ?: error("Cannot infer class name from source:\n$code")
            val fqn = "$pkg.$cls"
            object : SimpleJavaFileObject(
                URI.create("string:///${fqn.replace('.', '/')}${JavaFileObject.Kind.SOURCE.extension}"),
                JavaFileObject.Kind.SOURCE,
            ) {
                override fun getCharContent(ignoreEncodingErrors: Boolean) = code
            }
        }

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
    //endregion
}
