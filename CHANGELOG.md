## Unreleased

### Added

- `JsonSchemaConfig.Lenient` preset and `allowAdditionalProperties` flag (also on `FunctionCallingSchemaConfig`,
  together with `Lenient` and `OpenAPI` presets), and the `me.kpavlov.kt.schema.config` KSP/APT processor option
  selecting `strict`, `lenient`, `openapi` or a custom `JsonSchemaConfig` class. With `useUnionTypes` and
  `useNullableField` both `false`, nullable properties carry no null marker and are never required (nullable collection
  elements and map values get an `anyOf` null branch); the function-calling emitter now honors `useUnionTypes`/
  `useNullableField`. The `JsonSchemaConfig` and `FunctionCallingSchemaConfig` constructors gained a trailing parameter;
  the six-argument constructors are kept for binary compatibility, but Kotlin callers compiled against them with omitted
  (default) arguments must recompile ([#149](https://github.com/kpavlov/kt-schema/issues/149))
- Inline value classes (`@JvmInline value class`) are flattened to their wrapped type in the reflection and KSP front
  ends; `@Description` on the class or property is kept ([#117](https://github.com/kpavlov/kt-schema/issues/117))
- `kt-schema-apt`: `BigInteger` (`integer`) and `BigDecimal` (`number`) fields
  ([#114](https://github.com/kpavlov/kt-schema/pull/114))
- Reflection and KSP front ends: the polymorphic discriminator property name honors `@JsonClassDiscriminator("...")` and
  Jackson `@JsonTypeInfo(property = "...")`, inherited by nested sealed types, instead of always using `type`;
  configurable via `introspector.annotations.discriminator.names` and
  `introspector.annotations.discriminator.attributes`
- Reflection and KSP front ends: `kotlin.time.Instant` maps to `string`, matching kotlinx.serialization's default
  `Instant` serializer; previously it was expanded into an `epochSeconds`/`nanosecondsOfSecond` object

- `JsonSchemaConfig.shortDefinitionNames` (enabled in `Lenient`): `$defs` keys, `$ref`s, the root `$id` and discriminator
  `const` values use the shortest unique dotted suffix of the qualified name; annotation names are kept and
  kotlinx.serialization schemas are never shortened

### Deprecated

- `FunctionCallingSchemaConfig.Default`: identical to `Strict`; use `Strict` (IDE quick-fix available). It will be
  removed in a future minor release.

### Changed

- Breaking, IR migration ([#148](https://github.com/kpavlov/kt-schema/issues/148)): `Property` now stores facts instead
  of derived flags, and `ObjectNode` no longer stores `required`; emitters derive requiredness from these facts and
  their config. The deprecated shims were dropped, so callers of the IR must migrate:
    - `Property.hasDefaultValue` → `Property.optional` (set only by explicit markers: a Kotlin default, an optional
      type-name convention or an optional annotation; a default literal alone does not set it, but still makes the
      property non-required at emission)
    - `Property.defaultValue` / `isConstant` → `Property.value`: `PropertyValue.None`, `UnknownDefault`,
      `Default(Literal)` or `Const(Literal)`. `Default(Literal.Null)` distinguishes "default is `null`" from "no default"
    - `ObjectNode.required` is removed; use `Property.isPresenceRequired()` (internal API)
    - `Property.copy`/`componentN` and `ObjectNode.copy`/`componentN` change signature
- `val x: T? = null` now emits `"default": null` in reflection-based schemas (not under configs that omit the null
  marker, such as `Lenient`)
- Enum property defaults use the emitted entry name (`@SerialName`/`@JsonProperty` override) instead of the constant
  name, so `default` is always one of the schema's `enum` values
- `"nullable": true` is no longer stripped from a required nullable property
- Breaking: a sealed subtype that declares a property named like the polymorphic discriminator (default `type`, or the
  name from `@JsonClassDiscriminator`/`@JsonTypeInfo`) now fails schema generation with an `IllegalStateException`, in
  the JSON Schema and function-calling emitters, instead of silently dropping the discriminator `const`

### Fixed

- Distinct types sharing a name override (`@JsonTypeName`, `@SerialName`) are no longer merged into one node; behavior
  change: `TypeId.value` is now the declaration FQN even with an override, and duplicate explicit type names
  (including ambiguous discriminator values) now fail fast instead of silently falling back to the FQN.
  kotlinx.serialization also fails fast when distinct types share a serial
  name with different property names ([#147](https://github.com/kpavlov/kt-schema/issues/147))
- Generic inline value classes (e.g. `Wrapper<Int>`) now resolve their type arguments in the reflection and KSP front
  ends; previously reflection threw and KSP emitted an empty schema
- Self-wrapping inline value classes (e.g. `value class Tree(val children: List<Tree>)`) get a `$defs` entry of their
  wrapped shape (`{"type": "array", "items": {"$ref": ...}}`) in the reflection, KSP and `kotlinx.serialization` front
  ends; previously reflection emitted a dangling `$ref`, KSP failed processing and `kotlinx.serialization` overflowed
  the stack. Polymorphically recursive ones are cut off after 8 nested levels as "any value"
- KSP: typealiases (e.g. `typealias UserId = String`) resolve to the type they stand for instead of an empty schema; the
  `kotlin.collections` aliases `ArrayList`, `HashMap`, `LinkedHashMap`, `HashSet` and `LinkedHashSet` map to `array`/
  `object` schemas, and `Char` maps to `string` like in the reflection front end
- KSP: `@Schema(withSchemaObject = ...)` is now honored; previously the annotation arguments were never read and only
  the `me.kpavlov.kt.schema.withSchemaObject` processor option applied, contrary to the documented option priority. An
  explicit annotation value now takes precedence over the option
- KSP: processing errors point at the offending declaration and include the stack trace.
- KSP: inline value classes declared in another module (read from compiled output, where KSP reports `INLINE` instead
  of `VALUE`) are flattened to their wrapped type; previously they were emitted as an object with a `value` property
- `kt-schema-apt`: Kotlin `@JvmInline` value classes used as types in Java classes (detected by the
  `kotlin.jvm.JvmInline` annotation on the compiled box class) are flattened to their wrapped type, type arguments
  included; previously they were emitted as an object with a `value` property

### Dependencies

- Gradle 9.8.0, kotlinx.serialization 1.9.0, KSP 2.3.12, Kotest 6.2.5, JUnit 6.1.3, Jackson BOM 3.2.3 (test-only),
  detekt 2.0.0-alpha.6, Kover 0.9.11, slf4j 2.0.20, langchain4j 1.21.0, Koog 1.3.0 (examples)

---

## 0.8.3

Published: 2026-08-03

### Added

- Default values via Jackson annotations: `@JsonEnumDefaultValue` on an enum constant emits `default` on that enum's
  schema; `@JsonProperty(defaultValue = "...")` populates the property default. The property default only shows up with
  a non-strict `JsonSchemaConfig`, since KSP/APT output marks every property required. Configurable via
  `introspector.annotations.enumDefault.names`, `introspector.annotations.defaultValue.names` and
  `introspector.annotations.defaultValue.attributes` ([#99](https://github.com/kpavlov/kt-schema/pull/99))

### Docs

- Expanded guides (generation modes, configuration, architecture, type support), updated examples
  ([#98](https://github.com/kpavlov/kt-schema/pull/98))

---

## 0.8.2

Published: 2026-08-03

### Added

- `kt-schema-apt`: enum support, both as root types and as fields; `@JsonTypeName` / `@JsonProperty` override the enum
  and constant names ([#97](https://github.com/kpavlov/kt-schema/pull/97))

### Fixed

- An enum shared by several `@Schema` roots no longer crashes the processor on the second root
- `kt-schema-apt`: an enum with no constants now fails with a clear error instead of emitting `"enum": []`
- KSP: `@SerialName` on enum entries covered by tests

---

## 0.8.1

Published: 2026-08-02

### Changed

- **IR API (breaking for direct users of `kt-schema-generator-core`)**: `TypeId` is now a value class, `ObjectNode`/
  `EnumNode`/`PolymorphicNode` implement the new `NamedTypeNode`, and `PolymorphicNode.baseName` is now `name`
  ([#92](https://github.com/kpavlov/kt-schema/pull/92))
- Function-calling schemas fail with a clear error beyond 8 levels of type nesting (guards against cycles)

### Fixed

- Custom type names (`@SerialName`, `@JsonTypeName`, ...) are respected in `$id`, `$defs`, `$ref` and polymorphic
  discriminator values; unique graph-wide names fall back to the FQN on collision
  ([#92](https://github.com/kpavlov/kt-schema/pull/92))
- `kt-schema-apt`: `@Nullable` annotation is respected ([#91](https://github.com/kpavlov/kt-schema/pull/91))

### Chores

- Pure Java annotation-processing example, release pipeline

---

## 0.8.0

Published: 2026-08-02

### Added

- **`kt-schema-apt`**: Java annotation processor generating JSON Schema resources at compile time for Java records,
  classes and interfaces ([#75](https://github.com/kpavlov/kt-schema/pull/75))
    - Collections, maps, arrays and type variables ([#80](https://github.com/kpavlov/kt-schema/pull/80))
    - `rootPackage`, `include` and `exclude` options with glob patterns
      ([#82](https://github.com/kpavlov/kt-schema/pull/82)); KSP now shares the same glob matcher
- Jackson annotations support in reflection, KSP and APT: `@JsonProperty` (names), `@JsonTypeName` (polymorphic subtype
  names), `@JsonIgnore`; `@get:` use-site targets, inherited and singleton properties are honored
  ([#83](https://github.com/kpavlov/kt-schema/pull/83), [#84](https://github.com/kpavlov/kt-schema/pull/84))
- Nullable/optional conventions shared by all front ends: types matching `introspector.nullable.type.names` (default
  `*Opt`) and `@Nullable`-style annotations are nullable; optional markers (`introspector.annotations.optional.names`,
  `introspector.optional.type.names`) exclude a property from `required` and are opt-in
- Jackson 3 `JsonNode` hierarchy resolves to `{}` (opaque), and scalar nodes (`StringNode`, `IntNode`, ...) to their
  primitive types ([#74](https://github.com/kpavlov/kt-schema/issues/74))

### Fixed

- Nullable properties stay `required` by default
- `NoClassDefFoundError: org/slf4j/LoggerFactory` in KSP and APT processor workers (`slf4j-simple` is now bundled)

### Performance

- APT: type nodes are memoized across roots

### Dependencies

- Jackson 3 (test-only), KSP 2.3.10, Kotest 6.2.3, JUnit 6.1.2, detekt 2.0.0-alpha.5, Kover 0.9.9

---

## 0.7.0

Published: 2026-07-01

### Added

- `jsonSchemaOf<T>()` convenience function for kotlinx-serialization, analogous to `serializer<T>()`
  ([#60](https://github.com/kpavlov/kt-schema/pull/60))
- `@JsonClassDiscriminator` support: per-class discriminator key now takes precedence over the global
  `Json.classDiscriminator` setting in sealed polymorphic schema generation
  ([#53](https://github.com/kpavlov/kt-schema/issues/53))
- Built-in kotlinx-serialization JSON types (`JsonObject`, `JsonElement`, `JsonArray`, `JsonPrimitive`, `JsonNull`) are
  mapped to open schemas instead of crashing; configurable via
  `SerializationClassSchemaIntrospector.Config.opaqueSerialNames` and `introspector.opaque.type.names`
  ([#54](https://github.com/kpavlov/kt-schema/issues/54))

### Dependencies

- Gradle 9.6.1, KSP 2.3.9, langchain4j 1.17.1, kotlin-logging 8.0.4

---

## 0.6.0 Migrate to kt-schema 🚀

Published: 2026-06-29

## What's Changed

### Breaking

* **Migrate from kotlinx-schema to kt-schema** [#31](https://github.com/kpavlov/kt-schema/pull/31). Notable changes
  include renaming the Kotlin packages from `kotlinx.schema` to `me.kpavlov.kt.schema`, renaming Maven coordinates from
  `org.jetbrains.kotlinx` to `me.kpavlov`
* **Remove deprecated Apple platform targets** [#43](https://github.com/kpavlov/kt-schema/pull/43)

### Chores

* Compress AGENTS.md
* Update Dokka config
* Update GitHub workflows

---

<details>
<summary>kotlinx-schema changes</summary>

## 0.5.0

Published: 2026-04-07

### Added

- **Open polymorphism support**: abstract classes and interfaces generate JSON Schema when subtypes are registered
  via          
  `SerializersModule`; deterministic `oneOf`/`anyOf` output with descriptive errors for missing registrations
- **`@SerialDescription` annotation**: `@SerialInfo`-compatible description annotation read automatically by the
  serialization-based generator — no custom `DescriptionExtractor` needed (#303, #304)
- **`@SchemaIgnore` / `@SerialSchemaIgnore`**: exclude sealed subtypes from schema generation; Jackson
  `@JsonIgnoreType`      
  recognized by default; KSP fails fast on contradictory `@Schema` + `@SchemaIgnore` (#277)
- **`@SerialName` support in reflection and KSP**: class names, property names, enum entries, sealed subtypes,
  and            
  `$id`/`$defs`/`$ref` now honor `@SerialName` via FQN-aware annotation matching (#204, #308)

### Fixed

- **Recursive types**: schema generation correctly handles recursive sealed hierarchies and self-referencing types;
  subtypes  
  routed through cycle-safe path with idempotent discriminator injection (#307)
- **Inline value class schemas**: generation now matches kotlinx-serialization format; descriptions propagated to
  flattened
  primitive schema with correct property-level override precedence; TypeRef caching added
- **`@JsonClassDiscriminator` support**: per-class discriminator key from `@JsonClassDiscriminator` now takes precedence
  over the global `Json.classDiscriminator` setting in sealed polymorphic schema generation (#53)

### Dependencies

- Bump `kotest` from 6.1.7 to 6.1.11
- Bump `dokka-gradle-plugin` from 2.1.0 to 2.2.0
- Bump `kotlinx.kover` from 0.9.7 to 0.9.8
- Bump `gradle-wrapper` from 9.4.0 to 9.4.1
- Bump `mcp` from 0.9.0 to 0.11.0 (examples)
- Bump `ai.koog:agents-tools` from 0.6.4 to 0.7.3 (examples)

## 0.4.4

Published: 2026-03-18

### Fixed

* fix: returning empty description for multi-element annotations (#269)
* fix: description is not extracted from non-public annotation interfaces (#269)

---

## 0.4.3

Published: 2026-03-17

### Fixed

- **Java record class support**: reflection schema generator now correctly introspects Java `record` types (#263)
- **Nullable required constructor parameters**: default value extraction no longer silently skips nullable required
  parameters when building mock constructor arguments, restoring correct defaults detection for classes with
  patterns such as `data class Foo(val tag: String? = "abc", val count: Int? = 5)` (#263)
- **Private constructor filtering**: fallback constructor lookup now skips `private` constructors, preventing
  accidental invocation of synthetic or internal-only constructors (#263)

### Dependencies

- Bump `kotest` from 6.1.6 to 6.1.7

---

## 0.4.2

Published: 2026-03-13

### Fixed

- **Missing descriptions for inline primitive, list, and map nodes**: `node.description` is now correctly propagated
  for inline `PrimitiveNode`, `ListNode`, and `MapNode` schemas (#251)

### Dependencies

- Bump `kotest` from 6.1.5 to 6.1.6

---

## 0.4.1

Published: 2026-03-10

### Fixed

- **Missing property descriptions for class types**: class-level `@Description` annotations are now correctly propagated
  to nested object schemas (#238)

### Documentation

- Improved `Module.md` annotation descriptions and updated Dokka links (#241)

---

## 0.4.0

Published: 2026-03-09

### Breaking Changes

- **`$defs` for all named types**: all named types now always register in `$defs` and use `$ref` at every call site;
  previously only nullable named types were emitted as `$ref` (#194)
- **`kotlin.Any` maps to `{}`**: the previous output `{"type":"object","additionalProperties":false}` was incorrect;
  the unconstrained schema `{}` is now used per JSON Schema Draft 2020-12 (#194)
- **Polymorphic discriminator on by default**: `includePolymorphicDiscriminator` defaults to `true` — sealed subtype
  schemas now include a `const` discriminator property unless explicitly disabled (#212)
- **Fully-qualified subclass names**: sealed subclass names in `$defs`, `$ref`, and discriminator `const` values now
  use fully-qualified names (e.g. `com.example.Animal.Cat`) instead of simple names (#212)
- **`JsonSchemaConfig.Strict` and `FunctionCallingSchemaConfig.Strict`**: `respectDefaultPresence` set to `false` —
  all fields are required regardless of Kotlin default values (#212)

### Added

- **Polymorphic types**: sealed class hierarchies generate `oneOf` (JSON Schema) or `anyOf` (function calling) with a
  `const` discriminator per subtype, supported across KSP, reflection, and serialization backends (#212)
- **KSP class/function filtering**: `include` and `exclude` processor options accept glob patterns to filter which
  classes and functions receive generated extensions (#181)
- **Constructor parameter annotations for descriptions**: reflection generator now also searches constructor parameter
  annotations when resolving property descriptions (#203)
- **`suspend fun` support**: `ReflectionFunctionIntrospector` now correctly introspects suspendable functions (#212)

### Changed

- `ReflectionClassIntrospectionContext` and `ReflectionFunctionIntrospectionContext` merged into a single
  `ReflectionIntrospectionContext`, eliminating duplication (#212)
- `TDecl` type parameter removed from `BaseIntrospectionContext` (#212)

### Dependencies

- Bump `kotest` from 6.1.3 to 6.1.5
- Bump `ai.koog:agents-tools` from 0.6.2 to 0.6.4
- Bump `dev.langchain4j:langchain4j-core` from 1.11.0 to 1.12.2
- Bump `gradle` wrapper from 9.3.1 to 9.4.0

---

## 0.3.2

Published: 2026-02-20

### Added

- **Custom description extraction**: `SerializationClassJsonSchemaGenerator` now accepts
  `SerializationClassSchemaIntrospector.Config`
  with a pluggable `DescriptionExtractor` — map any annotation to the schema `description` field without modifying your
  models (#196)

### Documentation

- New guide: [Serialization-Based Schema Generation](docs/serializable.md) covering setup, configuration, and
  polymorphic types (#197)
- Knit integration: README and guide code examples are now compiled and verified (#187)

### Dependencies

- Bump `ksp` from 2.3.5 to 2.3.6
- Bump `io.github.oshai:kotlin-logging` from 7.0.14 to 8.0.01

## 0.3.1

Published: 2026-02-12

### Added

- Support WasmJS/Browser and watchOS X64 targets

### Fixed

- Move `slf4j.simple` dependency to test scope

## 0.3.0

Published: 2026-02-03

### Breaking Changes

- **Multiplatform migration**: `kotlinx-schema-generator-core` and `kotlinx-schema-generator-json` are now Kotlin
  Multiplatform
    - Affects internal introspection APIs and test structure
    - Reflection-based generators remain JVM-only; serialization-based generators now multiplatform

### Added

- **KDoc support**: Parameter, field, and property descriptions extracted from KDoc comments (#109, #148)
    - Works with KSP processor for compile-time generation
    - Complements `@Description` annotations

### Changed

- **Documentation**: Updated documentation and `Module.md` files
- **Test migration**: Moved tests to `commonTest` for multiplatform compatibility

### Fixed

- **Package structure**: Moved `TypeGraphToJsonObjectSchemaTransformer` to `kotlinx.schema.json`

## 0.2.0

Published: 2026-02-02

### Breaking Changes

- **JsonSchema: `additionalProperties` API**: Replaced `JsonPrimitive` with type-safe `AdditionalPropertiesConstraint`
  sealed interface
    - Use `AllowAdditionalProperties`, `DenyAdditionalProperties`, or `AdditionalPropertiesSchema(schema)` instead of
      boolean primitives
    - Enables compile-time type safety and better IDE support

### Added

- **kotlinx.serialization support**: New `SerializationClassJsonSchemaGenerator` for runtime introspection
    - Generate schemas from `SerialDescriptor` without KSP or reflection
    - Support for primitives, enums, objects, lists, maps, and polymorphic types
    - _**NB! Type/field descriptions are not supported due to limitations of `kotlinx.serialization` model!**_
- **Extended type-safe Schema DSL**
- **Internal API markers**: `@InternalSchemaGeneratorApi` annotation for APIs subject to change
- **Documentation**: Architecture pipeline overview with + diagrams

### Changed

- **Serializers refactoring**: Consolidated six enum serializers into generic `TypedEnumSerializer`
    - Moved serializers to a dedicated package for better organization
    - Simplified `StringOrListSerializer` and `AdditionalPropertiesSerializer`
- **Introspection architecture**: Extracted shared state management into `BaseIntrospectionContext<TDecl, TType>`
    - Eliminates code duplication across Reflection, KSP, and Serialization backends
    - Unified cycle detection and type caching

### Fixed

- Complex object parameters in function calling schemas are now handled correctly
    - Extracted shared type handlers (`handleAnyFallback`, `handleSealedClass`, `handleEnum`, `handleObjectOrClass`)
    - Improved error messages for unhandled KSType cases

### Dependencies

- Bump `kotlinx.kover` from 0.9.4 to 0.9.5
- Bump `gradle` from 9.3.0 to 9.3.1

## 0.1.0

Published: 2026-02-02

**Note**: Duplicate entry - see version below for actual release notes.

## 0.1.0

Published: 2026-01-30

### Breaking Changes

- Flattened `JsonSchema` structure - removed nested `JsonSchemaDefinition` wrapper
- Changed nullable representation from `"nullable": true` to `["type", "null"]` (JSON Schema 2020-12)
- Removed `strictSchemaFlag` configuration option
- Changed discriminator fields from `default` to `const` in sealed classes
- Reordered `JsonSchema` constructor parameters (`schema` before `id`)

### Added

- `useUnionTypes`, `useNullableField`, `includeDiscriminator` configuration flags
- `JsonSchemaConfig.Default`, `JsonSchemaConfig.Strict`, `JsonSchemaConfig.OpenAPI` presets
- Support for enum and primitive root schemas
- Centralized `formatSchemaId()` method for ID generation
- `JsonSchemaConstants` for reduced object allocation

### Changed

- Schemas now generate as flat JSON Schema Draft 2020-12 compliant output
- Updated to `ksp-maven-plugin` v0.3.0
- Enhanced KSP documentation with Gradle and Maven examples

### Fixed

- Enum root schemas now generate correctly (previously generated empty objects)
- Local classes now use `simpleName` fallback instead of failing

### Dependencies

- Bump `ai.koog:agents-tools` from 0.6.0 to 0.6.1
- Bump `com.google.devtools.ksp` from 2.3.4 to 2.3.5 (examples)

</details>