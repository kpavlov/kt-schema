package me.kpavlov.kt.schema.ksp

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
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

    private fun validClassDeclaration(annotations: List<KSAnnotation>): KSClassDeclaration =
        mockk {
            every { qualifiedName } returns ksName("test.Subject")
            every { this@mockk.annotations } returns annotations.asSequence()
            every { accept<Any?, Boolean>(any(), any()) } returns true
        }
}
