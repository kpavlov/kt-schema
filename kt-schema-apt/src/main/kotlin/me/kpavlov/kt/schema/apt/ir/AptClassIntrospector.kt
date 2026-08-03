// Copyright (c) 2026 Konstantin Pavlov and contributors
package me.kpavlov.kt.schema.apt.ir

import me.kpavlov.kt.schema.generator.core.ir.SchemaIntrospector
import me.kpavlov.kt.schema.generator.core.ir.TypeGraph
import me.kpavlov.kt.schema.generator.core.ir.TypeId
import me.kpavlov.kt.schema.generator.core.ir.TypeNode
import javax.annotation.processing.ProcessingEnvironment
import javax.lang.model.element.TypeElement
import javax.lang.model.util.Types

/**
 * Java-APT-backed schema IR introspector. Supports records, plain classes, interfaces and
 * enums, with primitive/String/boxed field types, nested references, collections, maps,
 * arrays and upper-bounded type variables; sealed/polymorphic hierarchies are not yet
 * supported.
 *
 * A fresh [AptIntrospectionContext] is created per root so `$defs` stay scoped to the
 * types reachable from that root; [nodeCache] memoizes built nodes across roots so nested
 * types shared between roots are introspected once.
 *
 * @author Konstantin Pavlov
 */
internal class AptClassIntrospector(
    processingEnv: ProcessingEnvironment,
) : SchemaIntrospector<TypeElement, Unit> {
    //region Configuration

    override val config = Unit

    /**
     * The processing environment's type utils. Bound to the same round as the [TypeElement]s
     * handed to [introspect], so elements and utilities always come from one [ProcessingEnvironment].
     */
    private val types: Types = processingEnv.typeUtils

    private val nodeCache: MutableMap<TypeId, CachedNode> = mutableMapOf()

    //endregion

    //region Introspection

    override fun introspect(root: TypeElement): TypeGraph {
        val context = AptIntrospectionContext(types, nodeCache)
        val rootRef = context.toRef(root.asType())
        return TypeGraph(root = rootRef, nodes = context.nodes)
    }

    //endregion
}
