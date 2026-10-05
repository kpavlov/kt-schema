package me.kpavlov.kt.schema.generator.core.ir

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.MethodSource

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AppliedTypeIdTest {
    private val string = AppliedTypeName("kotlin.String")

    fun parsed(): List<Arguments> =
        listOf(
            Arguments.of(
                "com.acme.Payload",
                AppliedTypeName("com.acme.Payload"),
            ),
            Arguments.of(
                "com.acme.Box<kotlin.String>",
                AppliedTypeName("com.acme.Box", listOf(AppliedTypeName.Argument(string))),
            ),
            Arguments.of(
                "com.acme.Box<kotlin.String?>",
                AppliedTypeName("com.acme.Box", listOf(AppliedTypeName.Argument(string, nullable = true))),
            ),
            Arguments.of(
                "p.Pair<kotlin.String,p.Box<kotlin.collections.List<kotlin.Int>>>",
                AppliedTypeName(
                    "p.Pair",
                    listOf(
                        AppliedTypeName.Argument(string),
                        AppliedTypeName.Argument(
                            AppliedTypeName(
                                "p.Box",
                                listOf(
                                    AppliedTypeName.Argument(
                                        AppliedTypeName(
                                            "kotlin.collections.List",
                                            listOf(AppliedTypeName.Argument(AppliedTypeName("kotlin.Int"))),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

    @ParameterizedTest(name = "{0}")
    @MethodSource("parsed")
    fun `should parse an id into its declaration and arguments`(
        id: String,
        expected: AppliedTypeName,
    ) {
        // When
        val result = TypeId(id).parseApplied()

        // Then
        result shouldBe expected
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(
        "p.Box<kotlin.String>,p.Box,kotlin.String",
        "p.Box<kotlin.String?>,p.Box,kotlin.String?",
        "'p.Pair<kotlin.String?,kotlin.Int>',p.Pair,'kotlin.String?,kotlin.Int'",
    )
    fun `should parse what appliedTypeId builds`(
        expectedId: String,
        declaration: String,
        arguments: String,
    ) {
        // When
        val id = appliedTypeId(declaration, arguments.split(','))

        // Then
        id.value shouldBe expectedId
        id.parseApplied()?.declaration shouldBe declaration
        id.parseApplied()?.arguments?.size shouldBe arguments.split(',').size
    }

    @ParameterizedTest(name = "\"{0}\"")
    @CsvSource(
        "''",
        "p.Box<",
        "p.Box<>",
        "p.Box<kotlin.String",
        "p.Box<kotlin.String>x",
        "<kotlin.String>",
        "p.Box<kotlin.String,>",
    )
    fun `should not parse a malformed id`(id: String) {
        TypeId(id).parseApplied().shouldBeNull()
    }
}
