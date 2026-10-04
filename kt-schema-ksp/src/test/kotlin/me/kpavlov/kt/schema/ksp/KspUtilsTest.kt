package me.kpavlov.kt.schema.ksp

import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSValueArgument
import com.google.devtools.ksp.symbol.Origin
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test

class KspUtilsTest {
    private val schemaAnnotationName = "me.kpavlov.kt.schema.Schema"

    @Test
    fun `should read explicit arguments of the annotation matched by its qualified name`() {
        // Given
        val declaration =
            declarationWith(
                annotationMock(schemaAnnotationName, argument("withSchemaObject", true)),
            )

        // When
        val parameters = getSchemaParameters(declaration, schemaAnnotationName)

        // Then
        parameters shouldBe mapOf("withSchemaObject" to true)
    }

    @Test
    fun `should skip arguments that KSP synthesized from annotation defaults`() {
        // Given
        val declaration =
            declarationWith(
                annotationMock(
                    schemaAnnotationName,
                    argument("value", "custom"),
                    argument("withSchemaObject", false, Origin.SYNTHETIC),
                ),
            )

        // When
        val parameters = getSchemaParameters(declaration, schemaAnnotationName)

        // Then
        parameters shouldBe mapOf("value" to "custom")
    }

    @Test
    fun `should return empty map when the annotation is absent`() {
        // Given
        val declaration =
            declarationWith(
                annotationMock("kotlin.Deprecated", argument("message", "old")),
            )

        // When
        val parameters = getSchemaParameters(declaration, schemaAnnotationName)

        // Then
        parameters shouldBe emptyMap()
    }

    private fun argument(
        argumentName: String,
        argumentValue: Any?,
        argumentOrigin: Origin = Origin.KOTLIN,
    ): KSValueArgument =
        mockk {
            every { name } returns ksName(argumentName)
            every { value } returns argumentValue
            every { origin } returns argumentOrigin
        }

    private fun declarationWith(vararg declaredAnnotations: KSAnnotation): KSDeclaration =
        mockk {
            every { annotations } returns declaredAnnotations.asSequence()
        }
}
