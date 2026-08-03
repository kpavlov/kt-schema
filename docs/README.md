# Documentation

kt-schema generates JSON Schema from Kotlin and Java models. Choose the generation path that matches how your
models are built and deployed.

## Features

### Generation modes

- **Compile-time Java APT** — generates schema resources for plain Java records, classes, interfaces, and enums
  with no runtime generation overhead. It runs on the JVM and does not require Kotlin in your application code.
- **Compile-time KSP** — generates schemas for annotated Kotlin classes with no runtime generation overhead and
  supports Kotlin Multiplatform.
- **Runtime reflection** — generates schemas for any JVM class, including types from third-party libraries.
- **Runtime serial descriptors** — generates schemas for Kotlin `@Serializable` classes across supported Kotlin
  platforms, including open polymorphism configured through `SerializersModule`.

### LLM function calling

- Generates function-calling schemas for OpenAI and Anthropic formats.
- Extracts function names and descriptions.
- Supports strict mode and parameter validation.

### Annotation interoperability

- Recognizes `@Description`, `@LLMDescription`, `@JsonPropertyDescription`, `@P`, and other configured
  description annotations.
- Extracts KDoc descriptions during KSP compile-time generation.
- Reuses annotations from Jackson, LangChain4j, and Koog without changing the model.

### Type support

- Supports enums, collections, maps, nested objects, nullability, and generics, including star projections.
- Emits `oneOf` schemas and discriminator fields for sealed classes and serialization-based open polymorphism.
- Represents nullable parameters as union types such as `["string", "null"]`.
- Supports JSON Schema constraints, including minimum/maximum values, patterns, and formats, through the DSL.
- Tracks compile-time defaults and extracts runtime defaults.
- Deduplicates named types through `$ref` and `$defs`.
- Maps `kotlin.Any` to `{}`, which accepts any JSON value.

### Developer experience

- Provides an experimental Gradle plugin for KSP setup and generated-source wiring.
- Provides a type-safe Kotlin DSL for programmatic JSON Schema construction.
- Supports JVM, JavaScript/Wasm, and Kotlin/Native targets where the selected generation mode is available.

## Guides

- [KSP Configuration Guide](ksp.md) — compile-time schemas for annotated Kotlin models.
- [Java Annotation Processor Guide](apt.md) — compile-time schema resources for Java records, classes,
  interfaces, and enums; includes Jackson enum-name support.
- [Serialization-Based Schema Generation](serializable.md) — runtime schemas from Kotlin serialization descriptors.
- [Project Architecture](architecture.md) — shared metadata, IR, and JSON Schema emission.

For runtime reflection, function-calling schemas, annotation support, and the JSON Schema DSL, see the
[project README](../README.md).
