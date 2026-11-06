---
title: Validation Constraints
description: How Jakarta Bean Validation annotations and built-in type ranges become JSON Schema validation keywords.
menus:
  main:
    name: Constraints
    weight: 35
---

# Validation Constraints

kt-schema emits JSON Schema validation keywords (`minimum`, `maxLength`, `pattern`, `format`, …) from two sources:
Jakarta Bean Validation annotations on your model, and the fixed range or wire format of built-in types. No extra
configuration is needed, and `jakarta.validation-api` is not a dependency of kt-schema.

## Jakarta Bean Validation annotations

Annotations from `jakarta.validation.constraints` and `javax.validation.constraints` are read from properties
(reflection, KSP and APT) and from function parameters (reflection and KSP).

```kotlin
@Schema
data class ValidatedShipment(
    @Min(1) @Max(100) val quantity: Int,
    @DecimalMin("0.01") @DecimalMax(value = "1000", inclusive = false) val price: Double,
    @Size(min = 2, max = 50) val name: String,
    @NotEmpty val tags: List<String>,
    @NotBlank val note: String?,
    @Pattern(regexp = "[A-Z]{3}") val currency: String,
    @Email val contact: String,
    @Size(max = 5) val attributes: Map<String, String>,
    @Min(0) val level: Byte,
)
```

Generated `properties` (other keywords omitted):

```json
{
    "quantity": { "type": "integer", "minimum": 1, "maximum": 100 },
    "price": { "type": "number", "minimum": 0.01, "exclusiveMaximum": 1000 },
    "name": { "type": "string", "minLength": 2, "maxLength": 50 },
    "tags": { "type": "array", "items": { "type": "string" }, "minItems": 1 },
    "note": { "type": ["string", "null"], "minLength": 1 },
    "currency": { "type": "string", "pattern": "[A-Z]{3}" },
    "contact": { "type": "string", "format": "email" },
    "attributes": { "type": "object", "additionalProperties": { "type": "string" }, "maxProperties": 5 },
    "level": { "type": "integer", "minimum": 0, "maximum": 127 }
}
```

### Mapping

| Annotation                     | Emitted keywords                                                                      |
|--------------------------------|---------------------------------------------------------------------------------------|
| `@Min`, `@Max`                 | `minimum`, `maximum`                                                                  |
| `@DecimalMin`, `@DecimalMax`   | `minimum`/`maximum`, or `exclusiveMinimum`/`exclusiveMaximum` with `inclusive = false` |
| `@Positive`, `@PositiveOrZero` | `exclusiveMinimum: 0`, `minimum: 0`                                                   |
| `@Negative`, `@NegativeOrZero` | `exclusiveMaximum: 0`, `maximum: 0`                                                   |
| `@Size(min, max)`              | `minLength`/`maxLength`, `minItems`/`maxItems` or `minProperties`/`maxProperties`     |
| `@NotEmpty`                    | `minLength: 1` / `minItems: 1` / `minProperties: 1`                                   |
| `@NotBlank`                    | `minLength: 1` (blank strings are not excluded)                                       |
| `@Pattern(regexp)`             | `pattern`, copied as written                                                          |
| `@Email`                       | `format: "email"`                                                                     |

**Not read:** `@NotNull`, `@Digits`, `@Past`/`@Future`, `@AssertTrue`/`@AssertFalse` and constraints on type
arguments (`List<@Size(max = 3) String>`). The serialization-based generator cannot see these annotations at all,
since `SerialDescriptor` exposes only `@SerialInfo` annotations.

### Rules

- **Several bounds on one property:** the tighter bound wins whatever the order, including over a built-in type
  range (`@Min(0) val level: Byte` gives `minimum: 0, maximum: 127`). For `pattern` and `format` the last annotation
  wins.
- **Object and enum properties:** a type emitted as a `$ref` keeps its reference, wrapped as
  `allOf: [{ "$ref": ... }, { "minProperties": 1 }]`.
- **`@Pattern`:** JSON Schema validators expect ECMA-262 regular expressions, so avoid possessive quantifiers and
  `\p{Alpha}`-style Java classes. A `@Pattern` with `flags` has no JSON Schema form: it is skipped with a warning.
- **Contradictions:** bounds that exclude every value (`@Size(min = 5, max = 2)`, `@Min(200)` on a `Byte`,
  `@Size(min = 2)` on a `Char`, `@DecimalMin("0.5") @DecimalMax("0.9")` on an `Int`) are emitted as written and
  logged as a warning naming the member. A negative `@Size` bound is ignored with a warning. On a nullable member
  nothing is logged, since `null` still satisfies the schema. Generation never fails on an inconsistent
  annotation, because the annotated class may not be yours to fix.
- **Warnings** go through kotlin-logging, which prints to stderr during KSP and APT builds.

### Custom annotations

Constraint annotations are matched by name, so any package can be registered through `kt-schema.properties`.
A match is mapped by its simple name: a `com.acme.Size(min, max)` works once `com.acme.*` is listed.

```properties
introspector.annotations.constraint.names=jakarta.validation.constraints.*,javax.validation.constraints.*,com.acme.*
```

## Built-in type constraints

Types with a fixed range or wire format carry it in the schema without any annotation.

| Kotlin / Java type                                                                                                         | Emitted keywords                       |
|----------------------------------------------------------------------------------------------------------------------------|----------------------------------------|
| `Byte`, `Short`                                                                                                            | `minimum`, `maximum` of the type range |
| `UByte`, `UShort`, `UInt`                                                                                                  | `minimum: 0`, `maximum` of the type    |
| `ULong`                                                                                                                    | `minimum: 0`                           |
| `Char`                                                                                                                     | `minLength: 1`, `maxLength: 1`         |
| `java.util.UUID`, `kotlin.uuid.Uuid`                                                                                       | `format: "uuid"`                       |
| `kotlin.time.Instant`, `kotlinx.datetime.Instant`, `java.time.Instant`, `OffsetDateTime`, `ZonedDateTime`, `java.util.Date` | `format: "date-time"`                  |
| `kotlinx.datetime.LocalDate`, `java.time.LocalDate`                                                                        | `format: "date"`                       |
| `kotlinx.datetime.LocalTime`, `java.time.LocalTime`, `java.time.OffsetTime`                                                | `format: "time"`                       |

`Int` and `Long` stay plain `integer`; `LocalDateTime` is a plain `string` since JSON Schema has no format for a
zone-less date-time. The `java.*` types apply to reflection, KSP and APT; kotlinx.serialization sees only the
Kotlin types it has serializers for.

> [!IMPORTANT]
> Date/time types are always emitted as ISO-8601 strings. The generators never see your `ObjectMapper`, so if you
> serialize with plain Jackson, disable `SerializationFeature.WRITE_DATES_AS_TIMESTAMPS` so payloads match the
> schema.

## See Also

- [KSP Configuration Guide](ksp.md) and [Java Annotation Processor Guide](apt.md) — the compile-time front ends
  that read these annotations
- [Multi-Framework Annotation Support](configuration.md#multi-framework-annotation-support) — how annotation names are
  matched and configured
- [JSON Schema DSL](../kt-schema-json/README.md) — set the same keywords by hand
