---
title: Function Calling
description: Generate tool definitions for OpenAI, Anthropic and MCP from Kotlin functions, at runtime via reflection or at compile time via KSP.
menus:
  main:
    name: Function Calling
    weight: 50
---

# Function Calling

LLM APIs (OpenAI, Anthropic) and the Model Context Protocol (MCP) call your code through *tools*: each tool is a
name, a description and a JSON Schema for its parameters. kt-schema builds that definition from a Kotlin function,
so parameter names, types, nullability and `@Description` text stay in one place.

## Provider formats

The generated `FunctionCallingSchema` serializes to the
[OpenAI Responses API function tool](https://developers.openai.com/api/docs/guides/function-calling) shape:
`type`, `name`, `description`, `strict` and `parameters` at the top level. Other APIs take the same `parameters`
object under a different key:

| API                                                                                                       | Wrapper                                                   | Parameters key | `strict`                                |
|:----------------------------------------------------------------------------------------------------------|:----------------------------------------------------------|:---------------|:----------------------------------------|
| OpenAI Responses API                                                                                      | as generated                                              | `parameters`   | top-level `strict: true`                |
| OpenAI Chat Completions                                                                                   | nest `name`/`description`/`parameters` under `function`   | `parameters`   | inside `function`                       |
| [Anthropic Messages API](https://platform.claude.com/docs/en/agents-and-tools/tool-use/strict-tool-use) | `name`, `description`, `input_schema`                     | `input_schema` | top-level `strict: true`                |
| [MCP tool](https://modelcontextprotocol.io/specification/latest/server/tools)                             | `name`, `title`, `description`, `inputSchema`             | `inputSchema`  | n/a                                     |

The default `strict` output has the shape OpenAI strict mode and Anthropic strict tool use require: every property
is in `required`, `additionalProperties` is `false`, and optional values are expressed as `["<type>", "null"]`.
Anthropic strict tool use also rejects numeric and string-length keywords (`minimum`, `maxLength`, …). Those come from
[validation constraints](constraints.md) and from the built-in ranges of `Byte`, `Short`, `Char` and the unsigned types,
so in parameters you send there with `strict: true`, drop the constraints and use `Int`, `Long` or `String` instead.

## Basic Usage

<!--- CLEAR -->
<!--- INCLUDE
import me.kpavlov.kt.schema.Description
import me.kpavlov.kt.schema.generator.json.ReflectionFunctionCallingSchemaGenerator
import kotlinx.serialization.Serializable

@Serializable
data class WeatherInfo(val temp: Double, val unit: String)
-->

```kotlin
@Description("Get current weather for a location")
fun getWeather(
    @Description("City and country, e.g. 'London, UK'")
    location: String,

    @Description("Temperature unit")
    unit: String = "celsius"
): WeatherInfo {
    return WeatherInfo(20.0, unit)
}

val generator = ReflectionFunctionCallingSchemaGenerator.Default
val schema = generator.generateSchema(::getWeather)
```

<!--- KNIT example-knit-function-calling-01.kt -->

## Generated Schema

The generated schema follows the LLM function calling format:

```json
{
    "type": "function",
    "name": "getWeather",
    "description": "Get current weather for a location",
    "strict": true,
    "parameters": {
        "type": "object",
        "properties": {
            "location": {
                "type": "string",
                "description": "City and country, e.g. 'London, UK'"
            },
            "unit": {
                "type": "string",
                "description": "Temperature unit"
            }
        },
        "required": [
            "location",
            "unit"
        ],
        "additionalProperties": false
    }
}
```

## Key Features

- **Automatic extraction**: Function name and descriptions from `@Description` annotations
- **Default values**: With a non-strict config such as `FunctionCallingSchemaConfig.Simple`, a property default in a
  nested data class (e.g., `data class Config(val port: Int = 8080)`) becomes `default` and leaves the property
  optional. Strict mode makes every property required and emits no `default`
- **Strict mode**: `strict: true` enables
  OpenAI's [strict mode](https://developers.openai.com/api/docs/guides/function-calling#strict-mode) and
  Anthropic's [strict tool use](https://platform.claude.com/docs/en/agents-and-tools/tool-use/strict-tool-use), so
  the model's arguments are guaranteed to match the schema
- **Union types**: Nullable parameters use `["string", "null"]` instead of `nullable: true`
- **Required by default**: All parameters marked as required, as strict mode demands; a `null` union marks the
  optional ones
- **Type safety**: Proper JSON Schema types from Kotlin types (Int → integer, String → string, etc.)

> **Note:** Function parameter defaults (e.g., `unit: String = "celsius"`) cannot be extracted via reflection, unlike
> nested data class property defaults.

## Working with Multiple Functions

<!--- CLEAR -->
<!--- INCLUDE
import me.kpavlov.kt.schema.Description
import me.kpavlov.kt.schema.generator.json.ReflectionFunctionCallingSchemaGenerator
import kotlinx.serialization.json.Json
import me.kpavlov.kt.schema.json.encodeToJsonObject
-->

```kotlin
// Define your functions
@Description("Search the knowledge base")
fun searchKnowledge(
    @Description("Search query") query: String,
    @Description("Max results") limit: Int = 10
): String = TODO()

@Description("Calculate order total with tax")
fun calculateTotal(
    @Description("Item prices") prices: List<Double>,
    @Description("Tax rate as decimal") taxRate: Double = 0.0
): Double = TODO()

// Generate schemas
val generator = ReflectionFunctionCallingSchemaGenerator.Default
val schemas = listOf(::searchKnowledge, ::calculateTotal)
    .map { generator.generateSchema(it) }

// Serialize to JSON
val jsonSchemas = schemas.map { Json.encodeToString(it) }

// Or get as JsonObject
val schemaObjects = schemas.map { it.encodeToJsonObject() }
```

<!--- KNIT example-knit-function-calling-02.kt -->

Send the result through your provider's client library; see [Provider formats](#provider-formats) for the key
each API expects the parameters under.

## Nullable Parameters

Nullable parameters are represented as union types:

<!--- CLEAR -->
<!--- INCLUDE
import me.kpavlov.kt.schema.Description
import kotlinx.serialization.Serializable
-->

```kotlin
@Description("Update user profile")
fun updateProfile(
    @Description("User ID") userId: String,
    @Description("New name, if changing") name: String? = null,
    @Description("New email, if changing") email: String? = null
): Boolean = TODO("does not matter")// ...
```

<!--- KNIT example-knit-function-calling-03.kt -->

Generates:

```json
{
    "properties": {
        "userId": {
            "type": "string",
            "description": "User ID"
        },
        "name": {
            "type": [
                "string",
                "null"
            ],
            "description": "New name, if changing"
        },
        "email": {
            "type": [
                "string",
                "null"
            ],
            "description": "New email, if changing"
        }
    },
    "required": [
        "userId",
        "name",
        "email"
    ]
}
```

**Note**: Even nullable parameters are in `required` array. The `null` type in the union indicates optionality.

For more details on function calling schemas and OpenAI compatibility,
see [kt-schema-json/README.md](../kt-schema-json/README.md#function-calling-schema-for-llm-apis).

## Compile-time Function Schema Generation (KSP)

Generate function schemas at compile time with zero runtime overhead. KSP generates type-safe extensions for all your
annotated functions, with APIs that reflect where functions actually live in your code.

### Top-Level Functions

Annotate package-level functions to generate top-level schema accessors:

<!--- CLEAR -->
<!--- INCLUDE
import me.kpavlov.kt.schema.Schema
import me.kpavlov.kt.schema.Description

fun greetPersonJsonSchemaString(): String = ""
-->

```kotlin
@Schema
@Description("Sends a greeting message to a person")
fun greetPerson(
    @Description("Name of the person to greet")
    name: String,
    @Description("Optional greeting prefix (e.g., 'Hello', 'Hi')")
    greeting: String = "Hello",
): String = "$greeting, $name!"

// Generated: top-level functions
val schema = greetPersonJsonSchemaString()
```

<!--- KNIT example-knit-function-calling-04.kt -->

### Instance/Member Functions

Annotate class methods to generate `KClass` extensions on the containing class:

<!--- CLEAR -->
<!--- INCLUDE
import me.kpavlov.kt.schema.Schema
import me.kpavlov.kt.schema.Description
import kotlin.reflect.KClass

fun KClass<UserService>.registerUserJsonSchemaString(): String = ""
-->

```kotlin
class UserService {
    @Schema
    @Description("Registers a new user in the system")
    fun registerUser(
        @Description("Username for the new account")
        username: String,
        @Description("Email address")
        email: String,
    ): String = "User registered"
}

// Generated: KClass extension on UserService
val schema = UserService::class.registerUserJsonSchemaString()
```

<!--- KNIT example-knit-function-calling-05.kt -->

### Companion Object Functions

Annotate companion methods to generate `KClass` extensions on the companion object itself:

<!--- CLEAR -->
<!--- INCLUDE
import me.kpavlov.kt.schema.Schema
import me.kpavlov.kt.schema.Description
import kotlin.reflect.KClass

fun KClass<DatabaseConnection.Companion>.createJsonSchemaString(): String = ""
-->

```kotlin
class DatabaseConnection {
    companion object {
        @Schema
        @Description("Creates a new database connection")
        fun create(
            @Description("Database host")
            host: String,
            @Description("Database port")
            port: Int = 5432,
        ): DatabaseConnection = TODO()
    }
}

// Generated: KClass extension on companion object
val schema = DatabaseConnection.Companion::class.createJsonSchemaString()
```

<!--- KNIT example-knit-function-calling-06.kt -->

This API correctly reflects that companion functions belong to the companion object, not the parent class.

### Singleton Object Functions

Annotate object methods to generate `KClass` extensions on the object type:

<!--- CLEAR -->
<!--- INCLUDE
import me.kpavlov.kt.schema.Schema
import me.kpavlov.kt.schema.Description
import kotlin.reflect.KClass

fun KClass<ConfigurationManager>.loadConfigJsonSchemaString(): String = ""
-->

```kotlin
object ConfigurationManager {
    @Schema
    @Description("Loads configuration from a file")
    fun loadConfig(
        @Description("Configuration file path")
        filePath: String,
        @Description("Whether to create file if it doesn't exist")
        createIfMissing: Boolean = false,
    ): Map<String, String> = TODO()
}

// Generated: KClass extension on object
val schema = ConfigurationManager::class.loadConfigJsonSchemaString()
```

<!--- KNIT example-knit-function-calling-07.kt -->

### What Gets Generated

KSP generates schema accessor functions that match where your functions live:

| Function Type | Annotate         | Generated API                | Example                                                        |
|---------------|------------------|------------------------------|----------------------------------------------------------------|
| **Top-level** | Package function | Top-level accessor           | `greetPersonJsonSchemaString()`                                |
| **Instance**  | Class method     | `KClass` extension           | `UserService::class.registerUserJsonSchemaString()`            |
| **Companion** | Companion method | `Companion::class` extension | `DatabaseConnection.Companion::class.createJsonSchemaString()` |
| **Object**    | Object method    | `Object::class` extension    | `ConfigurationManager::class.loadConfigJsonSchemaString()`     |

For each annotated function, you get:

- **Always**: `{functionName}JsonSchemaString(): String` — returns the schema as a JSON string
- **Optional**: `{functionName}JsonSchema(): FunctionCallingSchema` — returns the schema object (requires
  `withSchemaObject = true`)

### Schema Format

Generated schemas use the [OpenAI Responses API function tool](https://developers.openai.com/api/docs/guides/function-calling)
shape (see [Provider formats](#provider-formats)):

```json
{
    "type": "function",
    "name": "greetPerson",
    "description": "Sends a greeting message to a person",
    "strict": true,
    "parameters": {
        "type": "object",
        "properties": {
            "name": {
                "type": "string",
                "description": "Name of the person to greet"
            },
            "greeting": {
                "type": "string",
                "description": "Optional greeting prefix"
            }
        },
        "required": [
            "name",
            "greeting"
        ],
        "additionalProperties": false
    }
}
```

**Strict mode**: All parameters are marked as required by default, even those with default values, as
[OpenAI strict mode](https://developers.openai.com/api/docs/guides/function-calling#strict-mode) requires.
With a non-strict config (`lenient`, `openapi`), parameters that have a Kotlin default value are omitted from
`required`; KSP can't read the default's value, so no `default` is emitted.

### KSP vs Runtime Reflection

| Feature            | KSP (Compile-time)                                                                      | Reflection (Runtime)                     |
|--------------------|-----------------------------------------------------------------------------------------|------------------------------------------|
| **Performance**    | Zero runtime cost                                                                       | Small reflection overhead                |
| **Platforms**      | Multiplatform                                                                           | JVM only                                 |
| **Default values** | Tracked but not extracted ([KSP limitation](https://github.com/google/ksp/issues/1868)) | Fully extracted from data classes        |
| **When to use**    | Your annotated functions                                                                | Third-party functions, dynamic scenarios |

### Suspend Functions

Suspend functions work identically to regular functions. The generated schemas don't expose the suspend modifier—they
describe parameter types only:

<!--- CLEAR -->
<!--- INCLUDE
import me.kpavlov.kt.schema.Schema
import me.kpavlov.kt.schema.Description
import kotlinx.serialization.Serializable

@Serializable
data class UserData(val id: Long)

fun fetchUserDataJsonSchemaString(): String = ""
-->

```kotlin
@Schema
@Description("Fetches user data asynchronously")
suspend fun fetchUserData(
    @Description("User ID to fetch") userId: Long,
): UserData = TODO()

// Generated API works the same way
val schema = fetchUserDataJsonSchemaString()
```

<!--- KNIT example-knit-function-calling-08.kt -->


## See Also

- [KSP Configuration Guide](ksp.md) — processor setup and the `config` option
- [Runtime Reflection](reflection.md) — the reflection generators for classes
- [Configuration](configuration.md) — `strict`, `lenient` and `openapi` schema flavours
