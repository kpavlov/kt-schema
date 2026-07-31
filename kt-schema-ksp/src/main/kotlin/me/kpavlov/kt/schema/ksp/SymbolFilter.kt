package me.kpavlov.kt.schema.ksp

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSDeclaration
import me.kpavlov.kt.schema.generator.core.GlobMatcher
import me.kpavlov.kt.schema.generator.core.parseGlobPatterns

/**
 * Filters [KSClassDeclaration] and [KSFunctionDeclaration] symbols from a mixed sequence,
 * applying root package and glob pattern constraints.
 *
 * Filtering order (applied to both classes and functions):
 * 1. Type cast — only the target declaration type is kept.
 * 2. Root package check via [filterByRootPackage].
 * 3. Include patterns: if non-empty, the qualified name must match at least one.
 *    A declaration with no qualified name is excluded when any include pattern is present.
 * 4. Exclude patterns: a declaration matching any of these is dropped.
 *
 * Glob matching is delegated to [GlobMatcher]; glob syntax is documented there.
 *
 * Prefer constructing via [fromOptions] when reading directly from KSP processor options.
 *
 * Example:
 * ```kotlin
 * val filter = SymbolFilter.fromOptions(
 *     rootPackage = options["me.kpavlov.kt.schema.rootPackage"],
 *     includeOption = options["me.kpavlov.kt.schema.include"],
 *     excludeOption = options["me.kpavlov.kt.schema.exclude"],
 *     logger = logger,
 * )
 * val classes: Sequence<KSClassDeclaration> = filter.filter<KSClassDeclaration>(allSymbols)
 * val functions: Sequence<KSFunctionDeclaration> = filter.filter<KSFunctionDeclaration>(allSymbols)
 * ```
 *
 * @param rootPackage optional root package; declarations outside it are skipped.
 * @param includePatterns glob patterns; if non-empty, a declaration must match at least one.
 * @param excludePatterns glob patterns; a declaration matching any of these is excluded.
 * @param logger KSP logger for diagnostic messages.
 */
internal class SymbolFilter(
    private val rootPackage: String?,
    includePatterns: List<String>,
    excludePatterns: List<String>,
    private val logger: KSPLogger,
) {
    private val globMatcher = GlobMatcher(includePatterns, excludePatterns)

    companion object {
        /**
         * Constructs a [SymbolFilter] from raw KSP processor option strings.
         *
         * Splits [includeOption] and [excludeOption] on commas and semicolons, trims each token,
         * and drops blanks. A `null` or blank option value means no filtering for that dimension.
         *
         * @param rootPackage raw value of the `me.kpavlov.kt.schema.rootPackage` option.
         * @param includeOption raw value of the `me.kpavlov.kt.schema.include` option.
         * @param excludeOption raw value of the `me.kpavlov.kt.schema.exclude` option.
         * @param logger KSP logger for diagnostic messages.
         */
        fun fromOptions(
            rootPackage: String?,
            includeOption: String?,
            excludeOption: String?,
            logger: KSPLogger,
        ) = SymbolFilter(
            rootPackage = rootPackage?.trim()?.takeIf { it.isNotEmpty() },
            includePatterns = parseGlobPatterns(includeOption),
            excludePatterns = parseGlobPatterns(excludeOption),
            logger = logger,
        )
    }

    inline fun <reified T : KSDeclaration> filter(symbols: Sequence<KSAnnotated>): Sequence<T> =
        symbols
            .filterIsInstance<T>()
            .filter { filterByRootPackage(it, rootPackage, logger) }
            .filter { matchesPatterns(it.qualifiedName?.asString()) }

    @Suppress("ReturnCount")
    private fun matchesPatterns(name: String?): Boolean {
        if (name == null) return globMatcher.includePatterns.isEmpty()
        return globMatcher.matches(name)
    }
}
