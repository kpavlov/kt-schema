# Module kt-schema-ksp

Core abstractions and intermediate representation (IR) for schema generation.

Provides the foundational architecture unifying KSP, Reflection, and Serialization introspection strategies 
through a common [TypeGraph][me.kpavlov.kt.schema.generator.core.ir.TypeGraph] IR.

**Platform Support:** Multiplatform IR models (Common) • JVM reflection introspection • Kotlin 2.2+

## Key Components

- [TypeGraph][me.kpavlov.kt.schema.generator.core.ir.TypeGraph] - intermediate representation capturing type metadata, hierarchies, and annotations
  [SchemaGenerator][me.kpavlov.kt.schema.generator.core.SchemaGenerator] - abstract interface for implementing custom generators
- [SchemaIntrospector][me.kpavlov.kt.schema.generator.core.ir.SchemaIntrospector] - pluggable introspection layer for analyzing types
- [TypeGraphTransformer][me.kpavlov.kt.schema.generator.core.ir.TypeGraphTransformer] - converts IR to concrete schema formats
- [Config][me.kpavlov.kt.schema.generator.core.Config] - configuration for annotation recognition

## Architecture

Three introspection strategies converge on [TypeGraph][me.kpavlov.kt.schema.generator.core.ir.TypeGraph]:

1. **Compile-time (KSP)**: Symbol processor → [TypeGraph][me.kpavlov.kt.schema.generator.core.ir.TypeGraph] → generated code
2. **Runtime (Reflection)**: [KClass][kotlin.reflect.KClass] analysis → [TypeGraph][me.kpavlov.kt.schema.generator.core.ir.TypeGraph] → runtime schema
3. **Runtime (Serialization)**: [SerialDescriptor][kotlinx.serialization.descriptors.SerialDescriptor] → [TypeGraph][me.kpavlov.kt.schema.generator.core.ir.TypeGraph] → runtime schema

The unified [TypeGraph][me.kpavlov.kt.schema.generator.core.ir.TypeGraph] feeds transformers that produce 
[JsonSchema][me.kpavlov.kt.schema.json.JsonSchema], [FunctionCallingSchema][me.kpavlov.kt.schema.json.FunctionCallingSchema], etc.

## Extending

Implement custom schema formats by:
1. Creating a [TypeGraphTransformer][me.kpavlov.kt.schema.generator.core.ir.TypeGraphTransformer] implementation
2. Extending [AbstractSchemaGenerator][me.kpavlov.kt.schema.generator.core.AbstractSchemaGenerator] with your transformer
3. Using existing introspectors or implementing [SchemaIntrospector][me.kpavlov.kt.schema.generator.core.ir.SchemaIntrospector]

# Package me.kpavlov.kt.schema.generator.core

Core schema generator abstractions and configuration.

# Package me.kpavlov.kt.schema.generator.core.ir

Intermediate representation (IR) models and transformers.

# Package me.kpavlov.kt.schema.generator.reflect

Reflection-based introspection for JVM runtime analysis.
