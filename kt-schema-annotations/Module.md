# Module kt-schema-annotations

Core annotations for marking classes and functions for schema generation.

**Platform Support:** Multiplatform (Common, JVM, JS, Native, Wasm) • Kotlin 2.2+

## Annotations

- [@Schema][me.kpavlov.kt.schema.Schema] - marks declarations for schema generation. Recognized by compile-time KSP generator.
- [@Description][me.kpavlov.kt.schema.Description] - adds human-readable descriptions to schemas
- [@SchemaIgnore][me.kpavlov.kt.schema.SchemaIgnore] - excludes a class (e.g., sealed subtype) from schema generation

## Example

```kotlin
@Schema
@Description("User account information")
data class User(
    @Description("Unique user identifier") val id: Long,
    @Description("User's email address") val email: String
)
```

# Package me.kpavlov.kt.schema

Core annotations for JSON Schema generation.
