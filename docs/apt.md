# Java Annotation Processor

**Table of contents**
<!--- TOC -->

* [Setup](#setup)
  * [Maven](#maven)
  * [Gradle (Java projects)](#gradle-java-projects)
* [Triggering schema generation](#triggering-schema-generation)
* [Configuration options](#configuration-options)
* [Generated output](#generated-output)
  * [Reading the resource at runtime](#reading-the-resource-at-runtime)
* [Supported types](#supported-types)
* [See Also](#see-also)

<!--- END -->

Generate JSON Schema resources at compile time from plain Java records and classes using the `kt-schema-apt` JSR 269
(`javax.annotation.processing`) processor — no Kotlin required in your own code.

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

The processor picks up a Java type either of two ways — you don't need both:

1. **Annotate the type with `@Schema`** (from `kt-schema-annotations`) — processed regardless of package.
2. **Set the [`rootPackage`](#configuration-options) option** — every top-level `record` or `class` declared
   under that package (and its sub-packages) is processed, `@Schema` or not.

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

With `rootPackage` configured, you can skip `kt-schema-annotations` entirely and reuse annotations you
already depend on — for example Jackson's:

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
| `rootPackage` | `String` | `null`  | Process every top-level `record` or `class` under this package (and sub-packages), in addition to `@Schema`-annotated types.   |

Set it as a javac `-A` compiler argument:

```kotlin
tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-Ame.kpavlov.kt.schema.rootPackage=com.example")
}
```

> [!NOTE]
> `kt-schema-apt` is newer than the KSP processor and doesn't yet support all of its options.
> `include`/`exclude` glob filtering, `withSchemaObject`, `visibility`, and `enabled` from
> [the KSP processor](ksp.md) aren't implemented here yet.

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
  "$id": "com.example.Person",
  "$schema": "https://json-schema.org/draft/2020-12/schema",
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
  "required": ["firstName", "lastName", "age"],
  "additionalProperties": false,
  "description": "A person with a first and last name and age."
}
```

This is the same [Draft 2020-12 JSON Schema model](../kt-schema-json) the KSP processor produces — both reuse
the same `TypeGraphToJsonSchemaTransformer`, so `$id`/`$defs`/`$ref`/nullability handling is identical.

### Reading the resource at runtime

Read it like any other classpath resource:

```java
try (InputStream in = Person.class.getClassLoader()
        .getResourceAsStream("META-INF/kt-schema/schemas/com/example/Person.json")) {
    String schema = new String(in.readAllBytes(), StandardCharsets.UTF_8);
}
```

## Supported types

- Java `record`s — components map to required properties (Java records have no notion of optional/default
  values, so every component is required)
- Plain Java `class`es — non-static fields map to required properties, treated the same way as records
- `String`, boxed and primitive numeric/boolean types
- Nested records/classes, emitted as `$ref`/`$defs` and deduplicated, same as KSP

Not yet supported: enums, sealed interfaces/classes, generics, and collections/maps. Processing an
unsupported type fails the build with a descriptive error rather than emitting an incomplete schema.

> [!NOTE]
> Reference-typed components are always treated as non-nullable/required — there's no `@Nullable` support yet.

## See Also

- [KSP Configuration Guide](ksp.md) — the Kotlin compile-time equivalent, with `KClass<T>.jsonSchemaString` extensions
- [Annotation Reference](../README.md#using-schema-and-description-annotations) — `@Schema` and `@Description` usage
- [Multi-Framework Annotation Support](../README.md#multi-framework-annotation-support) — Jackson, LangChain4j, Koog recognition
- [Project Architecture](architecture.md) — how the shared IR and transformer pipeline works
