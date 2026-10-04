package me.kpavlov.kt.schema.ksp

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSValueParameter
import com.google.devtools.ksp.symbol.Nullability
import io.kotest.assertions.json.shouldEqualJson
import io.kotest.matchers.collections.shouldBeEmpty
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.parallel.Isolated
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.io.ByteArrayOutputStream

@ExtendWith(MockKExtension::class)
@Isolated
class SchemaExtensionProcessorTest {
    @MockK
    private lateinit var codeGenerator: CodeGenerator

    @MockK(relaxUnitFun = true)
    private lateinit var logger: KSPLogger

    @MockK
    private lateinit var resolver: Resolver

    private lateinit var subject: SchemaExtensionProcessor

    @BeforeEach
    fun beforeEach() {
        subject =
            SchemaExtensionProcessor(
                codeGenerator,
                logger,
                options = emptyMap(),
            )
    }

    @Test
    fun `should skip processing when disabled via options`() {
        // Given
        val options = mapOf("me.kpavlov.kt.schema.enabled" to "false")
        subject =
            SchemaExtensionProcessor(
                codeGenerator,
                logger,
                options,
            )

        val symbols = mockk<Sequence<KSAnnotated>>()
        every { resolver.getSymbolsWithAnnotation("me.kpavlov.kt.schema.Schema") } returns symbols

        // When
        val result = subject.process(resolver)

        // Then
        result.shouldBeEmpty()
        verify(exactly = 0) { codeGenerator.createNewFile(any(), any(), any()) }
    }

    @Test
    fun `should process when enabled is explicitly true`() {
        // Given
        val options = mapOf("me.kpavlov.kt.schema.enabled" to "true")
        subject =
            SchemaExtensionProcessor(
                codeGenerator,
                logger,
                options,
            )

        val emptySymbols = emptySequence<KSAnnotated>()
        every { resolver.getSymbolsWithAnnotation("me.kpavlov.kt.schema.Schema") } returns emptySymbols

        // When
        val result = subject.process(resolver)

        // Then
        result.shouldBeEmpty()
    }

    @Test
    fun `should process when enabled option is not set (default enabled)`() {
        // Given
        val options = emptyMap<String, String>()
        subject =
            SchemaExtensionProcessor(
                codeGenerator,
                logger,
                options,
            )

        val emptySymbols = emptySequence<KSAnnotated>()
        every { resolver.getSymbolsWithAnnotation("me.kpavlov.kt.schema.Schema") } returns emptySymbols

        // When
        val result = subject.process(resolver)

        // Then
        result.shouldBeEmpty()
    }

    @Test
    fun `finish should log success message`() {
        // Given
        val options = emptyMap<String, String>()
        subject =
            SchemaExtensionProcessor(
                codeGenerator,
                logger,
                options,
            )

        // When
        subject.finish()

        // Then
        verify { logger.info(match { it.contains("Done") }) }
    }

    @Test
    fun `onError should log error with options`() {
        // Given
        val options =
            mapOf(
                "me.kpavlov.kt.schema.enabled" to "true",
                "me.kpavlov.kt.schema.withSchemaObject" to "true",
            )
        subject =
            SchemaExtensionProcessor(
                codeGenerator,
                logger,
                options,
            )

        // When
        subject.onError()

        // Then
        verify {
            logger.error(match { it.contains("Error") && it.contains("KSP Processor Options") })
        }
    }

    @Test
    fun `processor accepts valid visibility option - public`() {
        // Given
        val options = mapOf("me.kpavlov.kt.schema.visibility" to "public")
        subject =
            SchemaExtensionProcessor(
                codeGenerator,
                logger,
                options,
            )

        val emptySymbols = emptySequence<KSAnnotated>()
        every { resolver.getSymbolsWithAnnotation("me.kpavlov.kt.schema.Schema") } returns emptySymbols

        // When
        val result = subject.process(resolver)

        // Then
        result.shouldBeEmpty()
    }

