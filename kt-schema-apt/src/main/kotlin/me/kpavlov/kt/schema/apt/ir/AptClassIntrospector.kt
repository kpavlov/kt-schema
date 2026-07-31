// Copyright (c) 2026 Konstantin Pavlov and contributors
package me.kpavlov.kt.schema.apt.ir

import me.kpavlov.kt.schema.generator.core.ir.SchemaIntrospector
import me.kpavlov.kt.schema.generator.core.ir.TypeGraph
import javax.lang.model.element.TypeElement
import javax.lang.model.util.Types

/**
 * Java-APT-backed schema IR introspector. Supports records, plain classes and interfaces
 * with primitive/String/boxed field types and nested references; generics/enums/sealed
 * hierarchies are not yet supported.
 *
 * @author Konstantin Pavlov
 */
internal class AptClassIntrospector(
    types: Types,
) : SchemaIntrospector<TypeElement, Unit> {
    //region Configuration

    override val config = Unit

    private val context = AptIntrospectionContext(types)

    //endregion

    //region Introspection

    override fun introspect(root: TypeElement): TypeGraph {
        val rootRef = context.toRef(root.asType())
        return TypeGraph(root = rootRef, nodes = context.nodes)
    }

    //endregion
}
