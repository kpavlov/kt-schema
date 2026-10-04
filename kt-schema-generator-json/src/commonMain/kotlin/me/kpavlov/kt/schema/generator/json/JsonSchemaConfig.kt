package me.kpavlov.kt.schema.generator.json

/**
 * Configuration for JSON Schema transformers.
 *
 * Controls schema generation behavior with individual flags for nullable handling
 * and required field handling. Use the [Strict] preset for full JSON Schema Draft 2020-12 compliance.
 *
 * ## Configuration Flags
 *
 * ### Required Field Behavior
 *
 * | respectDefaultPresence | requireNullableFields | Behavior |
 * |------------------------|-----------------------|----------|
 * | true  | false | Fields without defaults required; nullable fields with defaults optional |
 * | true  | true  | Fields without defaults required; nullable fields always required |
 * | false | true  | All fields required (including nullables) |
 * | false | false | Only non-nullable fields required |
 *
 * ### Nullable Type Representation
 *
 * | useUnionTypes | useNullableField | Output |
 * |---------------|------------------|--------|
 * | true | false | `{"type": ["string", "null"]}` (JSON Schema Draft 2020-12) |
 * | false | true | `{"type": "string", "nullable": true}` (legacy OpenAPI) |
 * | false | false | `{"type": "string"}` (no nullable indication); nullable properties are omitted from `required` |
 *
 * @see [JSON Schema Draft 2020-12](https://json-schema.org/draft/2020-12/json-schema-core.html)
 * @author Konstantin Pavlov
 */
