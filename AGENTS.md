# AGENTS: Dev Guidelines

## Prime Directives

1. Tests first. Add/update tests before changing code.
2. Tests: AAA structure, concrete inputs/outputs, no randomness.
3. SOLID: SRP, OCP, LSP, ISP, DIP.
4. Minimal change. Satisfy tests and issue, nothing more.
5. Keep build green.
6. Clarity over cleverness.
7. Ask when uncertain.
8. Kotlin Champion quality.
9. Use available MCPs to work with code.
10. Don't use MCP to run terminal commands.
11. Suggest updating AGENTS.md/CLAUDE.md when you learn best practices.

## Code Style

- Follow Kotlin coding conventions + `.editorconfig`.
- DSL typesafe builders preferred; fall back to setters if more readable.
- `val` immutable, `var` mutable, `lateinit var` over nullable where possible.
- Multi-dollar string interpolation where applicable.
- Fully qualified imports (no star imports).
- Preserve backward compatibility.
- `//region name` / `//endregion` to group related members.

## Testing

- Readable > comprehensive. Tests are documentation.
- Parameterized tests for 3+ input/output variations:
  - `@CsvSource` — short scalars inline.
  - `@MethodSource` + `@TestInstance(PER_CLASS)` — complex types, no `companion object`.
- JVM tests only unless asked otherwise.
- Backtick test names: `` fun `should do X`() ``.
- No KDocs on tests.
- JSON assertions: `schema shouldEqualJson """...""".trimIndent()`
- `$` in raw strings: use `$$"""` or `${'$'}` escaping.
- Verify both `KClass<T>.jsonSchemaString` and `KClass<T>.jsonSchema`.
- `// language=json` before multiline JSON blocks.
- Use `shouldEqualJson` — no brittle whitespace assertions.
- Cover: primitives, enums, nullables, lists/maps, nested, generics, `@Description`.
- Non-annotated classes must not gain generated extensions.
- Libraries: `kotlin-test`, `mockk`, `kotest-assertions` (infix `shouldBe`).
- `withClue(...)` only when failure reason is non-obvious.
- Nullable multi-assert: `field.shouldNotBeNull { ... }`.
- No `this.` inside lambdas unless needed for disambiguation.
- `assertSoftly(subject) { ... }` for multi-assert on one subject. Never bare `assertSoftly { }`.
- Java tests: JUnit5, Mockito, AssertJ.

## Coding

- Module boundaries: KSP = metadata only; IR = framework-agnostic; Emitters = JSON Schema only.
- `@Description` → `description` field in emitted schema.
- Nullable primitives: `type: ["<type>", "null"]`.
- Nullable refs: `oneOf: [{ "$ref": "..." }, { "type": "null" }]`.
- Root schema: `$id`, `$defs`, `$ref`.
- Small functions, small reversible changes.
- `git mv` for moving files. Never commit or push.

## Workflow

1. Summarize requirement. Identify affected files/tests.
2. Plan minimal changes.
3. Prefer jetbrains MCP for viewing/editing/running tests.
4. Write tests first.
5. Implement (SOLID, no API breakage without discussion).
6. Run tests: `./gradlew test` | `./gradlew :ksp-integration-tests:test`.
7. Verify schemas by structure (Kotest JSON matchers).
8. Document decisions in PR/commit.

## PR Checklist

- [ ] Tests readable and passing.
- [ ] Minimal change; no added complexity.
- [ ] `shouldEqualJson` with escaped `$` keys.
- [ ] Both `jsonSchemaString` and `jsonSchema` covered.
- [ ] Non-annotated classes unchanged.
- [ ] SOLID + module boundaries respected.

## Documentation

- No invented facts. Keep it truthful and concise.
- Update README on new features; update KDoc on API changes.
- Document interfaces/abstract classes; skip KDoc on overrides.
- No KDoc on private members unless intent is non-obvious.
- KDoc class refs: `[ClassName]`.
- Links to specs: verify they're real and accurate.

### Module.md (Dokka)

- `# Module module-name` (H1), packages `# Package pkg.name` (H1), all other headers H2+.
- Content: brief description, platform support, key classes, short example (5–15 lines).
- No tutorial content; no duplication of README.

## Build Commands

```
./gradlew build          # build all
./gradlew test           # all tests
make integration-test    # KSP integration tests
make knit                # verify knit examples
make apidump             # generate apidump
```

## Ask for Help When

- Requirements conflict with existing tests or docs.
- Smallest change requires altering core abstractions.
- Expected schema shape is unclear — ask for a concrete test case.
