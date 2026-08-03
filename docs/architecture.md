# Architecture

`kt-schema` is a layered library that generates schemas—primarily JSON Schema—from Kotlin declarations
and Java classes. It unifies compile-time analysis (KSP) and runtime inspection (reflection) by translating
both into a shared Internal Representation (IR).

The architecture is modular by design, so you can plug in new schema formats and generation strategies while
keeping behavior consistent across JVM, JS, Native, and Wasm.

**Architecture goals:**

- **Unified IR**: Separate schema sources (KSP, reflection, or [SerialDescriptor][kser-descriptor]) from output targets
- **Multiplatform (KSP)**: Support schema generation across all Kotlin targets.
- **Extensibility**: Enable third-party annotations, custom introspectors and transformations.
- **Zero runtime overhead on KSP**: Compile-time generation for performance-sensitive paths.
- **Third-party support**: Generate schemas for types you don't own without editing their source.

## Overview

The library implements the following pipeline: 

```mermaid
graph LR
    subgraph Sources["📦 SOURCES"]
        Java["Java Classes<br/>Third-party libs"]
        Functions["Kotlin Functions"]
        Kotlin["Kotlin Classes<br/>@Schema annotated"]
        KSerializer["SerialDescriptor"]
    end

    subgraph Introspectors["🔍 Stage 1: INTROSPECTORS"]
        KSP["KspSchemaIntrospector<br/><i>compile-time</i>"]
        Serialization["SerializationClassSchemaIntrospector<br/><i>runtime</i>"]
        Apt["AptClassIntrospector<br/><i>compile-time</i>"]
        Reflect["ReflectionSchemaIntrospector<br/><i>runtime</i>"]
    end

    subgraph IR["🧬 INTERNAL REPRESENTATION"]
        TypeGraph["TypeGraph<br/>Unified intermediate format<br/>Properties • Types • Descriptions<br/>Nullable • Defaults"]
    end

    subgraph Transformers["⚙️ TRANSFORMERS"]
        JsonTransform["JsonSchemaTransformer<br/>Draft 2020-12"]
        FuncTransform["FunctionCallingTransformer<br/>(OpenAI/Anthropic format)"]
    end

    subgraph Output["📄 SCHEMA OUTPUTS"]
        KtClass["Generated .kt<br/>MyClass::class.jsonSchema<br/>MyClass::class.jsonSchemaString"]
        KtFunc["Generated .kt<br/>myMethodJsonSchema()<br/>myMethodJsonSchemaString()"]
        JsonSchema["JsonSchema<br/>(@Serializable)"]
        JsonObj["JsonObject<br/>kotlinx.serialization"]
        JsonStr["JSON String<br/>Serialized schema"]
        AptResource["Generated .json resource<br/>META-INF/kt-schema/schemas/..."]
        FuncSchema["FunctionCallingSchema<br/>(@Serializable)"]
    end

    Kotlin --> KSP
    KSerializer --> Serialization
    Java --> Reflect
    Java --> Apt
    Functions --> KSP
    Functions --> Reflect

    KSP --> TypeGraph
    Apt --> TypeGraph
    Reflect --> TypeGraph
    Serialization --> TypeGraph

    TypeGraph --> JsonTransform
    TypeGraph --> FuncTransform

    JsonTransform --> JsonSchema
    FuncTransform --> FuncSchema
    JsonSchema --> JsonObj
    JsonSchema --> AptResource
    FuncSchema --> KtFunc
    JsonObj --> JsonStr
    JsonObj --> KtClass

    classDef sourceStyle fill:#e3f2fd,stroke:#1565c0,stroke-width:3px,color:#000
    classDef introspectorStyle fill:#fff3e0,stroke:#ef6c00,stroke-width:3px,color:#000
    classDef irStyle fill:#fce4ec,stroke:#c2185b,stroke-width:4px,color:#000
    classDef transformStyle fill:#f3e5f5,stroke:#7b1fa2,stroke-width:3px,color:#000
    classDef outputStyle fill:#e8f5e9,stroke:#2e7d32,stroke-width:3px,color:#000

    class Kotlin,Java,JavaApt,Functions,KSerializer sourceStyle
    class KSP,Apt,Reflect,Serialization introspectorStyle
    class TypeGraph irStyle
    class JsonTransform,FuncTransform transformStyle
    class KtClass,KtFunc,JsonSchema,FuncSchema,JsonObj,JsonStr,AptResource outputStyle
```