public open class JsonSchemaConfig(
    /**
     * Whether to derive required fields from each property's optionality and default value.
     *
     * When `true`: A property is required unless it is optional (it has a Kotlin default value or an
     * explicit optional marker) or carries a known default; constants are always required. If
     * [requireNullableFields] is also `true`, nullable fields are additionally required even when
     * they carry a default value. A `default` is emitted only for properties that are not required.
     *
     * When `false`: Uses [requireNullableFields] to determine required field behavior, and no
     * `default` is emitted for required fields.
     *
     * Default: `true`
     */
    public val respectDefaultPresence: Boolean = true,
    /**
     * Whether nullable fields must be present in JSON.
     *
     * When [respectDefaultPresence] is `true`: additionally requires nullable fields
     * even when they have a default value (e.g. `val x: String? = null`).
     * A required nullable field keeps its null marker (`["string", "null"]` or `"nullable": true`).
     *
     * When [respectDefaultPresence] is `false`:
     * - `true`: All fields are required (must be present, can be null).
     * - `false`: Only non-nullable fields are required.
     *
     * Example with `requireNullableFields = true`:
     * ```kotlin
     * fun writeLog(level: String, exception: String? = null)
     * ```
     * Generates:
     * ```json
     * {
     *   "required": ["level", "exception"],
     *   "properties": {
     *     "level": { "type": "string" },
     *     "exception": { "type": ["string", "null"] }
     *   }
     * }
     * ```
     *
     * Default: `false`
     */
    public val requireNullableFields: Boolean = false,
    /**
     * Whether to use union types for nullable fields.
     *
     * When `true`: Generates `["string", "null"]` (JSON Schema Draft 2020-12 standard).
     * When `false`: Uses nullable field instead (see [useNullableField]); with both disabled,
     * nullable properties carry no null marker and are never required; nullable collection elements
     * and map values get an `anyOf` branch with `{"type": "null"}`.
     *
     * Default: `true`
     */
    public val useUnionTypes: Boolean = true,
    /**
     * Whether to emit the nullable field for nullable types.
     *
     * When `true`: Adds `"nullable": true` (legacy OpenAPI compatibility).
     * When `false`: Omits nullable field (standard JSON Schema).
     *
     * **Note**: Ignored when [useUnionTypes] is `true`.
     *
     * Default: `false`
     */
    public val useNullableField: Boolean = false,
    /**
     * Whether to include a type discriminator field in polymorphic schemas.
     *
     * When enabled, each polymorphic subtype schema gets an additional `"type"` property
     * containing a constant string equal to the subtype's simple class name.
     *
     * It's a good practice to enable it by default
     **/
    public val includePolymorphicDiscriminator: Boolean = true,
    /**
     * Whether to include discriminator in polymorphic schemas.
     *
     * When `true`: Includes discriminator object in oneOf schemas (OpenAPI 3.x compatibility).
     * When `false`: Omits discriminator (standard JSON Schema Draft 2020-12).
     *
     * Note: Discriminator is an OpenAPI extension, not part of JSON Schema specification.
     * Note: to enable this option, [includePolymorphicDiscriminator] must also be `true`.
     *
     * Default: `false`
     */
    public val includeOpenAPIPolymorphicDiscriminator: Boolean = false,
    /**
     * Whether object schemas allow properties that are not declared.
     *
     * When `true`: object schemas omit `additionalProperties` (JSON Schema allows them by default).
     * When `false`: object schemas emit `"additionalProperties": false`.
     *
     * Map schemas are unaffected: they always describe their value type in `additionalProperties`.
     *
     * Default: `false`
     */
    public val allowAdditionalProperties: Boolean = false,
    /**
     * Whether `$defs` keys, `$ref` targets, the root `$id` and discriminator `const` values use the
     * shortest unique dotted suffix of the type's qualified name (`com.acme.Payload` becomes `Payload`).
     *
     * Names set explicitly by annotations such as `@JsonTypeName` are used as is. Colliding names
     * grow one package segment at a time until unique, ending with the full qualified name.
     * Schemas generated from kotlinx.serialization descriptors are never shortened.
     *
     * Default: `false`
     */
    public val shortDefinitionNames: Boolean = false,
) {
    // Binary compatibility with callers compiled before allowAdditionalProperties was added
    @Deprecated("Kept for binary compatibility", level = DeprecationLevel.HIDDEN)
    public constructor(
        respectDefaultPresence: Boolean,
        requireNullableFields: Boolean,
        useUnionTypes: Boolean,
        useNullableField: Boolean,
        includePolymorphicDiscriminator: Boolean,
        includeOpenAPIPolymorphicDiscriminator: Boolean,
    ) : this(
        respectDefaultPresence = respectDefaultPresence,
        requireNullableFields = requireNullableFields,
        useUnionTypes = useUnionTypes,
        useNullableField = useNullableField,
        includePolymorphicDiscriminator = includePolymorphicDiscriminator,
        includeOpenAPIPolymorphicDiscriminator = includeOpenAPIPolymorphicDiscriminator,
        allowAdditionalProperties = false,
        shortDefinitionNames = false,
    )

    init {
        // Validate flag combinations
        require(!useUnionTypes || !useNullableField) {
            "Cannot use both useUnionTypes and useNullableField. " +
                "Choose one: union types [\"string\", \"null\"] OR nullable field."
        }

        require(!includeOpenAPIPolymorphicDiscriminator || includePolymorphicDiscriminator) {
            "includeOpenAPIPolymorphicDiscriminator requires includePolymorphicDiscriminator to be enabled"
        }
    }

    public companion object {
        /**
         * Default configuration for standard JSON Schema Draft 2020-12 generation.
         *
         * - Uses default presence detection (fields with defaults are optional)
         * - Uses union types for nullable fields: `["string", "null"]`
         * - Nullable fields without a default are required (must be present, may be `null`)
         *
         * **Note**: Works best with reflection-based introspection, which also emits default values.
         * KSP knows that a default exists but not its value, so such fields are optional without a `default`.
         */
        public val Default: JsonSchemaConfig =
            JsonSchemaConfig()

        /**
         * Configuration where all fields are required regardless of Kotlin default values.
         *
         *  - `requireNullableFields = true` — all fields in required array (including nullables)
         *  - `useUnionTypes = true` — union types for nullable fields: `["string", "null"]`
         *  - Type discriminators are enabled for polymorphic types
         *
         * Use this when generating schemas for OpenAI function calling APIs with strict mode enabled,
         * or any other schema consumer that requires all properties to be present in the JSON object.
         *
         * See [JSON Schema Draft 2020-12](https://json-schema.org/draft/2020-12/json-schema-core.html)
         */
        public val Strict: JsonSchemaConfig =
            JsonSchemaConfig(
                respectDefaultPresence = false,
                requireNullableFields = true,
                useUnionTypes = true,
                useNullableField = false,
                includePolymorphicDiscriminator = true,
                includeOpenAPIPolymorphicDiscriminator = false,
            )

        /**
         * Configuration for OpenAPI 3.x compatibility:
         *  - `respectDefaultPresence = true` - respect default values when available
         *  - `requireNullableFields = false` - nullable fields are optional
         *  - `useUnionTypes = false` - use nullable field instead of union types
         *  - `useNullableField = true` - emit "nullable": true for OpenAPI
         *  - `includeDiscriminator = true` - include discriminator for polymorphic types
         *
         * Use this when generating schemas for OpenAPI 3.x specifications.
         * OpenAPI 3.x uses a subset of JSON Schema with some extensions.
         *
         * See [OpenAPI 3.1 Specification](https://spec.openapis.org/oas/v3.1.0)
         */
        public val OpenAPI: JsonSchemaConfig =
            JsonSchemaConfig(
                respectDefaultPresence = true,
                requireNullableFields = false,
                useUnionTypes = false,
                useNullableField = true,
                includePolymorphicDiscriminator = true,
                includeOpenAPIPolymorphicDiscriminator = true,
            )

        /**
         * Compact, permissive configuration: only non-nullable fields that are neither optional nor have a
         * known default are required, nullable properties carry no null marker, and extra properties are
         * allowed. Nullable collection elements and map values keep an `anyOf` null branch, since they
         * cannot be omitted.
         *
         * An absent field means `null`, so serialize payloads with `explicitNulls = false` (Jackson:
         * `NON_NULL`); an explicit `null` fails validation, since there is no null marker. A `null`
         * default is never emitted for the same reason.
         *
         *  - `respectDefaultPresence = true` - fields with defaults are optional
         *  - `requireNullableFields = false` - nullable fields are optional
         *  - `useUnionTypes = false`, `useNullableField = false` - no null markers
         *  - `allowAdditionalProperties = true` - object schemas omit `additionalProperties`
         *  - `shortDefinitionNames = true` - `$defs` keys and `$ref`s use short type names
         *  - Type discriminators are enabled for polymorphic types
         */
        public val Lenient: JsonSchemaConfig =
            JsonSchemaConfig(
                respectDefaultPresence = true,
                requireNullableFields = false,
                useUnionTypes = false,
                useNullableField = false,
                includePolymorphicDiscriminator = true,
                includeOpenAPIPolymorphicDiscriminator = false,
                allowAdditionalProperties = true,
                shortDefinitionNames = true,
            )
    }

    override fun toString(): String =
        "JsonSchemaConfig(" +
            "respectDefaultPresence=$respectDefaultPresence, " +
            "requireNullableFields=$requireNullableFields, " +
            "useUnionTypes=$useUnionTypes, " +
            "useNullableField=$useNullableField, " +
            "includePolymorphicDiscriminator=$includePolymorphicDiscriminator, " +
            "includeOpenAPIPolymorphicDiscriminator=$includeOpenAPIPolymorphicDiscriminator, " +
            "allowAdditionalProperties=$allowAdditionalProperties, " +
            "shortDefinitionNames=$shortDefinitionNames" +
            ")"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is JsonSchemaConfig) return false

        if (respectDefaultPresence != other.respectDefaultPresence) return false
        if (requireNullableFields != other.requireNullableFields) return false
        if (useUnionTypes != other.useUnionTypes) return false
        if (useNullableField != other.useNullableField) return false
        if (includePolymorphicDiscriminator != other.includePolymorphicDiscriminator) return false
        if (includeOpenAPIPolymorphicDiscriminator != other.includeOpenAPIPolymorphicDiscriminator) return false
        if (allowAdditionalProperties != other.allowAdditionalProperties) return false
        if (shortDefinitionNames != other.shortDefinitionNames) return false

        return true
    }

    override fun hashCode(): Int {
        var result = respectDefaultPresence.hashCode()
        result = 31 * result + requireNullableFields.hashCode()
        result = 31 * result + useUnionTypes.hashCode()
        result = 31 * result + useNullableField.hashCode()
        result = 31 * result + includePolymorphicDiscriminator.hashCode()
        result = 31 * result + includeOpenAPIPolymorphicDiscriminator.hashCode()
        result = 31 * result + allowAdditionalProperties.hashCode()
        result = 31 * result + shortDefinitionNames.hashCode()
        return result
    }
}

internal fun JsonSchemaConfig.withoutShortDefinitionNames(): JsonSchemaConfig =
    if (!shortDefinitionNames) {
        this
    } else {
        JsonSchemaConfig(
            respectDefaultPresence = respectDefaultPresence,
            requireNullableFields = requireNullableFields,
            useUnionTypes = useUnionTypes,
            useNullableField = useNullableField,
            includePolymorphicDiscriminator = includePolymorphicDiscriminator,
            includeOpenAPIPolymorphicDiscriminator = includeOpenAPIPolymorphicDiscriminator,
            allowAdditionalProperties = allowAdditionalProperties,
            shortDefinitionNames = false,
        )
    }
