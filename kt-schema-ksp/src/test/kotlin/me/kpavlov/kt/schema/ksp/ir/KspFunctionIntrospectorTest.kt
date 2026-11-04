package me.kpavlov.kt.schema.ksp.ir

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSValueParameter
import com.google.devtools.ksp.symbol.Nullability
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import me.kpavlov.kt.schema.generator.core.ir.ObjectNode
import me.kpavlov.kt.schema.generator.core.ir.PropertyValue
import me.kpavlov.kt.schema.generator.core.ir.TypeId
import me.kpavlov.kt.schema.ksp.ksName
import kotlin.test.Test

class KspFunctionIntrospectorTest {
    @Test
    fun `should mark a parameter with a Kotlin default as optional`() {
        // Given
        val function =
            functionMock(
                "greet",
                parameterMock("name", hasDefault = false),
                parameterMock("greeting", hasDefault = true),
            )

        // When
        val graph = KspFunctionIntrospector().introspect(function)

        // Then
        val node = graph.nodes[TypeId("greet")] as ObjectNode
        val byName = node.properties.associateBy { it.name }
        byName["name"].shouldNotBeNull {
            assertSoftly(this) {
                optional shouldBe false
                value shouldBe PropertyValue.None
            }
        }
        byName["greeting"].shouldNotBeNull {
            assertSoftly(this) {
                optional shouldBe true
                value shouldBe PropertyValue.UnknownDefault
            }
        }
    }

    @Test
    fun `should keep a nullable parameter without a default required`() {
        val function =
            functionMock(
                "find",
                parameterMock("query", hasDefault = false, nullable = true),
            )

        val graph = KspFunctionIntrospector().introspect(function)

        val node = graph.nodes[TypeId("find")] as ObjectNode
        node.properties.single().optional shouldBe false
    }

    private fun functionMock(
        name: String,
        vararg params: KSValueParameter,
    ): KSFunctionDeclaration =
        mockk {
            every { simpleName } returns ksName(name)
            every { parameters } returns params.toList()
            every { docString } returns null
            every { annotations } returns emptySequence()
        }

    private fun parameterMock(
        paramName: String,
        hasDefault: Boolean,
        nullable: Boolean = false,
    ): KSValueParameter {
        val stringClass =
            mockk<KSClassDeclaration> {
                every { qualifiedName } returns ksName("kotlin.String")
                every { simpleName } returns ksName("String")
            }
        val stringType =
            mockk<KSType> {
                every { declaration } returns stringClass
                every { nullability } returns if (nullable) Nullability.NULLABLE else Nullability.NOT_NULL
                every { isMarkedNullable } returns nullable
            }
        return mockk {
            every { name } returns ksName(paramName)
            every { type } returns mockk { every { resolve() } returns stringType }
            every { this@mockk.hasDefault } returns hasDefault
            every { annotations } returns emptySequence()
        }
    }
}
