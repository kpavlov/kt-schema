---
title: Documentation
menus:
  main:
    name: Overview
    weight: 1
---

# Documentation

kt-schema generates JSON Schema and LLM function calling schemas from Kotlin and Java code. Choose the generation
path that matches how your models are built and deployed, annotate (or don't), and read the schema from a generated
extension, a classpath resource or a runtime call.

## Why kt-schema?

1. **🤖 LLM function calling**: generate tool definitions for OpenAI, Anthropic and MCP directly from Kotlin functions,
   with proper type definitions and descriptions.
2. **📦 Third-party classes**: create schemas for library classes without modifying their source (Spring entities,
   Ktor models, etc.).
3. **🔄 Multi-framework compatibility**: works with existing annotations from Jackson, LangChain4j, Koog and more, with
   no code changes.

## Choosing Your Approach

|                                   | 🔧 [KSP Processor](ksp.md) | ☕ [Java APT](apt.md) | 📦 [Serialization-based](serializable.md) | 🔍 [Runtime Reflection](reflection.md) |
|:----------------------------------|:--------------------------:|:--------------------:|:-----------------------------------------:|:--------------------------------------:|
| **Platforms**                     |    JVM + Multiplatform     |       JVM only       |            JVM + Multiplatform            |                JVM only                |
| **When generated**                |        Compile-time        |     Compile-time     |                  Runtime                  |                Runtime                 |
| **Requires annotation processor** |         Yes (KSP)          |      Yes (APT)       |                    No                     |                   No                   |
| **Class must be `@Serializable`** |             No             |          No          |                    Yes                    |                   No                   |
| **Annotate class with `@Schema`** |          Required          |    Not required¹     |               Not required                |              Not required              |
| **KDoc extracted to description** |             ✅              |          ❌           |                     ❌                     |                   ❌                    |
| **Extract default values**        |          Partial²          |       Partial²       |                     ❌                     |                   ✅                    |
| **Third-party classes**           |             ❌              |          ❌           |         ✅ (only `@Serializable`)          |            ✅ any JVM class             |

¹ with the `rootPackage` option — see [Java Annotation Processor Guide](apt.md#triggering-schema-generation).

² Jackson's `@JsonEnumDefaultValue` becomes the enum's `default`. Kotlin/Java default-value expressions can't be
evaluated at compile time, so no property `default` is emitted — see
[Multi-Framework Annotation Support](configuration.md#multi-framework-annotation-support).

- **[Pick KSP](ksp.md)** when you own the classes, want zero runtime overhead, and target Multiplatform or need
  KDoc in your schema.
- **[Pick Java APT](apt.md)** when your code is plain Java (no Kotlin) and you want zero runtime overhead.
  Supports Java records, classes, interfaces and enums.
- **[Pick Serialization-based](serializable.md)** when your classes are already `@Serializable` and you need
  Multiplatform support without a build-time processor.
- **[Pick Reflection](reflection.md)** when you need JVM-only runtime generation for third-party classes, or
  need to extract data class default values. Works equally well for `@Serializable` classes on JVM.

## Quick Start

Refer to the [example projects](../examples).

### Annotate Your Models

<!--- CLEAR -->
<!--- INCLUDE
import me.kpavlov.kt.schema.Schema
import me.kpavlov.kt.schema.Description
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
-->

```kotlin
/**
 * A postal address for deliveries and billing.
 */
@Schema
data class Address(
    @Description("Street address, including house number") @Size(max = 120) val street: String,
    @Description("City or town name") @Size(min = 1, max = 80) val city: String,
    @Description("Postal or ZIP code") @Pattern(regexp = "[0-9]{5}") val zipCode: String,
    @Description("Two-letter ISO country code; defaults to US") @Pattern(regexp = "[A-Z]{2}") val country: String = "US",
)
```

<!--- KNIT example-knit-home-01.kt -->

<details>
<summary>Generated JSON schema (KSP, default <code>strict</code> config)</summary>

```json
{
    "$schema": "https://json-schema.org/draft/2020-12/schema",
    "$id": "com.example.Address",
    "description": "A postal address for deliveries and billing.",
    "type": "object",
    "properties": {
        "street": {
            "type": "string",
            "description": "Street address, including house number",
            "maxLength": 120
        },
        "city": {
            "type": "string",
            "description": "City or town name",
            "minLength": 1,
            "maxLength": 80
        },
        "zipCode": {
            "type": "string",
            "description": "Postal or ZIP code",
            "pattern": "[0-9]{5}"
        },
        "country": {
            "type": "string",
            "description": "Two-letter ISO country code; defaults to US",
            "pattern": "[A-Z]{2}"
        }
    },
    "additionalProperties": false,
    "required": [
        "street",
        "city",
        "zipCode",
        "country"
    ]
}
```

</details>

> **Note:** KDoc comments on classes can also be used as descriptions. Jakarta Bean Validation annotations such as
> `@Size` and `@Pattern` become `maxLength`, `minLength` and `pattern` — see
> [Validation Constraints](constraints.md).

### Quick Setup

Add the Google KSP plugin and the processor dependency:

```kotlin
// Multiplatform (KMP)
plugins {
    kotlin("multiplatform")
    id("com.google.devtools.ksp") version "<ksp-version>"
}

dependencies {
    add("kspCommonMainMetadata", "me.kpavlov.kt.schema:kt-schema-ksp:<version>")
    implementation("me.kpavlov.kt.schema:kt-schema-annotations:<version>")
}

kotlin {
    sourceSets.commonMain.kotlin.srcDir("build/generated/ksp/metadata/commonMain/kotlin")
}
```

Then read the schema from the generated extension:

```kotlin
val schema: String = Address::class.jsonSchemaString
```

For JVM-only projects and all processor options, see the [KSP Configuration Guide](ksp.md). To see what the
schema contains, see [Generated Schemas](generated.md).
