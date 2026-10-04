package me.kpavlov.kt.schema.ksp

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.Origin

/**
 * Whether [declaration] is in [rootPackage] or one of its subpackages; always true if [rootPackage] is null.
 * Logs the declarations that are skipped.
 */
internal fun filterByRootPackage(
    declaration: KSDeclaration,
    rootPackage: String?,
    logger: KSPLogger,
): Boolean {
    if (rootPackage != null) {
        val pkg = declaration.packageName.asString()
        val inRoot = pkg == rootPackage || pkg.startsWith("$rootPackage.")
        if (!inRoot) {
            logger.info(
                "[kt-schema] Skipping ${declaration.qualifiedName?.asString()} " +
                    "as it is outside rootPackage '$rootPackage'",
            )
            return false
        }
    }
    return true
}

/**
 * The arguments explicitly passed to [annotation] on [declaration], or an empty map if it is absent.
 * Defaults, which KSP merges into [com.google.devtools.ksp.symbol.KSAnnotation.arguments] as [Origin.SYNTHETIC],
 * are skipped so that unset parameters fall back to the processor option.
 *
 * @param annotation fully qualified annotation name
 */
internal fun getSchemaParameters(
    declaration: KSDeclaration,
    annotation: String,
): Map<String, Any?> =
    declaration.annotations
        .firstOrNull { it.annotationType.resolve().declaration.qualifiedName?.asString() == annotation }
        ?.arguments
        ?.filter { it.origin != Origin.SYNTHETIC }
        ?.mapNotNull { argument -> argument.name?.asString()?.let { it to argument.value } }
        ?.toMap()
        .orEmpty()
