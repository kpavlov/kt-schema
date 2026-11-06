---
title: JSON Schema DSL
description: Build JSON Schema Draft 2020-12 documents by hand with the type-safe Kotlin DSL in the kt-schema-json module.
menus:
  main:
    name: DSL
    weight: 70
---

# JSON Schema DSL

For manual schema construction, use the [**kt-schema-json**](../kt-schema-json) module.
It provides type-safe Kotlin models compliant with
the [JSON Schema Draft 2020-12](https://json-schema.org/draft/2020-12/schema) specification
and a DSL for building JSON Schema definitions programmatically, with full kotlinx-serialization support.

```kotlin
dependencies {
    implementation("me.kpavlov.kt.schema:kt-schema-json:<version>")
}
```

## Quick Example

<!--- CLEAR -->
<!--- INCLUDE
import me.kpavlov.kt.schema.json.jsonSchema
-->

```kotlin
val schema = jsonSchema {
    property("id") {
        required = true
        string { format = "uuid" }
    }
    property("email") {
        required = true
        string { format = "email" }
    }
    property("age") {
        integer {
            minimum = 0.0
            maximum = 150.0
        }
    }
    // Polymorphic types with discriminators
    property("role") {
        oneOf {
            discriminator(propertyName = "type") {
                "admin" mappedTo "#/definitions/AdminRole"
                "user" mappedTo {
                    property("type") { string { constValue = "user" } }
                    property("permissions") { array { ofString() } }
                }
            }
        }
    }
}
```

<!--- KNIT example-knit-dsl-01.kt -->

## Features

- ✅ Type-safe property definitions (string, number, integer, boolean, array, object, reference)
- ✅ **Polymorphism**: oneOf, anyOf, allOf with elegant discriminator support
- ✅ **Type-safe enums**: Native Kotlin types (`List<String>`, `List<Number>`, etc.) instead of `JsonElement`
- ✅ Constraints: required, nullable, enum, const, min/max, format validation
- ✅ Nested schemas and arrays of complex types
- ✅ Full kotlinx-serialization integration
- ✅ Kotlin Multiplatform support

**📖 For comprehensive documentation, see [kt-schema-json/README.md](../kt-schema-json/README.md)** covering:

- Complete DSL reference and type-safe enum API
- Polymorphism patterns (oneOf, anyOf, allOf) with discriminators
- Generic properties with heterogeneous enums
- Working with nested objects and arrays
- Serialization/deserialization examples
- Function calling schema for LLM APIs

## See Also

- [Generated Schemas](generated.md) — the same model, produced from your classes instead of by hand
- [Validation Constraints](constraints.md) — the keywords generators emit from annotations
