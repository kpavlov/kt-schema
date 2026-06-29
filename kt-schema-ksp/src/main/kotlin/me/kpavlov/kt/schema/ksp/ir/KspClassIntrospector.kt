package me.kpavlov.kt.schema.ksp.ir

import com.google.devtools.ksp.symbol.KSClassDeclaration
import me.kpavlov.kt.schema.generator.core.ir.SchemaIntrospector
import me.kpavlov.kt.schema.generator.core.ir.TypeGraph
import me.kpavlov.kt.schema.generator.core.ir.TypeRef

/**
 * KSP-backed Schema IR introspector. Focuses on classes and enums; generics use star-projection.
 */
internal class KspClassIntrospector : SchemaIntrospector<KSClassDeclaration, Unit> {
    override val config = Unit

    override fun introspect(root: KSClassDeclaration): TypeGraph {
        val context = KspIntrospectionContext()
        val rootRef = TypeRef.Ref(root.typeId())
        // ensure root node is populated
        context.toRef(root.asType(emptyList()))
        return TypeGraph(root = rootRef, nodes = context.nodes)
    }
}
