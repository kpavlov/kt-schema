---
title: Generated Schemas
description: What kt-schema emits for data classes, enums, nested objects, generics and sealed hierarchies, and how @Schema and @Description shape it.
menus:
  main:
    name: Schemas
    weight: 25
---

# Generated Schemas

## What Gets Generated

Schemas follow JSON Schema Draft 2020-12 format. Example (pretty-printed):

```json
{
    "$schema": "https://json-schema.org/draft/2020-12/schema",
    "$id": "com.example.Address",
    "type": "object",
    "properties": {
        "street": {
            "type": "string",
            "description": "Street address, including house number"
        },
        "city": {
            "type": "string",
            "description": "City or town name"
        },
        "zipCode": {
            "type": "string",
            "description": "Postal or ZIP code"
        },
        "country": {
            "type": "string",
            "description": "Two-letter ISO country code; defaults to US",
            "default": "US"
        }
    },
    "required": [
        "street",
        "city",
        "zipCode"
    ],
    "additionalProperties": false,
    "description": "A postal address for deliveries and billing."
}
```

- Enums are `type: string` with `enum: []` and carry `@Description` as `description`.
- Object properties include their inferred type schema and, when present, property-level `@Description` as`description`.
- **Default values** are automatically extracted and included in the schema when using **runtime reflection** (e.g.,
  `val country: String = "US"` → `"default": "US"`; `val note: String? = null` → `"default": null`). KSP (compile-time)
  records that a property has a default but cannot extract its value, so it emits no `default`. A property is *optional*
  when it has a Kotlin default or an explicit optional marker; a known default also makes it non-required, and it is
  emitted as `default` only for non-required properties.
- Nullable properties are emitted as a union including `null`.
- Collections: `List<T>`/`Set<T>` → `{ "type":"array", "items": T }`; `Map<String, V>` →
  `{ "type":"object", "additionalProperties": V }`.
- **Validation constraints**: Jakarta Bean Validation annotations (`@Min`, `@Size`, `@Pattern`, `@Email`, …) and
  built-in type ranges (`Byte`, `UInt`, `Char`) and formats (`UUID`, `Instant`, `LocalDate`) become JSON Schema
  keywords — see the [Validation Constraints guide](constraints.md).
- `kotlin.Any` / unbound generic type parameters (e.g., `T`) map to the empty schema `{}`, which accepts any JSON value.
- Named types (nested objects, enums, sealed classes) are deduplicated in a `$defs` section and referenced via `$ref` at
  every usage site.

## Examples

### Basic data classes

Here's a practical example of a product model with various property types:

<!--- CLEAR -->
<!--- INCLUDE
import me.kpavlov.kt.schema.Schema
import me.kpavlov.kt.schema.Description
-->

```kotlin
@Description("A purchasable product with pricing and inventory info.")
@Schema
data class Product(
    @Description("Unique identifier for the product")
    val id: Long,
    @Description("Human-readable product name")
    val name: String,
    @Description("Optional detailed description of the product")
    val description: String?,
    @Description("Unit price expressed as a decimal number")
    val price: Double,
    @Description("Whether the product is currently in stock")
    val inStock: Boolean = true,
    @Description("List of tags for categorization and search")
    val tags: List<String> = emptyList(),
)
```

<!--- KNIT example-knit-generated-01.kt --> 

Use the generated extensions:

```kotlin
val schema = Product::class.jsonSchemaString
val schemaObject = Product::class.jsonSchema
```

<details>
<summary>Generated JSON schema</summary>

```json
{
    "$schema": "https://json-schema.org/draft/2020-12/schema",
    "$id": "com.example.Product",
    "type": "object",
    "properties": {
        "id": {
            "type": "integer",
            "description": "Unique identifier for the product"
        },
        "name": {
            "type": "string",
            "description": "Human-readable product name"
        },
        "description": {
            "type": [
                "string",
                "null"
            ],
            "description": "Optional detailed description of the product"
        },
        "price": {
            "type": "number",
            "description": "Unit price expressed as a decimal number"
        },
        "inStock": {
            "type": "boolean",
            "description": "Whether the product is currently in stock"
        },
        "tags": {
            "type": "array",
            "items": {
                "type": "string"
            },
            "description": "List of tags for categorization and search"
        }
    },
    "required": [
        "id",
        "name",
        "description",
        "price"
    ],
    "additionalProperties": false,
    "description": "A purchasable product with pricing and inventory info."
}
```

</details>

### Enums with descriptions

Enums are supported with descriptions on both the enum class and individual values:

<!--- CLEAR -->
<!--- INCLUDE
import me.kpavlov.kt.schema.Schema
import me.kpavlov.kt.schema.Description
-->

