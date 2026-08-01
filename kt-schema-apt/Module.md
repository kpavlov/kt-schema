# Module kt-schema-apt

Compile-time JSON Schema generation for plain Java projects via a JSR 269 (`javax.annotation.processing`)
annotation processor.

Analyzes `@Schema`-annotated Java types — or every type under a configured root package — at compile time,
writing one JSON Schema resource per type. No Kotlin required in consuming code.

**Platform Support:** JVM only • Build-time only • Java 17+

## Generated Output

```java
@Schema
public record User(String name) {}

// Generated resource:
// META-INF/kt-schema/schemas/com/example/User.json
```

Unlike `kt-schema-ksp`, this module has no Kotlin code to attach extension properties to, so it writes the
serialized `JsonSchema` directly as a classpath resource instead of generating Kotlin source.

## Configuration

Processor options, set via javac `-A` compiler arguments:

- `me.kpavlov.kt.schema.rootPackage` — process every top-level type under this package (and sub-packages),
  in addition to `@Schema`-annotated types

See [Java Annotation Processor Guide](https://github.com/kpavlov/kt-schema/blob/main/docs/apt.md).

## Features

- Reuses the same [`TypeGraphToJsonSchemaTransformer`][me.kpavlov.kt.schema.generator.json.TypeGraphToJsonSchemaTransformer]
  as `kt-schema-ksp`, so output ($id/$defs/$ref/nullability) is identical
- Recognizes description/ignore/name-override annotations from Jackson, LangChain4j, Koog, etc. via the shared
  [`Introspections`][me.kpavlov.kt.schema.generator.core.ir.Introspections] config, same as KSP and reflection

# Package me.kpavlov.kt.schema.apt

JSR 269 annotation processor implementation.

# Package me.kpavlov.kt.schema.apt.ir

Intermediate representation introspection for `javax.lang.model` (Java APT) types.