    @Test
    fun `processor accepts valid visibility option - internal`() {
        // Given
        val options = mapOf("me.kpavlov.kt.schema.visibility" to "internal")
        subject =
            SchemaExtensionProcessor(
                codeGenerator,
                logger,
                options,
            )

        val emptySymbols = emptySequence<KSAnnotated>()
        every { resolver.getSymbolsWithAnnotation("me.kpavlov.kt.schema.Schema") } returns emptySymbols

        // When
        val result = subject.process(resolver)

        // Then
        result.shouldBeEmpty()
    }

    @Test
    fun `processor accepts valid visibility option - private`() {
        // Given
        val options = mapOf("me.kpavlov.kt.schema.visibility" to "private")
        subject =
            SchemaExtensionProcessor(
                codeGenerator,
                logger,
                options,
            )

        val emptySymbols = emptySequence<KSAnnotated>()
        every { resolver.getSymbolsWithAnnotation("me.kpavlov.kt.schema.Schema") } returns emptySymbols

        // When
        val result = subject.process(resolver)

        // Then
        result.shouldBeEmpty()
    }

    @Test
    fun `processor accepts valid visibility option - empty string`() {
        // Given
        val options = mapOf("me.kpavlov.kt.schema.visibility" to "")
        subject =
            SchemaExtensionProcessor(
                codeGenerator,
                logger,
                options,
            )

        val emptySymbols = emptySequence<KSAnnotated>()
        every { resolver.getSymbolsWithAnnotation("me.kpavlov.kt.schema.Schema") } returns emptySymbols

        // When
        val result = subject.process(resolver)

        // Then
        result.shouldBeEmpty()
    }

    @Test
    fun `processor handles missing visibility option (defaults to empty)`() {
        // Given
        val options = emptyMap<String, String>()
        subject =
            SchemaExtensionProcessor(
                codeGenerator,
                logger,
                options,
            )

        val emptySymbols = emptySequence<KSAnnotated>()
        every { resolver.getSymbolsWithAnnotation("me.kpavlov.kt.schema.Schema") } returns emptySymbols

        // When
        val result = subject.process(resolver)

        // Then
        result.shouldBeEmpty()
    }

    @Test
    fun `should report contradictory annotations at the offending class`() {
        // Given
        val classDeclaration = validClassDeclaration(annotations = listOf(annotationMock("test.SchemaIgnore")))
        every { resolver.getSymbolsWithAnnotation("me.kpavlov.kt.schema.Schema") } returns
            sequenceOf(classDeclaration)

        // When
        subject.process(resolver)

        // Then
        verify { logger.error(match { it.contains("contradictory") }, classDeclaration) }
    }

    @Test
    fun `should report generation failure at the offending class with its stack trace`() {
        // Given: unstubbed members make schema generation throw
        val classDeclaration = validClassDeclaration(annotations = emptyList())
        every { resolver.getSymbolsWithAnnotation("me.kpavlov.kt.schema.Schema") } returns
            sequenceOf(classDeclaration)

        // When
        subject.process(resolver)

        // Then
        verify {
            logger.error(match { it.startsWith("Failed to generate schema extension") }, classDeclaration)
            logger.exception(any())
        }
    }

    @Test
    fun `should report invalid config option and generate nothing`() {
        // Given: an unknown config class; the unstubbed resolver proves no symbol is read
        subject =
            SchemaExtensionProcessor(
                codeGenerator,
                logger,
                mapOf(SchemaExtensionProcessor.OPTION_CONFIG to "no.such.Config"),
            )

        // When
        val result = subject.process(resolver)

        // Then
        result.shouldBeEmpty()
        verify { logger.error(match { it.contains("no.such.Config") && it.contains("lenient") }) }
        verify(exactly = 0) { codeGenerator.createNewFile(any(), any(), any()) }
    }

    @Test
    fun `should generate strict class schema when config option is not set`() {
        // When
        val schema = generatedClassSchema(options = emptyMap())

        // Then
        schema shouldEqualJson
            // language=json
            $$"""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "$id": "test.Subject",
              "type": "object",
              "additionalProperties": false
            }
            """.trimIndent()
    }

