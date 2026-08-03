# Java Annotation Processor

**Table of contents**
<!--- TOC -->

* [Setup](#setup)
  * [Maven](#maven)
  * [Gradle (Java projects)](#gradle-java-projects)
* [Triggering schema generation](#triggering-schema-generation)
* [Configuration options](#configuration-options)
* [Generated output](#generated-output)
  * [Extended example](#extended-example)
  * [Reading the resource at runtime](#reading-the-resource-at-runtime)
* [Supported types](#supported-types)
  * [Jackson enum names](#jackson-enum-names)
  * [Default values](#default-values)
  * [Marking a property nullable/optional](#marking-a-property-nullableoptional)
* [See Also](#see-also)

<!--- END -->

Generate JSON Schema resources at compile time from plain Java records, classes, interfaces and enums using the
`kt-schema-apt` JSR 269 (`javax.annotation.processing`) processor — no Kotlin required in your own code.

## Setup

### Maven

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <configuration>
        <annotationProcessorPaths>
            <path>
                <groupId>me.kpavlov.kt.schema</groupId>
                <artifactId>kt-schema-apt</artifactId>
                <version>${kt-schema.version}</version>
            </path>
        </annotationProcessorPaths>
        <compilerArgs>
            <arg>-Ame.kpavlov.kt.schema.rootPackage=com.example</arg>
            <arg>-Ame.kpavlov.kt.schema.include=com.example.**</arg>
        </compilerArgs>
    </configuration>
</plugin>

<!-- Only needed if you annotate types with @Schema/@Description directly -->
<dependencies>
    <dependency>
        <groupId>me.kpavlov.kt.schema</groupId>
        <artifactId>kt-schema-annotations</artifactId>
        <version>${kt-schema.version}</version>
    </dependency>
</dependencies>
```

### Gradle (Java projects)

```kotlin
plugins {
    java
}

dependencies {
    annotationProcessor("me.kpavlov.kt.schema:kt-schema-apt:<version>")
    // Only needed if you annotate types with @Schema/@Description directly:
    implementation("me.kpavlov.kt.schema:kt-schema-annotations:<version>")
}
```

## Triggering schema generation

The processor discovers types as follows:

1. **Scope** — when the [`rootPackage`](#configuration-options) option is set, only types
   declared under that package (and its sub-packages) are considered; otherwise the whole
   module is scanned. This applies to `@Schema`-annotated types and include-glob matches alike.
2. **Selection** — a `record`, `class` or `interface` is processed when it is annotated with
   `@Schema` (from `kt-schema-annotations`) or matches at least one
   [`include`](#configuration-options) glob pattern.
3. **Exclusion** — a type matching any [`exclude`](#configuration-options) glob pattern is
   dropped, even when selected by `@Schema` or an include pattern.

```java
import me.kpavlov.kt.schema.Description;
import me.kpavlov.kt.schema.Schema;

@Description("A person with a first and last name and age.")
@Schema
public record Person(
        @Description("Given name of the person") String firstName,
        @Description("Family name of the person") String lastName,
        @Description("Age of the person in years") int age) {
}
```

With `rootPackage` + `include` configured, you can skip `kt-schema-annotations` entirely and reuse
annotations you already depend on — for example Jackson's:

```java
import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

@JsonClassDescription("A person with a first and last name and age.")
public record Person(
        @JsonPropertyDescription("Given name of the person") String firstName,
        @JsonPropertyDescription("Family name of the person") String lastName,
        @JsonPropertyDescription("Age of the person in years") int age) {
}
```

`kt-schema-apt` recognizes `@JsonPropertyDescription`/`@JsonClassDescription` out of the box — see
[Multi-Framework Annotation Support](../README.md#multi-framework-annotation-support) for the full list.

## Configuration options

| Option        | Type     | Default | Description                                                                                                     |
|:--------------|:---------|:--------|:------------------------------------------------------------------------------------------------------------------|
| `rootPackage` | `String` | `null`  | Scope discovery to types declared under this package (and sub-packages); absent means the whole module is scanned.  |
| `include`     | `String` | `null`  | Comma/semicolon-separated glob patterns; a type not annotated with `@Schema` is processed only when it matches at least one. |
| `exclude`     | `String` | `null`  | Comma/semicolon-separated glob patterns; a type matching any of them is dropped, even when `@Schema`-annotated or include-matched. |

Set them as javac `-A` compiler arguments:

```kotlin
tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.addAll(
        listOf(
            "-Ame.kpavlov.kt.schema.rootPackage=com.example",
            "-Ame.kpavlov.kt.schema.include=com.example.**",
        ),
    )
}
```

Glob syntax: `*` matches any sequence of non-`.` characters, `**` matches any sequence including
`.`, `?` matches a single non-`.` character — the same syntax the KSP processor uses.

## Generated output

Unlike the KSP processor — which generates `KClass<T>.jsonSchemaString`/`.jsonSchema` Kotlin extensions —
`kt-schema-apt` has no Kotlin code to attach extension properties to. Instead, it writes one JSON Schema
resource per processed type:

```text
META-INF/kt-schema/schemas/<package-as-directories>/<ClassName>.json
```

For the `Person` record above, that's `META-INF/kt-schema/schemas/com/example/Person.json`:

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$id": "com.example.Person",
  "description": "A person with a first and last name and age.",
  "type": "object",
  "properties": {
    "firstName": {
      "type": "string",
      "description": "Given name of the person"
    },
    "lastName": {
      "type": "string",
      "description": "Family name of the person"
    },
    "age": {
      "type": "integer",
      "description": "Age of the person in years"
    }
  },
  "additionalProperties": false,
  "required": ["firstName", "lastName", "age"]
}
```

This is the same [Draft 2020-12 JSON Schema model](../kt-schema-json) the KSP processor produces — both reuse
the same `TypeGraphToJsonSchemaTransformer`, so `$id`/`$defs`/`$ref`/nullability handling is identical.

### Extended example

Beyond plain scalars and nested records, the processor handles collections, maps, arrays, `Object` and type
variables. Consider an `Order` record referencing an `Address`:

```java
import me.kpavlov.kt.schema.Description;
import me.kpavlov.kt.schema.Schema;

@Schema
@Description("A customer order with tags, quantities, price points and billing address.")
public record Order(
        @Description("Unique order identifier") String id,
        @Description("Tags attached to the order") java.util.Set<String> tags,
        @Description("Line item quantities by SKU") java.util.Map<String, Integer> quantities,
        @Description("Historical price points") double[] prices,
        @Description("Free-form metadata") Object metadata,
        @Description("Billing address") Address address) {
}
```

```java
@Schema
public record Address(
        @Description("City part of the address") String city,
        @Description("Street part of the address") String street) {
}
```

This generates `META-INF/kt-schema/schemas/com/example/Order.json`:

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$id": "com.example.Order",
  "description": "A customer order with tags, quantities, price points and billing address.",
  "type": "object",
  "properties": {
    "id": {
      "type": "string",
      "description": "Unique order identifier"
    },
    "tags": {
      "type": "array",
      "description": "Tags attached to the order",
      "items": {
        "type": "string"
      }
    },
    "quantities": {
      "type": "object",
      "description": "Line item quantities by SKU",
      "additionalProperties": {
        "type": "integer"
      }
    },
    "prices": {
      "type": "array",
      "description": "Historical price points",
      "items": {
        "type": "number"
      }
    },
    "metadata": {
      "description": "Free-form metadata"
    },
    "address": {
      "$ref": "#/$defs/com.example.Address",
      "description": "Billing address"
    }
  },
  "additionalProperties": false,
  "required": ["id", "tags", "quantities", "prices", "metadata", "address"],
  "$defs": {
    "com.example.Address": {
      "type": "object",
      "properties": {
        "city": {
          "type": "string",
          "description": "City part of the address"
        },
        "street": {
          "type": "string",
          "description": "Street part of the address"
        }
      },
      "additionalProperties": false,
      "required": ["city", "street"]
    }
  }
}
```

Collections (`List`, `Set`, `Collection`, and subclasses thereof) map to `array` with `items`, `Map` maps to
`object` with `additionalProperties`, arrays nest `items` per dimension, `Object` emits an empty schema `{}`
(accepts any value), and nested records become `$ref`/`$defs` entries. `Address` is also emitted as its own
root resource because it is `@Schema`-annotated.

### Reading the resource at runtime

Read it like any other classpath resource:

```java
try (InputStream in = Person.class.getClassLoader()
        .getResourceAsStream("META-INF/kt-schema/schemas/com/example/Person.json")) {
    String schema = new String(in.readAllBytes(), StandardCharsets.UTF_8);
}
```

## Supported types

- Java `record`s — components map to required properties by default (Java records have no notion of
  optional/default values); mark a component nullable/optional via convention — see below
- Plain Java `class`es — non-static fields map to required properties, treated the same way as records
- Java `interface`s — no-arg methods map to required properties, named per the JavaBeans convention
  (`getName()` → `name`, `isActive()` → `active`, and a bare `name()` accessor stays `name`)
- Java `enum`s — emitted as `type: string` with an `enum` array listing the constants in declaration order
- `String`, boxed and primitive numeric/boolean types
- `Iterable`-derived collections (`List`, `Set`, `Collection`, custom subclasses) — emitted as `array` with
  `items` describing the element type
- `Map` — emitted as `object` with `additionalProperties` describing the value type
- Arrays, including multi-dimensional — emitted as `array` with `items` nested per dimension
- `java.lang.Object` — emitted as an empty schema `{}` accepting any value
- Type variables: upper-bounded (`T extends Number`) resolve to their bound; unbounded (`T`) emit `{}`
- Nested records/classes/interfaces, emitted as `$ref`/`$defs` and deduplicated, same as KSP

Not yet supported: sealed class hierarchies (polymorphic `oneOf` with discriminators). Processing an
unsupported type fails the build with a descriptive error rather than emitting an incomplete schema.

### Jackson enum names

When the application already uses Jackson, `kt-schema-apt` uses `@JsonProperty` on enum constants as their
JSON Schema values and `@JsonTypeName` on the enum as its schema `$id`. The generated resource path still
uses the declared Java type name.

```java
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;

@JsonTypeName("PriorityLevel")
public enum Priority {
    @JsonProperty("low") LOW,
    @JsonProperty("medium") MEDIUM,
    @JsonProperty("high") HIGH
}
```

This produces a schema whose `$id` is `PriorityLevel` and whose `enum` is `["low", "medium", "high"]`,
at `META-INF/kt-schema/schemas/com/example/Priority.json` for a `com.example.Priority` enum.

### Default values

Java has no default-parameter expressions to evaluate, but two Jackson annotations are recognized as an explicit,
compile-time-visible substitute:

```java
import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;
import com.fasterxml.jackson.annotation.JsonProperty;

public enum Priority {
    LOW,
    @JsonEnumDefaultValue MEDIUM,
    HIGH
}

public record Job(Priority priority, @JsonProperty(defaultValue = "30") int timeoutSeconds) {}
```

`@JsonEnumDefaultValue`, placed on one enum constant, marks it as that enum's `default` — always shown on the
enum's own schema in `$defs`, since it describes the *type*, not any one property using it. `@JsonProperty(defaultValue = "...")`
populates the property's default internally, but — like every property here — `kt-schema-apt`'s generated resource
always marks it required and never shows the `default` keyword for it, since Java has no reliable way to know
whether a value truly behaves as optional. Both are configurable via `kt-schema.properties`
(`introspector.annotations.enumDefault.names`, `introspector.annotations.defaultValue.names`,
`introspector.annotations.defaultValue.attributes`) — see
[Multi-Framework Annotation Support](../README.md#multi-framework-annotation-support).

### Marking a property nullable/optional

Java has no `?` type syntax and no default-value expressions, so every property is non-nullable/required by
default. Two independent conventions cover the two concerns — nullable and optional are not the same thing
and don't imply each other:

- **Nullable** — marks a property's *type* nullable (adds `"null"` to its `type`), the same way Kotlin's `?`
  is handled, but leaves it in `required`:
  - **Type-name glob pattern** — a field/component whose *type's* simple class name matches a configured
    pattern (`*` = any substring; default `*Opt`), e.g. a type literally named `EmailOpt`.
  - **`@Nullable` annotation** — a field/component/accessor or its type annotated with a marker annotation
    (default simple name `Nullable`, matched case-insensitively regardless of package — so
    `org.jspecify.annotations.Nullable`, `javax.annotation.Nullable`, `jakarta.annotation.Nullable`,
    `org.jetbrains.annotations.Nullable`, etc. all work out of the box).
- **Optional** — excludes a property from `required`, the same way a Kotlin default value is handled, but
  doesn't affect its type's nullability. Matched the same way as the nullable convention (type-name glob
  pattern or marker annotation, e.g. `@Optional`), via a separate, opt-in-only configuration with no default.

Both conventions are configurable via `kt-schema.properties` (`introspector.nullable.type.names`,
`introspector.optional.type.names`, `introspector.annotations.nullable.names`,
`introspector.annotations.optional.names`) — every one of these accepts a comma-separated list of
glob patterns (`*`/`?`), not just exact names.

For example, use JSpecify to accept `null` while keeping the property required:

```java
import org.jspecify.annotations.Nullable;

public record Person(String name, @Nullable String middleName) {
}
```

`middleName` is emitted with `"type": ["string", "null"]` and remains in `required`.

## See Also

- [KSP Configuration Guide](ksp.md) — the Kotlin compile-time equivalent, with `KClass<T>.jsonSchemaString` extensions
- [Annotation Reference](../README.md#using-schema-and-description-annotations) — `@Schema` and `@Description` usage
- [Multi-Framework Annotation Support](../README.md#multi-framework-annotation-support) — Jackson, LangChain4j, Koog recognition
- [Project Architecture](architecture.md) — how the shared IR and transformer pipeline works
