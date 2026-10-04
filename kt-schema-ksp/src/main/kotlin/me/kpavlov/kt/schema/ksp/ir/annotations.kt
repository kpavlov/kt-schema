package me.kpavlov.kt.schema.ksp.ir

import com.google.devtools.ksp.symbol.KSAnnotation

/**
 * Passes the simple and qualified names of the annotation's class to [block]. Unlike [KSAnnotation.shortName],
 * they ignore import aliases (`import a.b.Description as D`).
 */
internal inline fun <R> KSAnnotation.withClassNames(block: (simpleName: String, qualifiedName: String?) -> R): R {
    val declaration = annotationType.resolve().declaration
    return block(declaration.simpleName.asString(), declaration.qualifiedName?.asString())
}

/** Applies an `Introspections` extractor to this annotation's class names and named arguments. */
internal fun KSAnnotation.extractWith(
    extract: (simpleName: String, qualifiedName: String?, arguments: List<Pair<String, Any?>>) -> String?,
): String? =
    withClassNames { simpleName, qualifiedName -> extract(simpleName, qualifiedName, namedArguments()) }

/** Named arguments as name/value pairs, including the defaults KSP merges into [KSAnnotation.arguments]. */
internal fun KSAnnotation.namedArguments(): List<Pair<String, Any?>> =
    arguments.mapNotNull { argument -> argument.name?.asString()?.let { it to argument.value } }
