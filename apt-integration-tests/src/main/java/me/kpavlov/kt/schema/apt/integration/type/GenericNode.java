package me.kpavlov.kt.schema.apt.integration.type;

import java.util.List;

/**
 * Recursive generic class used with a concrete type argument.
 */
public class GenericNode<T> {
    public T value;
    public List<GenericNode<T>> children;
}
