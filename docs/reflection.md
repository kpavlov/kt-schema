---
title: Runtime Reflection
description: Generate JSON Schema at runtime on the JVM for any class, including third-party types you cannot annotate.
menus:
  main:
    name: Reflection
    weight: 45
---

# Runtime Reflection

> [!NOTE]
> If your classes are `@Serializable`, use the [Serialization-Based Generator](serializable.md) instead — it works
> on all platforms without reflection.

For JVM-only scenarios with classes you don't own or can't annotate, use
[`ReflectionClassJsonSchemaGenerator`](../kt-schema-generator-json/src/jvmMain/kotlin/me/kpavlov/kt/schema/generator/json/ReflectionClassJsonSchemaGenerator.kt)
and [`ReflectionFunctionCallingSchemaGenerator`](../kt-schema-generator-json/src/jvmMain/kotlin/me/kpavlov/kt/schema/generator/json/ReflectionFunctionCallingSchemaGenerator.kt)
with Kotlin reflection.

## Why Runtime Generation?

**Primary use case: Third-party library classes**

The compile-time (KSP) approach requires you to annotate classes with `@Schema`, which isn't possible for:

- Library classes (Spring entities, Ktor models, database classes)
- Framework-provided models
- Classes from dependencies you don't control

Runtime generation solves this by using reflection to analyze any class at runtime.

> [!IMPORTANT]
> **Limitations:**
> - KDoc annotations are not available at runtime
> - Function parameter defaults (e.g., `fun foo(x: Int = 5)`) cannot be extracted via reflection
> - Data class property defaults (e.g., `data class Config(val port: Int = 8080)`) ARE supported

## Usage

<!--- CLEAR -->
<!--- INCLUDE
import me.kpavlov.kt.schema.Schema
import me.kpavlov.kt.schema.json.JsonSchema
-->

```kotlin
// Works with ANY class, even from third-party libraries
import com.thirdparty.library.User  // Not your code!

val generator = me.kpavlov.kt.schema.generator.json.ReflectionClassJsonSchemaGenerator.Default
val schema: JsonSchema = generator.generateSchema(User::class)
val schemaString: String = generator.generateSchemaString(User::class)
```

<!--- KNIT example-knit-reflection-01.kt -->

**Add dependency**: `me.kpavlov.kt.schema:kt-schema-generator-json:<version>`

## See Also

- [Generated Schemas](generated.md) — what the schema looks like for each Kotlin type
- [Function Calling](function-calling.md) — `ReflectionFunctionCallingSchemaGenerator` for LLM tool schemas
- [Serialization-Based Schema Generation](serializable.md) — multiplatform alternative for `@Serializable` classes