    @ParameterizedTest
    @ValueSource(strings = ["lenient", "Lenient", "LENIENT"])
    fun `should generate lenient class schema when config option is lenient`(value: String) {
        // When
        val schema = generatedClassSchema(options = mapOf(SchemaExtensionProcessor.OPTION_CONFIG to value))

        // Then
        schema shouldEqualJson
            // language=json
            $$"""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "$id": "test.Subject",
              "type": "object"
            }
            """.trimIndent()
    }

    @Test
    fun `should leave defaulted property optional without default when config option is lenient`() {
        // Given: a class with a defaulted constructor parameter (KSP can't read its value) and a plain one
        val parameters =
            listOf(
                constructorParameter("priority", "kotlin.Int", hasDefault = true),
                constructorParameter("text", "kotlin.String", hasDefault = false),
            )

        // When
        val schema =
            generatedClassSchema(
                options = mapOf(SchemaExtensionProcessor.OPTION_CONFIG to "Lenient"),
                constructorParameters = parameters,
            )

        // Then
        schema shouldEqualJson
            // language=json
            $$"""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "$id": "test.Subject",
              "type": "object",
              "properties": {
                "priority": { "type": "integer" },
                "text": { "type": "string" }
              },
              "required": ["text"]
            }
            """.trimIndent()
    }

    private fun generatedClassSchema(
        options: Map<String, String>,
        constructorParameters: List<KSValueParameter> = emptyList(),
    ): String {
        val classDeclaration = schemaClassDeclaration(constructorParameters)
        val generated = ByteArrayOutputStream()
        every { codeGenerator.createNewFile(any(), any(), any()) } returns generated
        every { resolver.getSymbolsWithAnnotation("me.kpavlov.kt.schema.Schema") } returns
            sequenceOf(classDeclaration)

        SchemaExtensionProcessor(codeGenerator, logger, options).process(resolver)

        // The schema is embedded in the generated `jsonSchemaString` extension as a raw string literal.
        return generated
            .toString(Charsets.UTF_8)
            .substringAfter("\"\"\"")
            .substringBefore("\"\"\"")
            .replace($$"${'$'}", "$")
    }

    /** A `test.Subject` class annotated with `@Schema`, with the given primary-constructor parameters. */
    private fun schemaClassDeclaration(constructorParameters: List<KSValueParameter>): KSClassDeclaration {
        val type = mockk<KSType>(relaxed = true)
        val declaration =
            mockk<KSClassDeclaration>(relaxed = true) {
                every { qualifiedName } returns ksName("test.Subject")
                every { simpleName } returns ksName("Subject")
                every { packageName } returns ksName("test")
                every { classKind } returns ClassKind.CLASS
                every { annotations } returns sequenceOf(annotationMock("me.kpavlov.kt.schema.Schema"))
                every { accept<Any?, Boolean>(any(), any()) } returns true
                every { containingFile } returns mockk<KSFile>(relaxed = true)
                every { asStarProjectedType() } returns type
                every { declarations } returns emptySequence()
                every { primaryConstructor } returns
                    mockk { every { parameters } returns constructorParameters }
            }
        every { type.declaration } returns declaration
        return declaration
    }

    private fun constructorParameter(
        parameterName: String,
        typeQualifiedName: String,
        hasDefault: Boolean,
    ): KSValueParameter {
        val typeDeclaration =
            mockk<KSDeclaration> {
                every { qualifiedName } returns ksName(typeQualifiedName)
                every { simpleName } returns ksName(typeQualifiedName.substringAfterLast('.'))
            }
        val resolvedType =
            mockk<KSType>(relaxed = true) {
                every { declaration } returns typeDeclaration
                every { nullability } returns Nullability.NOT_NULL
                every { isMarkedNullable } returns false
            }
        return mockk(relaxed = true) {
            every { name } returns ksName(parameterName)
            every { type } returns mockk { every { resolve() } returns resolvedType }
            every { this@mockk.hasDefault } returns hasDefault
            every { annotations } returns emptySequence()
        }
    }

    private fun validClassDeclaration(annotations: List<KSAnnotation>): KSClassDeclaration =
        mockk {
            every { qualifiedName } returns ksName("test.Subject")
            every { this@mockk.annotations } returns annotations.asSequence()
            every { accept<Any?, Boolean>(any(), any()) } returns true
        }
}
