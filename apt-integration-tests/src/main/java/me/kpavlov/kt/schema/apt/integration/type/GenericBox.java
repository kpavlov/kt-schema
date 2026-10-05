package me.kpavlov.kt.schema.apt.integration.type;

/**
 * Generic record used with different type arguments in one root.
 */
public record GenericBox<T>(T value) {
}
