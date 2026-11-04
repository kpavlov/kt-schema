package me.kpavlov.kt.schema.generator.reflect

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.types.shouldBeInstanceOf
import me.kpavlov.kt.schema.generator.core.ExperimentalSchemaGeneratorApi
import me.kpavlov.kt.schema.generator.core.ir.Constraint
import me.kpavlov.kt.schema.generator.core.ir.Literal
import me.kpavlov.kt.schema.generator.core.ir.ObjectNode
import me.kpavlov.kt.schema.generator.core.ir.TypeGraph
import me.kpavlov.kt.schema.generator.core.ir.TypeRef
import me.kpavlov.kt.schema.test.fixtures.ValidatedOrder
import me.kpavlov.kt.schema.test.fixtures.placeValidatedOrder
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

@OptIn(ExperimentalSchemaGeneratorApi::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReflectionIntrospectorValidationConstraintsTest {
    private val classProperties = ReflectionClassIntrospector.introspect(ValidatedOrder::class).rootConstraints()
    private val functionParameters = ReflectionFunctionIntrospector.introspect(::placeValidatedOrder).rootConstraints()

    fun classCases(): List<Arguments> =
        listOf(
            Arguments.of(
                "quantity",
                listOf(Constraint.Minimum(Literal.Integer(1)), Constraint.Maximum(Literal.Integer(100))),
            ),
            Arguments.of(
                "price",
                listOf(
                    Constraint.Minimum(Literal.Decimal(0.01)),
                    Constraint.Maximum(Literal.Integer(1000), exclusive = true),
                ),
            ),
            Arguments.of("weight", listOf(Constraint.Minimum(Literal.Integer(0), exclusive = true))),
            Arguments.of("discount", listOf(Constraint.Minimum(Literal.Integer(0)))),
            Arguments.of("debit", listOf(Constraint.Maximum(Literal.Integer(0), exclusive = true))),
            Arguments.of("credit", listOf(Constraint.Maximum(Literal.Integer(0)))),
            Arguments.of("name", listOf(Constraint.Size(min = 2, max = 50))),
            Arguments.of("tags", listOf(Constraint.Size(min = 1))),
            Arguments.of("note", listOf(Constraint.Size(min = 1))),
            Arguments.of("currency", listOf(Constraint.Pattern("[A-Z]{3}"))),
            Arguments.of("contact", listOf(Constraint.Format("email"))),
            Arguments.of("attributes", listOf(Constraint.Size(max = 5))),
            Arguments.of(
                "level",
                listOf(Constraint.Maximum(Literal.Integer(127)), Constraint.Minimum(Literal.Integer(0))),
            ),
        )

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("classCases")
    fun `should attach Jakarta constraints to class properties`(
        name: String,
        expected: List<Constraint>,
    ) {
        classProperties.getValue(name) shouldContainExactly expected
    }

    fun functionCases(): List<Arguments> =
        listOf(
            Arguments.of("quantity", listOf(Constraint.Minimum(Literal.Integer(1)))),
            Arguments.of("customer", listOf(Constraint.Size(min = 1))),
            Arguments.of("tags", listOf(Constraint.Size(max = 3))),
        )

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("functionCases")
    fun `should attach Jakarta constraints to function parameters`(
        name: String,
        expected: List<Constraint>,
    ) {
        functionParameters.getValue(name) shouldContainExactly expected
    }

    private fun TypeGraph.rootConstraints(): Map<String, List<Constraint>> =
        (nodes.getValue(root.shouldBeInstanceOf<TypeRef.Ref>().id) as ObjectNode)
            .properties
            .associate { it.name to it.type.constraints }
}
