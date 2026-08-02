# Maven + Java Annotation Processing Example

This Maven project shows `kt-schema-apt`, a JSR 269 annotation processor that generates JSON Schema
from plain Java at compile time. Your domain model stays pure Java — no Kotlin, no runtime
reflection, no code to keep in sync.

`mvn test` compiles the model, runs the processor, and verifies every generated schema against
expected JSON using JUnit 6 and JsonUnit.

## What it demonstrates

- Schemas generated during `compile` through Maven `annotationProcessorPaths`
- Type selection via `rootPackage` + `include` globs — no `@Schema` annotations required
- Records, plain classes, and interfaces, including nested types
- JavaBeans accessors: `isActive()` becomes `active`, `getName()` becomes `name`
- Jackson annotations recognized out of the box:
  - `@JsonProperty` renames a property (`getEmployeeId()` → `employee_id`)
  - `@JsonPropertyDescription` / `@JsonClassDescription` become schema descriptions
  - `@JsonIgnore` keeps a field out of the schema
- Collections: `List`/`Set` become `array` with `items`, `Map` becomes `object` with `additionalProperties`
- Nested types land in `$defs` and are referenced with `$ref`, deduplicated
- Self- and mutual recursion produce cyclic `$ref`s back into `$defs`
- JSpecify `@Nullable` marks a property nullable without making it optional

## Domain model

The `com.example.orgchart` package models a small org chart:

```java
@JsonClassDescription("An employee of the organization.")
public interface Employee {
    @JsonProperty("employee_id")
    String getEmployeeId();

    List<Employee> getReports();   // self-recursion -> cyclic $ref

    ContactInfo getContactInfo();  // nested record -> $defs/$ref
}
```

- `Employee` — interface; `manager`/`reports` make the schema recursive. `manager` and `department`
  are annotated with JSpecify `@Nullable`.
- `ContactInfo`, `Address` — records; `ContactInfo` holds a `List<Address>` of nested records.
- `Compensation` — plain class with a `@JsonIgnore` field.
- `Department` — record referencing `Employee`, making the two schemas mutually recursive.

## Nullable properties

The example uses JSpecify's `@Nullable` on interface return types:

```java
import org.jspecify.annotations.Nullable;

public interface Employee {
    @Nullable
    Employee getManager();
    // ... other properties
}
```

The generated `manager` property accepts `null` and stays in the schema's `required` list. This preserves the
contract: a manager value is always present, but may be `null`.

## Generated output

One schema resource is generated per processed type:

```text
META-INF/kt-schema/schemas/<package-as-directories>/<ClassName>.json
```

`com.example.orgchart.Employee` maps to
`META-INF/kt-schema/schemas/com/example/orgchart/Employee.json` — the fully qualified class name
with `.` replaced by `/`.

## Tests

`OrgChartSchemaTest` loads each generated resource from the classpath by its class and asserts it
equals the expected JSON:

```java
assertThatJson(schemaResource(Employee.class)).isEqualTo(/* expected JSON */);
```

## Build

```bash
mvn test
```

## APT wiring

From `pom.xml`:

```xml
<annotationProcessorPaths>
    <path>
        <groupId>me.kpavlov.kt.schema</groupId>
        <artifactId>kt-schema-apt</artifactId>
        <version>${kt-schema.version}</version>
    </path>
</annotationProcessorPaths>
<compilerArgs>
    <arg>-Ame.kpavlov.kt.schema.rootPackage=com.example</arg>
    <arg>-Ame.kpavlov.kt.schema.include=com.example.**</arg>
</compilerArgs>
```

See [docs/apt.md](../../docs/apt.md) for the full processor documentation and the supported types.
