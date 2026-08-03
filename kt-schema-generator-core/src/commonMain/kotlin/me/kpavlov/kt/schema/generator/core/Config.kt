package me.kpavlov.kt.schema.generator.core

import me.kpavlov.kt.schema.generator.core.Config.descriptionAnnotationNames
import me.kpavlov.kt.schema.generator.core.Config.nameAnnotationNames
import me.kpavlov.kt.schema.generator.core.ir.PrimitiveKind

/**
 * Default opaque type names used across all platforms.
 * JVM overrides this by loading from `kt-schema.properties`.
 */
internal val DEFAULT_OPAQUE_TYPE_NAMES: Set<String> =
    setOf(
        "kotlinx.serialization.json.JsonElement",
        "kotlinx.serialization.json.JsonPrimitive",
        "kotlinx.serialization.json.JsonNull",
        // Jackson databind node hierarchy (Jackson 3.x `tools.jackson.databind`): abstract
        // tree types representing arbitrary JSON values — mapped to the empty schema `{}`.
        // Concrete leaf/numeric-abstraction node types with a well-defined primitive shape are
        // NOT here — see DEFAULT_PRIMITIVE_TYPE_KINDS below.
        "tools.jackson.databind.JsonNode",
        "tools.jackson.databind.node.ObjectNode",
        "tools.jackson.databind.node.ArrayNode",
        "tools.jackson.databind.node.ContainerNode",
        "tools.jackson.databind.node.ValueNode",
        "tools.jackson.databind.node.BaseJsonNode",
        "tools.jackson.databind.node.NullNode",
        "tools.jackson.databind.node.MissingNode",
        "tools.jackson.databind.node.POJONode",
    )

/**
 * Fully qualified names of third-party types with a single well-defined JSON primitive shape,
 * mapped to the [PrimitiveKind] they represent.
 *
 * A generic mechanism, not tied to any one framework — unlike [DEFAULT_OPAQUE_TYPE_NAMES] (types
 * with no fixed shape, mapped to the empty schema `{}`), every type listed here always represents
 * exactly one JSON primitive, so it resolves to the matching
 * [PrimitiveNode][me.kpavlov.kt.schema.generator.core.ir.PrimitiveNode] instead. Currently only
 * populated with the leaf/numeric-abstraction types from Jackson's databind node hierarchy
 * (`tools.jackson.databind.node`); other frameworks' fixed-shape wrapper types can be added the
 * same way.
 */
internal val DEFAULT_PRIMITIVE_TYPE_KINDS: Map<String, PrimitiveKind> =
    mapOf(
        "tools.jackson.databind.node.StringNode" to PrimitiveKind.STRING,
        "tools.jackson.databind.node.BinaryNode" to PrimitiveKind.STRING,
        "tools.jackson.databind.node.BooleanNode" to PrimitiveKind.BOOLEAN,
        "tools.jackson.databind.node.IntNode" to PrimitiveKind.INT,
        "tools.jackson.databind.node.ShortNode" to PrimitiveKind.INT,
        "tools.jackson.databind.node.LongNode" to PrimitiveKind.LONG,
        "tools.jackson.databind.node.BigIntegerNode" to PrimitiveKind.LONG,
        "tools.jackson.databind.node.NumericIntNode" to PrimitiveKind.LONG,
        "tools.jackson.databind.node.DoubleNode" to PrimitiveKind.DOUBLE,
        "tools.jackson.databind.node.FloatNode" to PrimitiveKind.FLOAT,
        "tools.jackson.databind.node.DecimalNode" to PrimitiveKind.DOUBLE,
        "tools.jackson.databind.node.NumericFPNode" to PrimitiveKind.DOUBLE,
        "tools.jackson.databind.node.NumericNode" to PrimitiveKind.DOUBLE,
    )

/**
 * Canonical set of fully qualified names of `kotlinx.serialization.json` types treated as
 * opaque JSON values (mapped to the empty schema `{}`).
 *
 * This is the single source of truth shared across modules and platforms:
 * - the JVM reflection introspector uses it as the fallback for [Config.opaqueTypeNames];
 * - the serialization- and KSP-based generators (in `kt-schema-generator-json`) default their
 *   opaque-type sets to this function, so all paths stay consistent.
 *
 * Exposed as a function (rather than a constant) so callers can build on it — e.g.
 * `opaqueSerialNames = defaultOpaqueTypeNames() + myCustomTypes` — without depending on an
 * inlined constant value. The JVM reflection path additionally lets users override the
 * effective list via the `introspector.opaque.type.names` property in `kt-schema.properties`.
 */
public fun defaultOpaqueTypeNames(): Set<String> = DEFAULT_OPAQUE_TYPE_NAMES

/**
 * Canonical map of fully qualified third-party type names to the [PrimitiveKind] they represent
 * (e.g. Jackson's `StringNode` -> `STRING`). Shared across the reflection and KSP introspectors so
 * both resolve these fixed-shape types to the same concrete primitive schema instead of the opaque
 * empty schema `{}` used for types with no fixed shape (see [defaultOpaqueTypeNames]).
 */
