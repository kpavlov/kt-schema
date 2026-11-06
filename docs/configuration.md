---
title: Configuration
description: Choose a schema flavour (strict, lenient, openapi) and configure which annotations kt-schema recognizes from Jackson, LangChain4j, Koog and your own code.
menus:
  main:
    name: Configuration
    weight: 60
---

# Configuration

## Schema configuration

The `me.kpavlov.kt.schema.config` processor option selects the schema flavour for both KSP and the Java annotation
processor. Unset, classes use `strict` and KSP function schemas use `FunctionCallingSchemaConfig.Strict`. The value is
a case-insensitive shortcut:

| Value     | Schema                                                                                                                     |
|:----------|:---------------------------------------------------------------------------------------------------------------------------|
| `strict`  | All fields required, nullable types as `["string", "null"]`, `additionalProperties: false`.                                |
| `lenient` | Compact and permissive: only non-nullable fields without defaults are required, no null markers, extra properties allowed. |
| `openapi` | OpenAPI 3.x flavour: nullable types use `"nullable": true`.                                                                |

```kotlin
// KSP
ksp {
    arg("me.kpavlov.kt.schema.config", "lenient")
}

// Java annotation processor
tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-Ame.kpavlov.kt.schema.config=lenient")
}
```

Instead of a shortcut, the value can be the fully qualified name of your own `JsonSchemaConfig` class: a Kotlin
`object`, or a class with a public no-arg constructor. It must be on the processor classpath, i.e. provided by a
`ksp(...)` / `annotationProcessor(...)` dependency. For function schemas (KSP), a plain `JsonSchemaConfig` is copied
into a
`FunctionCallingSchemaConfig` with `strictMode = false`. An unresolvable value fails the build with an error listing the
valid shortcuts.

> [!IMPORTANT]
> `lenient` schemas express `null` as an absent field: a nullable property has no null marker and is never required.
> Nullable collection elements and map values can't be omitted, so they keep an `anyOf` branch with `{"type": "null"}`.
> Serialize payloads with `explicitNulls = false` (e.g. `Json { explicitNulls = false }`), otherwise an explicit
> `null` does not validate.

## Multi-Framework Annotation Support

**You don't need to change your existing code!**

kt-schema recognizes description annotations from multiple frameworks by their **simple name**, allowing you to
generate schemas from code that uses annotations from other libraries.

### Supported Annotations

The library automatically recognizes these description annotations by default:

| Annotation                                                 | Simple Name               | Library/Framework | Example                               |
|------------------------------------------------------------|---------------------------|-------------------|---------------------------------------|
| `me.kpavlov.kt.schema.Description`                         | `Description`             | kt-schema         | `@Description("User name")`           |
| `ai.koog.agents.core.tools.annotations.LLMDescription`     | `LLMDescription`          | Koog AI agents    | `@LLMDescription("Query text")`       |
| `com.fasterxml.jackson.annotation.JsonPropertyDescription` | `JsonPropertyDescription` | Jackson           | `@JsonPropertyDescription("Email")`   |
| `com.fasterxml.jackson.annotation.JsonClassDescription`    | `JsonClassDescription`    | Jackson           | `@JsonClassDescription("User model")` |
| `dev.langchain4j.model.output.structured.P`                | `P`                       | LangChain4j       | `@P("Search query")`                  |

Beyond descriptions, kt-schema also recognizes **name overrides** and **ignore markers** by default:

**Name overrides** — matched by **fully qualified name** (case-sensitive):

| Annotation                                      | Maps to                                                        |
|-------------------------------------------------|----------------------------------------------------------------|
| `com.fasterxml.jackson.annotation.JsonProperty` | property **name** in `properties`/`required`                   |
| `com.fasterxml.jackson.annotation.JsonTypeName` | polymorphic subtype name (`$defs` key + discriminator `const`) |

**Ignore markers** — matched by **simple name** (case-insensitive), regardless of package:

| Annotation                 | Maps to                                     |
|----------------------------|---------------------------------------------|
| `JsonIgnore` (any package) | excludes the property/field from the schema |

**Default values** — matched by **fully qualified name** (case-sensitive); mainly useful for the KSP and Java APT
processors, which can't evaluate a real Kotlin/Java default-value expression at compile time:

| Annotation                                                       | Maps to                                                                                                    |
|------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------|
| `com.fasterxml.jackson.annotation.JsonEnumDefaultValue`          | the enum constant it's placed on becomes that enum type's `default` (on the enum's own schema, in `$defs`) |
| `com.fasterxml.jackson.annotation.JsonProperty(defaultValue=..)` | the property's default value, internally                                                                   |

> [!NOTE]
> The `@JsonEnumDefaultValue`-derived `default` always appears in the generated output, since it's a property of
> the enum's own schema. `@JsonProperty(defaultValue = "...")` is recognized and populates the property internally,
> but doesn't appear in — or exclude the property from `required` in — the KSP/APT processors' standard generated
> resource, since both mark every property required regardless of default presence. It does apply when building a
> schema through `TypeGraphToJsonSchemaTransformer` with a non-strict `JsonSchemaConfig` yourself.

### How It Works

Each annotation category (description, name-override, ignore) is configured with its own list of recognized names.
Within a list, entries containing a dot are matched **case-sensitively** against the fully qualified name; entries
without a dot are matched **case-insensitively** against the simple name. This means:

- ✅ No code changes needed to generate schemas from existing annotated classes
- ✅ Can migrate between annotation libraries without modifying code
- ✅ Generate schemas for third-party code that uses different annotations
- ✅ Use your preferred annotation library while still getting schema generation

> [!NOTE]
> Multi-framework annotation recognition applies to the **KSP processor**, the **Java APT processor**
> (via `AptIntrospectionContext`, which recognizes the same Jackson defaults), and **reflection-based generators**.
> The serialization-based generator (`SerializationClassJsonSchemaGenerator`) can only access annotations marked
> with `@SerialInfo` — see [Custom description extraction](serializable.md#custom-description-extraction) for
details.

### Customizing Annotation Detection

Annotation detection is configurable via `kt-schema.properties` loaded from the classpath.
The configuration file is **optional** — if not provided or fails to load, the library uses sensible defaults.

#### Default Configuration

By default, the library recognizes:

**Description annotations**: Description, LLMDescription, JsonPropertyDescription, JsonClassDescription, P **Description
attributes**: value, description **Ignore annotations**: SchemaIgnore, SerialSchemaIgnore, JsonIgnoreType, JsonIgnore
**Name-override annotations**: kotlinx.serialization.SerialName, com.fasterxml.jackson.annotation.JsonProperty,
com.fasterxml.jackson.annotation.JsonTypeName **Name-override attributes**: value **Enum-default annotations**:
com.fasterxml.jackson.annotation.JsonEnumDefaultValue **Default-value annotations**:
com.fasterxml.jackson.annotation.JsonProperty **Default-value attributes**: defaultValue **Discriminator annotations**:
kotlinx.serialization.json.JsonClassDiscriminator, com.fasterxml.jackson.annotation.JsonTypeInfo **Discriminator
attributes**: discriminator, property

> [!NOTE]
> Annotation names containing a dot (e.g., `kotlinx.serialization.SerialName`) are matched
> **case-sensitively** against the annotation's fully qualified name. Names without a dot
> are matched **case-insensitively** by simple name.

#### Adding Custom Annotations

To customize, place `kt-schema.properties` in your project's resources:

```properties
# Add your custom annotations to the defaults
introspector.annotations.description.names=Description,MyCustomAnnotation,DocString
introspector.annotations.description.attributes=value,description,text
# Name-override annotations (use FQN for precise matching)
introspector.annotations.name.names=kotlinx.serialization.SerialName
introspector.annotations.name.attributes=value
# Default-value annotations (use FQN for precise matching)
introspector.annotations.enumDefault.names=com.fasterxml.jackson.annotation.JsonEnumDefaultValue
introspector.annotations.defaultValue.names=com.fasterxml.jackson.annotation.JsonProperty
introspector.annotations.defaultValue.attributes=defaultValue
# Validation constraint annotations, mapped by simple name (Min, Max, Size, Pattern, ...)
introspector.annotations.constraint.names=jakarta.validation.constraints.*,javax.validation.constraints.*
# Polymorphic discriminator property name (reflection and KSP front ends; defaults to "type")
introspector.annotations.discriminator.names=kotlinx.serialization.json.JsonClassDiscriminator,com.fasterxml.jackson.annotation.JsonTypeInfo
introspector.annotations.discriminator.attributes=discriminator,property
```

**Note**: The library falls back to built-in defaults if the configuration file is missing or cannot be loaded.

#### Example: Adding Support for a Custom Framework

```kotlin
// Your custom annotation
package com.mycompany.annotations

annotation class ApiDoc(val text: String)

// Usage in your models
@ApiDoc(text = "Customer profile information")
data class Customer(
    @ApiDoc(text = "Unique customer identifier")
    val id: Long,
    val name: String
)
```

Update `kt-schema.properties`:

```properties
introspector.annotations.description.names=Description,ApiDoc
introspector.annotations.description.attributes=value,description,text
```

Now the schema generator will recognize `@ApiDoc` and extract descriptions from its `text` parameter.

### Example: Reusing Jackson Annotations

If your project already uses Jackson for JSON serialization, you can generate schemas from existing Jackson-annotated
classes without any modifications. This is particularly useful for REST APIs and Spring Boot applications where Jackson
annotations are already present.

<!--- CLEAR -->
<!--- INCLUDE
import me.kpavlov.kt.schema.generator.json.ReflectionClassJsonSchemaGenerator

annotation class JsonClassDescription(val value: String)
annotation class JsonPropertyDescription(val value: String)
-->

```kotlin
// Existing code with Jackson annotations - NO CHANGES NEEDED!
@JsonClassDescription("Customer profile data")
data class Customer(
    @JsonPropertyDescription("Unique customer ID")
    val id: Long,

    @JsonPropertyDescription("Full name")
    val name: String,

    @JsonPropertyDescription("Contact email")
    val email: String
)

// Generate JSON schema without modifying the code
val generator = me.kpavlov.kt.schema.generator.json.ReflectionClassJsonSchemaGenerator.Default
val schema = generator.generateSchema(Customer::class)

// Schema includes all Jackson descriptions!
```

<!--- KNIT example-knit-configuration-01.kt -->

### Example: LangChain4j Integration

LangChain4j uses the `@P` annotation for parameter descriptions in AI function calling. The library recognizes these
annotations automatically, enabling seamless integration with existing LangChain4j codebases.

<!--- CLEAR -->
<!--- INCLUDE
import me.kpavlov.kt.schema.generator.json.ReflectionFunctionCallingSchemaGenerator

annotation class P(val value: String)
-->

```kotlin
// Code using LangChain4j annotations
data class SearchQuery(
    @P("Search terms")
    val query: String,

    @P("Maximum results to return")
    val limit: Int = 10
)

// Generate schema for LLM function calling
val generator = ReflectionFunctionCallingSchemaGenerator.Default
val schema = generator.generateSchema(SearchQuery::class.constructors.first())
```

<!--- KNIT example-knit-configuration-02.kt -->

### Example: Koog AI Agents

Koog AI framework uses `@LLMDescription` for documenting agent tools and parameters. The library supports both the
verbose `description =` syntax and the shorthand form, making migration from Koog straightforward.

<!--- CLEAR -->
<!--- INCLUDE
import me.kpavlov.kt.schema.Schema

annotation class LLMDescription(val value: String = "", val description: String = "")
-->

```kotlin
@LLMDescription(description = "Product with pricing information")
@Schema
data class Product(
    @LLMDescription(description = "Product identifier")
    val id: Long,

    @LLMDescription("Product name")
    val name: String,

    @LLMDescription("Unit price")
    val price: Double,
)
```

<!--- KNIT example-knit-configuration-03.kt -->

### Precedence Rules

If multiple description annotations are present on the same element, the **first matching annotation in source-code
order** wins. There is no special priority for `@Description` over other recognized annotations — whichever recognized
annotation appears first on the declaration is used.

**Tip**: For predictability, place only one description annotation per element. If you use `@Description` from
kotlinx-schema alongside another recognized annotation, put `@Description` first in source order to ensure it wins.

## See Also

- [KSP Configuration Guide](ksp.md) and [Java Annotation Processor Guide](apt.md) — where the `config` option is set
- [Validation Constraints](constraints.md) — constraint annotation packages are configured the same way
- [Serialization-Based Schema Generation](serializable.md#configuration) — `JsonSchemaConfig` presets at runtime
