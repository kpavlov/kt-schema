package me.kpavlov.kt.schema.generator.json

/**
 * Configuration for function calling schema transformers.
 *
 * Extends [JsonSchemaConfig] with defaults optimized for LLM function calling.
 * By default, uses strict mode settings to comply with OpenAI function calling requirements.
 *
 * @property strictMode Whether to set `strict: true` flag in function calling schema output.
 *                      Required for OpenAI Structured Outputs and function calling strict mode.
 *                      Requires [allowAdditionalProperties] to be `false`.
 *
 * @see [OpenAI Function Calling](https://platform.openai.com/docs/guides/function-calling)
 */
public class FunctionCallingSchemaConfig(
    respectDefaultPresence: Boolean = JsonSchemaConfig.Default.respectDefaultPresence,
    requireNullableFields: Boolean = JsonSchemaConfig.Default.requireNullableFields,
    useUnionTypes: Boolean = JsonSchemaConfig.Default.useUnionTypes,
    useNullableField: Boolean = JsonSchemaConfig.Default.useNullableField,
    includePolymorphicDiscriminator: Boolean = JsonSchemaConfig.Default.includePolymorphicDiscriminator,
    /**
     * Whether to set the `strict: true` flag in function calling schema output.
     *
     * When `true`, the generated schema includes `"strict": true` in the JSON output.
     * Required for OpenAI Structured Outputs and function calling strict mode.
     *
     * Default: `true`
     */
    public val strictMode: Boolean = true,
    allowAdditionalProperties: Boolean = false,
) : JsonSchemaConfig(
        respectDefaultPresence = respectDefaultPresence,
        requireNullableFields = requireNullableFields,
        useUnionTypes = useUnionTypes,
        useNullableField = useNullableField,
        includePolymorphicDiscriminator = includePolymorphicDiscriminator,
        allowAdditionalProperties = allowAdditionalProperties,
    ) {
    // Binary compatibility with callers compiled before allowAdditionalProperties was added
    @Deprecated("Kept for binary compatibility", level = DeprecationLevel.HIDDEN)
    public constructor(
        respectDefaultPresence: Boolean,
        requireNullableFields: Boolean,
        useUnionTypes: Boolean,
        useNullableField: Boolean,
        includePolymorphicDiscriminator: Boolean,
        strictMode: Boolean,
    ) : this(
        respectDefaultPresence = respectDefaultPresence,
        requireNullableFields = requireNullableFields,
        useUnionTypes = useUnionTypes,
        useNullableField = useNullableField,
        includePolymorphicDiscriminator = includePolymorphicDiscriminator,
        strictMode = strictMode,
        allowAdditionalProperties = false,
    )

    init {
        require(!(strictMode && allowAdditionalProperties)) {
            "strictMode requires allowAdditionalProperties = false"
        }
    }

    public companion object {
        /**
         * Strict configuration for function calling schemas (strict mode enabled).
         *
         * - Strict flag: enabled (`strict: true` in output)
         * - All fields required including nullables
         * - Union type nullable handling: `["string", "null"]`
         */
        public val Strict: FunctionCallingSchemaConfig =
            FunctionCallingSchemaConfig(
                respectDefaultPresence = JsonSchemaConfig.Strict.respectDefaultPresence,
                requireNullableFields = JsonSchemaConfig.Strict.requireNullableFields,
                useUnionTypes = JsonSchemaConfig.Strict.useUnionTypes,
                useNullableField = JsonSchemaConfig.Strict.useNullableField,
                includePolymorphicDiscriminator = JsonSchemaConfig.Strict.includePolymorphicDiscriminator,
                strictMode = true,
            )

        /**
         * Non-strict configuration for function calling schemas.
         *
         * - Strict flag: disabled
         * - Only non-nullable fields required
         * - Union type nullable handling
         */
        public val Simple: FunctionCallingSchemaConfig =
            FunctionCallingSchemaConfig(
                respectDefaultPresence = true,
                requireNullableFields = false,
                useUnionTypes = true,
                useNullableField = false,
                includePolymorphicDiscriminator = false,
                strictMode = false,
            )

        /**
         * Compact, permissive configuration for function calling schemas.
         *
         * Mirrors [JsonSchemaConfig.Lenient]: only non-nullable fields without defaults are required,
         * nullable properties carry no null marker, and extra properties are allowed.
         * The strict flag is disabled, so a missing field means `null`.
         */
        public val Lenient: FunctionCallingSchemaConfig =
            FunctionCallingSchemaConfig(
                respectDefaultPresence = JsonSchemaConfig.Lenient.respectDefaultPresence,
                requireNullableFields = JsonSchemaConfig.Lenient.requireNullableFields,
                useUnionTypes = JsonSchemaConfig.Lenient.useUnionTypes,
                useNullableField = JsonSchemaConfig.Lenient.useNullableField,
                includePolymorphicDiscriminator = JsonSchemaConfig.Lenient.includePolymorphicDiscriminator,
                strictMode = false,
                allowAdditionalProperties = JsonSchemaConfig.Lenient.allowAdditionalProperties,
            )

        /**
         * OpenAPI 3.x flavoured configuration for function calling schemas.
         *
         * Mirrors [JsonSchemaConfig.OpenAPI] (nullable types use `"nullable": true`), except for the
         * OpenAPI polymorphic discriminator, which function calling schemas do not support.
         * The strict flag is disabled.
         */
        public val OpenAPI: FunctionCallingSchemaConfig =
            FunctionCallingSchemaConfig(
                respectDefaultPresence = JsonSchemaConfig.OpenAPI.respectDefaultPresence,
                requireNullableFields = JsonSchemaConfig.OpenAPI.requireNullableFields,
                useUnionTypes = JsonSchemaConfig.OpenAPI.useUnionTypes,
                useNullableField = JsonSchemaConfig.OpenAPI.useNullableField,
                includePolymorphicDiscriminator = JsonSchemaConfig.OpenAPI.includePolymorphicDiscriminator,
                strictMode = false,
                allowAdditionalProperties = JsonSchemaConfig.OpenAPI.allowAdditionalProperties,
            )

        /**
         * Default configuration for function calling schemas.
         */
        public val Default: FunctionCallingSchemaConfig = FunctionCallingSchemaConfig()
    }
}