```kotlin
@Description("Current lifecycle status of an entity.")
@Schema
enum class Status {
    @Description("Entity is active and usable")
    ACTIVE,

    @Description("Entity is inactive or disabled")
    INACTIVE,

    @Description("Entity is pending activation or approval")
    PENDING,
}
```

<!--- KNIT example-knit-generated-02.kt -->

<details>
<summary>Generated JSON schema</summary>

```json
{
    "$schema": "https://json-schema.org/draft/2020-12/schema",
    "$id": "com.example.Status",
    "type": "string",
    "enum": [
        "ACTIVE",
        "INACTIVE",
        "PENDING"
    ],
    "description": "Current lifecycle status of an entity."
}
```

</details>

### Nested objects

You can compose schemas by nesting annotated classes:

<!--- CLEAR -->
<!--- INCLUDE
import me.kpavlov.kt.schema.Schema
import me.kpavlov.kt.schema.Description

@Schema
data class Address(val street: String, val city: String, val zipCode: String, val country: String = "US")

@Schema
data class Product(val id: Long, val name: String, val description: String?, val price: Double, val inStock: Boolean = true, val tags: List<String> = emptyList())

@Schema
enum class Status { ACTIVE, INACTIVE, PENDING }
-->

```kotlin
@Description("A person with a first and last name and age.")
@Schema
data class Person(
    @Description("Given name of the person")
    val firstName: String,
    @Description("Family name of the person")
    val lastName: String,
    @Description("Age of the person in years")
    val age: Int,
)

@Description("An order placed by a customer containing multiple items.")
@Schema
data class Order(
    @Description("Unique order identifier")
    val id: String,
    @Description("The customer who placed the order")
    val customer: Person,
    @Description("Destination address for shipment")
    val shippingAddress: Address,
    @Description("List of items included in the order")
    val items: List<Product>,
    @Description("Current status of the order")
    val status: Status,
)
```

<!--- KNIT example-knit-generated-03.kt -->

The generated schema for `Order` will automatically include definitions for all nested types (`Person`, `Address`,
`Product`, `Status`) in the `$defs` section, with appropriate `$ref` pointers to link them together. This makes it easy
to build complex, composable data models.

### Generic types

Generic classes are supported, with type parameters resolved at usage sites:

<!--- CLEAR -->
<!--- INCLUDE
import me.kpavlov.kt.schema.Schema
import me.kpavlov.kt.schema.Description
-->

```kotlin
@Description("A generic container that wraps content with optional metadata.")
@Schema
data class Container<T>(
    @Description("The wrapped content value")
    val content: T,
    @Description("Arbitrary metadata key-value pairs")
    val metadata: Map<String, Any> = emptyMap(),
)
```

<!--- KNIT example-knit-generated-04.kt -->

Generic type parameters are resolved at the usage site. Unbound type parameters (like `T`), star projections and
`kotlin.Any`-typed properties map to `{}` — the empty JSON Schema that accepts any JSON value. For more specific typing,
instantiate the generic class with concrete types when you need them.

Each application of a generic class gets its own definition, named after its type arguments: `Box<String>` and
`Box<Int>` in one schema become `Box_of_String` and `Box_of_Int`, `Pair<String, Int>` becomes `Pair_of_String_and_Int`
and `Box<String?>` becomes `Box_of_nullable_String`. If the argument names clash, package segments are added. With
kotlinx.serialization a class is named this way only when it is used with several type arguments, after the
properties whose types differ, as a descriptor can't tell type arguments from other properties.

### Sealed class polymorphism

The library automatically generates JSON schemas for Kotlin sealed class hierarchies using `oneOf`:

<!--- CLEAR -->
<!--- INCLUDE
import me.kpavlov.kt.schema.Schema
import me.kpavlov.kt.schema.Description
import me.kpavlov.kt.schema.generator.json.ReflectionClassJsonSchemaGenerator
import me.kpavlov.kt.schema.json.encodeToString
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json
-->

```kotlin
@Description("Multicellular eukaryotic organism of the kingdom Metazoa")
@Schema
sealed class Animal {
    /**
     * Animal's name
     */
    @Description("Animal's name")
    abstract val name: String

    @Schema(withSchemaObject = true)
    data class Dog(
        @Description("Animal's name")
        override val name: String,
    ) : Animal()

    // @SerialName overrides the emitted type name for this subtype only (see below)
    @SerialName("Cat")
    @Schema(withSchemaObject = true)
    data class Cat(
        @Description("Animal's name")
        override val name: String,
    ) : Animal()
}
```

<!--- INCLUDE 
fun main() {
-->

```kotlin
val generator = ReflectionClassJsonSchemaGenerator.Default
val schema = generator.generateSchema(Animal::class)

println(schema.encodeToString(Json { prettyPrint = true }))
```

<!--- SUFFIX
}
-->
<!--- KNIT example-knit-generated-05.kt -->