**The Transformation Story:**

1. **Sources** — Kotlin classes, Java classes/records, Kotlin functions, or [SerialDescriptor][kser-descriptor] serve as input
2. **Introspectors** — Extract type information at compile-time (KSP, [Java APT](apt.md)) or runtime (Reflection, Serialization)
3. **TypeGraph** — Unified internal representation containing all type metadata
4. **Transformers** — Convert TypeGraph to JSON Schema or Function Calling format
5. **Outputs** — Generated Kotlin code, a generated `.json` resource (Java APT), JsonSchema, FunctionCallingSchema, and then to JsonObject, or JSON strings

## Module Dependencies

```mermaid
C4Context
    title kt-schema

    Boundary(lib, "kt-schema") {
        System(kxsKs, "kt-schema-ksp")
        System(kxsAnnotations, "kt-schema-annotations")
        System(kxsGenJson, "kt-schema-generator-json")
        System(kxsJsn, "kt-schema-json")
        System(kxsKsp, "kt-schema-ksp")
        System(kxsApt, "kt-schema-apt")
        System(kxsGradle, "kt-schema-gradle-plugin")
    }

    Rel(kxsGenJson, kxsGenCore, "uses")
    Rel(kxsGenJson, kxsJsn, "uses")
    Rel(kxsGenCore, kxsAnnotations, "knows")
    Rel(kxsKsp, kxsGenJson, "uses")
    Rel(kxsApt, kxsGenJson, "uses")
    Rel(kxsGradle, kxsKsp, "uses")

    Boundary(userCode, "User's Application Code") {
        System_Ext(userModels, "User Domain Models")
        System_Ext(userModelsExt, "User Models Extensions")
        Rel(userModelsExt, userModels, "uses")
    }

    Rel(userModels, kxsAnnotations, "uses")
    Rel(kxsKsp, userModelsExt, "generates")
    Rel(kxsApt, userModels, "generates schema resource for")

```

Top-level modules you might interact with:

- **kt-schema-annotations** — runtime annotations: @Schema and @Description
- **kt-schema-json** — type-safe models and DSL for building JSON Schema definitions programmatically
- **kt-schema-generator-core** — core abstractions, intermediate representation (IR) for schema descriptions, introspection utils,
  generator interfaces
- **kt-schema-generator-json** — JSON Schema transformer from the IR, kotlinx-serialization schema generator
- **kt-schema-ksp-processor** — KSP processor that scans your code and generates the extension properties:
    - `KClass<T>.jsonSchema: JsonObject`
    - `KClass<T>.jsonSchemaString: String`
- **kt-schema-apt** — [JSR 269 annotation processor](apt.md) for plain Java projects; generates a JSON Schema
  resource per processed type instead of Kotlin extensions
- **ksp-integration-tests** — KSP end‑to‑end tests for generation without the Gradle plugin
- **apt-integration-tests** — Java-only end‑to‑end tests for `kt-schema-apt`

### Workflow

```mermaid
sequenceDiagram
    actor C as Client
    participant S as SchemaGeneratorService
    participant G as SchemaGenerator
    participant I as SchemaIntrospector
    participant T as TypeGraphTransformer
    note over T: has Config
    C ->> S: getGenerator(T::class, R::class)
    S -->> G: find
    activate G
    S -->> C: SchemaGenerator
    C ->> G: generate(T) : R?
    G ->> I: introspect(T)
    I -->> G: TypeGraph
    G ->> T: transform(TypeGraph, rootName)
    T -->> G: schema (R)
    G -->> C: schema (R)
    deactivate G
```

1. _Client_ (KSP Processor or Java class) calls _SchemaGeneratorService_ to lookup _SchemaGenerator_
   by target type T and expected schema class. _SchemaGeneratorService_ returns _SchemaGenerator_, if any.
2. _Client_ (KSP Processor or Java class) calls _SchemaGenerator_ to generate a Schema string representation,
   and, optionally, object a Schema string representation.
3. SchemaGenerator invokes SchemaIntrospector to convert an object into _TypeGraph_
4. _TypeGraphTransformer_ converts a _TypeGraph_ to a target representation (e.g., JSON Schema)
   with respect to respecting _Config_ object and returns it to SchemaGenerator

[kser-descriptor]: https://kotlinlang.org/api/kotlinx.serialization/kotlinx-serialization-core/kotlinx.serialization.descriptors/-serial-descriptor/

