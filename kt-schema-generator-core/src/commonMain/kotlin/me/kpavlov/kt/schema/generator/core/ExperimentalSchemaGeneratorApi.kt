package me.kpavlov.kt.schema.generator.core

/**
 * Marks public schema generator APIs that are still experimental.
 *
 * Annotated APIs are usable but their shape may change or be removed in a minor release
 * before they are stabilized. Opt in with `@OptIn(ExperimentalSchemaGeneratorApi::class)`.
 */
@RequiresOptIn("This API is experimental and may change in a future minor release")
@MustBeDocumented
public annotation class ExperimentalSchemaGeneratorApi