public fun defaultPrimitiveTypeKinds(): Map<String, PrimitiveKind> = DEFAULT_PRIMITIVE_TYPE_KINDS

/**
 * Configuration for schema generation.
 *
 * This object encapsulates the configuration that controls which annotations are recognized
 * as description providers and which annotation parameters contain description text.
 *
 * Configuration is loaded lazily from `kt-schema.properties` on the classpath.
 * If loading fails, the system falls back to built-in default values and continues to operate.
 *
 * ## Configuration Properties
 *
 * - `introspector.annotations.description.names`: Comma-separated list of annotation simple names
 *   to recognize as description providers
 * - `introspector.annotations.description.attributes`: Comma-separated list of annotation parameter
 *   names that contain description text
 *
 * ## Fallback Behavior
 *
 * If the configuration file is present but unreadable or contains invalid values, the system
 * automatically uses default values: Description, LLMDescription, JsonPropertyDescription,
 * JsonClassDescription, P for annotation names, and "value", "description" for attributes.
 * A completely absent `kt-schema.properties` resource is treated as a packaging error and is
 * not silently defaulted.
 */
internal expect object Config {
    /**
     * Ordered list of lowercase annotation simple-name glob patterns recognized as description
     * providers (e.g. `"Json*"` matches `JsonPropertyDescription`, `JsonClassDescription`, etc.).
     *
     * Annotations are matched case-insensitively by their simple name only (not fully qualified name).
     * This allows recognition of description annotations from multiple frameworks (kt-schema,
     * Jackson, LangChain4j, Koog, etc.) without requiring specific imports.
     *
     * Loaded lazily from the `introspector.annotations.description.names` property in
     * `kt-schema.properties`. If loading fails, falls back to built-in defaults.
     *
     * Default value: Description, LLMDescription, JsonPropertyDescription, JsonClassDescription, P
     */
    val descriptionAnnotationNames: List<String>

    /**
     * Ordered list of lowercase parameter names to check for description text.
     *
     * When an annotation matches [descriptionAnnotationNames], its parameters are inspected
     * for these attribute names to extract the description value. The first matching parameter
     * with a non-null String value is returned. Order determines priority — earlier entries
     * take precedence.
     *
     * Loaded lazily from the `introspector.annotations.description.attributes` property in
     * `kt-schema.properties`. If loading fails, falls back to built-in defaults.
     *
     * Default value: "value", "description"
     *
     * ## Examples
     * - For `@Description("User name")`, the "value" parameter contains "User name"
     * - For `@JsonPropertyDescription(description = "User email")`, the "description" parameter contains "User email"
     */
    val descriptionValueAttributes: List<String>

    /**
     * Ordered list of lowercase annotation simple-name glob patterns recognized as ignore markers.
     *
     * Classes annotated with any of these annotations are excluded from schema generation
     * (e.g., sealed subtypes omitted from polymorphic `oneOf` schemas).
     *
     * Loaded lazily from the `introspector.annotations.ignore.names` property in
     * `kt-schema.properties`. If loading fails, falls back to built-in defaults.
     *
     * Default value: SchemaIgnore, SerialSchemaIgnore, JsonIgnoreType, JsonIgnore
     */
    val ignoreAnnotationNames: List<String>

    /**
     * Ordered list of annotation name glob patterns recognized as name-override providers
     * (e.g., `@SerialName`).
     *
     * Patterns containing a dot (`.`) are treated as fully qualified names and matched
     * **case-sensitively** against the annotation's qualified name. Patterns without a dot
     * are matched **case-insensitively** against the annotation's simple name.
     *
     * Loaded lazily from the `introspector.annotations.name.names` property in
     * `kt-schema.properties`. If loading fails, falls back to built-in defaults.
     *
     * Default value: kotlinx.serialization.SerialName,
     *                com.fasterxml.jackson.annotation.JsonProperty,
     *                com.fasterxml.jackson.annotation.JsonTypeName
     */
    val nameAnnotationNames: List<String>

    /**
     * Ordered list of lowercase parameter names to check for name-override text.
     *
     * When an annotation matches [nameAnnotationNames], its parameters are inspected
     * for these attribute names to extract the override name value. Order determines
     * priority — earlier entries take precedence.
     *
     * Loaded lazily from the `introspector.annotations.name.attributes` property in
     * `kt-schema.properties`. If loading fails, falls back to built-in defaults.
     *
     * Default value: "value"
     */
    val nameValueAttributes: List<String>

    /**
     * Set of fully qualified class names of types that should be treated as opaque
     * JSON values (mapped to empty schema `{}`) during reflection-based introspection.
     *
     * These types have incompatible class structures for standard object or sealed-type
     * processing but represent arbitrary JSON values.
     *
     * Loaded lazily from the `introspector.opaque.type.names` property in
     * `kt-schema.properties`. If the property is missing or invalid, falls back to built-in
     * defaults; an absent configuration file is not silently defaulted.
     *
     * Default value: kotlinx.serialization.json.JsonElement,
     *                kotlinx.serialization.json.JsonPrimitive,
     *                kotlinx.serialization.json.JsonNull,
     *                tools.jackson.databind.JsonNode and its node hierarchy
     */
    val opaqueTypeNames: Set<String>

    /**
     * Ordered list of lowercase annotation simple names recognized as nullable markers
     * (e.g. `@Nullable`). A property carrying one of these annotations is treated as if its
     * type were nullable (`type: [T, "null"]` in the emitted schema), the same way Kotlin's `?`
     * is handled — primarily useful for front ends without native nullable types (e.g. Java/APT).
     *
     * Matching follows the same rules as [ignoreAnnotationNames]: simple names case-insensitive,
     * FQNs (containing a dot) case-sensitive. A single simple-name entry like `"Nullable"` already
     * matches `javax.annotation.Nullable`, `jakarta.annotation.Nullable`,
     * `org.jetbrains.annotations.Nullable`, `org.jspecify.annotations.Nullable`, etc., since they
     * all share that simple name.
     *
     * Loaded lazily from the `introspector.annotations.nullable.names` property in
     * `kt-schema.properties`. If loading fails, falls back to built-in defaults.
     *
     * Default value: Nullable
     */
    val nullableAnnotationNames: List<String>

    /**
     * Glob patterns (`*` matches any substring) matched against a property's resolved type's
     * simple class name. A match marks the property nullable, the same way Kotlin's `?` is
     * handled — e.g. a type literally named `EmailOpt` matches the default pattern `*Opt`.
     *
     * Case-sensitive: class names are case-sensitive by convention.
     *
     * Loaded lazily from the `introspector.nullable.type.names` property in
     * `kt-schema.properties`. If loading fails, falls back to built-in defaults.
     *
     * Default value: *Opt
     */
    val nullableTypeNamePatterns: List<String>

    /**
     * Ordered list of lowercase annotation simple names recognized as optional markers.
     * A property carrying one of these annotations is excluded from the emitted schema's
     * `required` array, the same way a Kotlin default value is handled — primarily useful for
     * front ends without native default-value support (e.g. Java/APT).
     *
     * Matching follows the same rules as [ignoreAnnotationNames].
     *
     * Loaded lazily from the `introspector.annotations.optional.names` property in
     * `kt-schema.properties`. If loading fails, falls back to built-in defaults.
     *
     * Default value: (none). Configure this explicitly when an annotation represents an
     * omitted property rather than a nullable value.
     */
    val optionalAnnotationNames: List<String>

    /**
     * Glob patterns (`*` matches any substring) matched against a property's resolved type's
     * simple class name. A match excludes the property from the emitted schema's `required`
     * array, the same way a Kotlin default value is handled.
     *
     * Case-sensitive: class names are case-sensitive by convention.
     *
     * Loaded lazily from the `introspector.optional.type.names` property in
     * `kt-schema.properties`. If loading fails, falls back to built-in defaults.
     *
     * Default value: (none) — unlike [nullableTypeNamePatterns], this convention is opt-in only,
     * since a type-name-based default here would silently exclude too many unrelated classes from
     * `required`.
     */
    val optionalTypeNamePatterns: List<String>

    /**
     * Annotation names recognized as enum-default-value markers (e.g. Jackson's
     * `@JsonEnumDefaultValue`), placed on a single enum constant to mark it as that enum type's
     * default value — emitted as the `default` keyword on the enum's own schema.
     *
     * Matching follows the same rules as [nameAnnotationNames]: simple names case-insensitive,
     * FQNs (containing a dot) case-sensitive.
     *
     * Loaded lazily from the `introspector.annotations.enumDefault.names` property in
     * `kt-schema.properties`. If loading fails, falls back to built-in defaults.
     *
     * Default value: com.fasterxml.jackson.annotation.JsonEnumDefaultValue
     */
    val enumDefaultAnnotationNames: List<String>

    /**
     * Annotation names recognized as default-value providers (e.g. Jackson's
     * `@JsonProperty(defaultValue = "...")`), primarily useful for front ends without native
     * default-value support (e.g. APT, KSP) — for reflection, a real Kotlin default value always
     * takes precedence when both are present.
     *
     * Matching follows the same rules as [nameAnnotationNames].
     *
     * Loaded lazily from the `introspector.annotations.defaultValue.names` property in
     * `kt-schema.properties`. If loading fails, falls back to built-in defaults.
     *
     * Default value: com.fasterxml.jackson.annotation.JsonProperty
     */
    val defaultValueAnnotationNames: List<String>

    /**
     * Ordered list of lowercase annotation parameter names that may contain default-value text.
     *
     * Loaded lazily from the `introspector.annotations.defaultValue.attributes` property in
     * `kt-schema.properties`. If loading fails, falls back to built-in defaults.
     *
     * Default value: defaultValue
     */
    val defaultValueAttributes: List<String>
}
