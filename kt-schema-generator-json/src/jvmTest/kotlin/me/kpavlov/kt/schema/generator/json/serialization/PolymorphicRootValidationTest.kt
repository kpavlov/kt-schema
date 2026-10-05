package me.kpavlov.kt.schema.generator.json.serialization

import com.networknt.schema.InputFormat
import com.networknt.schema.Schema
import com.networknt.schema.SchemaLocation
import com.networknt.schema.SchemaRegistry
import com.networknt.schema.SpecificationVersion
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotBeEmpty
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.Test

class PolymorphicRootValidationTest {
    @Serializable
    sealed interface Shape {
        @Serializable
        @SerialName("Circle")
        data class Circle(
            val radius: Double,
        ) : Shape

        @Serializable
        @SerialName("Rect")
        data class Rect(
            val w: Double,
        ) : Shape
    }

    private val json = Json { classDiscriminator = "kind" }

    private val schema: Schema =
        SchemaRegistry
            .withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
            .getSchema(
                // The generated `$id` is relative, so it needs an absolute base to resolve against.
                SchemaLocation.of("https://example.com/schema.json"),
                SerializationClassJsonSchemaGenerator(json = json)
                    .generateSchemaString(Shape.serializer().descriptor),
                InputFormat.JSON,
            )

    @Test
    fun `should accept serialized subtype`() {
        val payload = json.encodeToString(Shape.serializer(), Shape.Circle(radius = 1.0))

        val errors = schema.validate(payload, InputFormat.JSON)

        errors.shouldBeEmpty()
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            """{"kind":"Circle"}""",
            """{"kind":"Hexagon","radius":1.0}""",
            """{"kind":"Circle","radius":1.0,"color":"red"}""",
        ],
    )
    fun `should reject invalid payload`(payload: String) {
        val errors = schema.validate(payload, InputFormat.JSON)

        errors.shouldNotBeEmpty()
    }
}
