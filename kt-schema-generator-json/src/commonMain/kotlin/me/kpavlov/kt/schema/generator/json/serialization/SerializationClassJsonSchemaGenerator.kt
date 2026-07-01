package me.kpavlov.kt.schema.generator.json.serialization

import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import me.kpavlov.kt.schema.generator.core.AbstractSchemaGenerator
import me.kpavlov.kt.schema.generator.json.JsonSchemaConfig
import me.kpavlov.kt.schema.generator.json.TypeGraphToJsonSchemaTransformer
import me.kpavlov.kt.schema.json.JsonSchema
import kotlin.reflect.KClass

/**
 * A generator for producing JSON Schema representations from kotlinx.serialization descriptors.
 *
 * This class utilizes kotlinx.serialization introspection to analyze [SerialDescriptor] instances
 * and generate JSON Schema objects. It is built on top of the `AbstractSchemaGenerator` and works
 * with a configurable `JsonSchemaConfig` to define schema generation behavior.
 *
 * @constructor Creates an instance of `SerializationClassJsonSchemaGenerator`.
 * @param json The [Json] instance used for serializing schema objects.
 * @param introspectorConfig Configuration for introspecting serial descriptors.
 * @param jsonSchemaConfig Configuration for generating JSON Schemas, such as formatting details
 * and handling of optional nullable properties. Defaults to [JsonSchemaConfig.Default].
 */
public class SerializationClassJsonSchemaGenerator(
    private val json: Json = Json.Default,
    introspectorConfig: SerializationClassSchemaIntrospector.Config = SerializationClassSchemaIntrospector.Config(),
    jsonSchemaConfig: JsonSchemaConfig = JsonSchemaConfig.Default,
) : AbstractSchemaGenerator<SerialDescriptor, JsonSchema, SerializationClassSchemaIntrospector.Config>(
        introspector = SerializationClassSchemaIntrospector(introspectorConfig, json),
        typeGraphTransformer =
            TypeGraphToJsonSchemaTransformer(
                config = jsonSchemaConfig,
                json = json,
            ),
    ) {
    override fun getRootName(target: SerialDescriptor): String = target.unwrapSerialName()

    override fun targetType(): KClass<SerialDescriptor> = SerialDescriptor::class

    override fun schemaType(): KClass<JsonSchema> = JsonSchema::class

    override fun encodeToString(schema: JsonSchema): String = json.encodeToString(schema)

    public companion object {
        /**
         * A default instance of the [SerializationClassJsonSchemaGenerator] class, preconfigured
         * with the default settings defined in [JsonSchemaConfig.Default].
         *
         * This instance can be used to generate JSON schema representations from
         * kotlinx.serialization descriptors. It simplifies the creation of schemas
         * without requiring explicit configuration.
         */
        public val Default: SerializationClassJsonSchemaGenerator = SerializationClassJsonSchemaGenerator()

        /**
         * Generates a JSON Schema for the given `@Serializable` type [T], using the provided [generator].
         *
         * Example:
         * ```kotlin
         * @Serializable
         * data class Person(val name: String, val age: Int)
         *
         * val schema = SerializationClassJsonSchemaGenerator.jsonSchemaOf<Person>()
         * ```
         */
        public inline fun <reified T> jsonSchemaOf(
            generator: SerializationClassJsonSchemaGenerator = SerializationClassJsonSchemaGenerator.Default,
        ): JsonSchema = generator.generateSchema(serializer<T>().descriptor)
    }
}
