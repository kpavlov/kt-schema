package me.kpavlov.kt.schema.apt.integration.type;

/**
 * Uses generic types with distinct type arguments.
 */
public record GenericPayload(GenericBox<String> text, GenericBox<Integer> count, GenericNode<String> tree) {
}
