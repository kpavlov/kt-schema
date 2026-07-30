// Copyright (c) 2026 Konstantin Pavlov and contributors
package me.kpavlov.kt.schema.apt.ir

import me.kpavlov.kt.schema.generator.core.ir.SchemaIntrospector
import me.kpavlov.kt.schema.generator.core.ir.TypeGraph
import javax.lang.model.element.TypeElement
import javax.lang.model.util.Types

/**
 * Java-APT-backed schema IR introspector. Supports records with primitive/String/boxed
 * component types and nested record references; generics/enums/sealed hierarchies are
 * not yet supported.
 *
 * @author Konstantin Pavlov
 */
internal class AptClassIntrospector(
    types: Types,
) : SchemaIntrospector<TypeElement, Unit> {
    override val config = Unit

    private val context = AptIntrospectionContext(types)

    override fun introspect(root: TypeElement): TypeGraph {
        val rootRef = context.toRef(root.asType())
        return TypeGraph(root = rootRef, nodes = context.nodes)
    }
}