<details>
<summary>Generated JSON schema</summary>

```json
{
    "$schema": "https://json-schema.org/draft/2020-12/schema",
    "$id": "me.kpavlov.kt.schema.integration.type.Animal",
    "description": "Multicellular eukaryotic organism of the kingdom Metazoa",
    "type": "object",
    "oneOf": [
        {
            "$ref": "#/$defs/Cat"
        },
        {
            "$ref": "#/$defs/me.kpavlov.kt.schema.integration.type.Animal.Dog"
        }
    ],
    "$defs": {
        "Cat": {
            "type": "object",
            "properties": {
                "type": {
                    "type": "string",
                    "const": "Cat"
                },
                "name": {
                    "type": "string",
                    "description": "Animal's name"
                }
            },
            "required": [
                "type",
                "name"
            ],
            "additionalProperties": false
        },
        "me.kpavlov.kt.schema.integration.type.Animal.Dog": {
            "type": "object",
            "properties": {
                "type": {
                    "type": "string",
                    "const": "me.kpavlov.kt.schema.integration.type.Animal.Dog"
                },
                "name": {
                    "type": "string",
                    "description": "Animal's name"
                }
            },
            "required": [
                "type",
                "name"
            ],
            "additionalProperties": false
        }
    }
}
```

</details>

**Key features:**

- **`oneOf` with `$ref`**: Each sealed subclass is stored in `$defs` and referenced via `$ref`
- **Fully qualified names by default**: `$defs` keys and discriminator `const` values use fully qualified class names
  (e.g., `com.example.Animal.Dog`) to avoid collisions across packages
- **Name overrides**: a subtype annotated with `@SerialName`/`@JsonTypeName` (or another recognized name-override
  annotation) uses that short name instead of its FQN — like `Cat` above, overridden via `@SerialName("Cat")`.
  Two declarations with the same explicit name fail schema generation instead of being renamed
- **Discriminator property**: A `type` field with a `const` value is automatically added to each subtype for runtime
  dispatch. The reflection and KSP front ends take the name from `@JsonClassDiscriminator("...")` or
  `@JsonTypeInfo(property = "...")` on the sealed type or its supertypes; a subtype declaring a property with that name
  fails schema generation
- **Property inheritance**: Base class properties are included in each subtype
- **Type safety**: Each subtype gets its own schema definition

#### Excluding subtypes with @SchemaIgnore

Use `@SchemaIgnore` to exclude specific sealed subtypes from the generated schema.
The class remains fully functional at runtime — only schema generation is affected:

```kotlin
@Schema
sealed class Event {
    data class Click(val x: Int, val y: Int) : Event()

    @SchemaIgnore
    data class Internal(val trace: String) : Event()
}
```

The `Internal` subtype will not appear in the `oneOf` composition or `$defs`.

For serialization-based generation, use `@SerialSchemaIgnore` (which carries `@SerialInfo`):

```kotlin
@Serializable
sealed class Event {
    @Serializable
    data class Click(val x: Int, val y: Int) : Event()

    @Serializable
    @SerialSchemaIgnore
    data class Internal(val trace: String) : Event()
}
```

Jackson's `@JsonIgnoreType` is also recognized automatically. Custom ignore annotations can be
registered via `kt-schema.properties`:

```properties
introspector.annotations.ignore.names=SchemaIgnore,SerialSchemaIgnore,JsonIgnoreType,MyCustomIgnore
```

## Using @Schema and @Description annotations

### @Schema annotation

Mark classes with `@Schema` to generate extension properties for them:

```kotlin
@Schema  // Uses default schema type "json"
data class Address(val street: String, val city: String)

@Schema("json")  // Explicitly specify schema type
data class Person(val name: String, val age: Int)
```

**@Schema parameters:**

- `value = "json"`: Schema type (only JSON currently supported)
- `withSchemaObject = false`: Generate `jsonSchema: JsonObject` property
  (see [Advanced Configuration](ksp.md#configuration-options))

**Note**: `jsonSchemaString` is always generated. `jsonSchema` requires `withSchemaObject = true`.

### @Description annotation

Use `@Description` on classes and properties to add human-readable documentation to your schemas:

```kotlin
@Description("A purchasable product with pricing info")
@Schema
data class Product(
    @Description("Unique identifier for the product") val id: Long,
    @Description("Human-readable product name") val name: String,
    @Description("Optional detailed description of the product") val description: String?,
    @Description("Unit price expressed as a decimal number") val price: Double,
)
```

**Tip**: With the recommended compiler flag `-Xannotation-default-target=param-property`, a bare `@Description` on a
primary constructor parameter also applies to the property. If you do not enable the flag, use `@param:Description` for
constructor-declared properties.

