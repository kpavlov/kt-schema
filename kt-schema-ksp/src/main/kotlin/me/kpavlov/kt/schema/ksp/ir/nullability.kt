package me.kpavlov.kt.schema.ksp.ir

import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeAlias
import me.kpavlov.kt.schema.generator.core.ir.Introspections

/** Whether the symbol has a nullable-marker annotation (e.g. `@Nullable`); see [Introspections]. */
internal fun KSAnnotated.isNullableAnnotated(): Boolean =
    annotations.any { it.withClassNames(Introspections::isNullableAnnotation) }

/** Whether the symbol has an optional-marker annotation (e.g. `@Optional`); see [Introspections]. */
internal fun KSAnnotated.isOptionalAnnotated(): Boolean =
    annotations.any { it.withClassNames(Introspections::isOptionalAnnotation) }

/** Whether the type's simple name matches a configured nullable-type-name glob (e.g. `*Opt`). */
internal fun KSType.isNullableByTypeName(): Boolean = Introspections.isNullableTypeName(classSimpleName())

/** Whether the type's simple name matches a configured optional-type-name glob (e.g. `*Opt`). */
internal fun KSType.isOptionalByTypeName(): Boolean = Introspections.isOptionalTypeName(classSimpleName())

/** Simple name of the declaration, looking through typealiases like the reflection front end. */
private fun KSType.classSimpleName(): String =
    generateSequence(declaration) { (it as? KSTypeAlias)?.type?.resolve()?.declaration }
        .last()
        .simpleName
        .asString()
