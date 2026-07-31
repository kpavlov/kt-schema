package me.kpavlov.kt.schema.generator.core

/**
 * Matches fully qualified type names against include/exclude glob patterns.
 *
 * Filtering order matches the KSP processor's `SymbolFilter`:
 * 1. Include patterns: if non-empty, the name must match at least one.
 * 2. Exclude patterns: a name matching any of these is dropped.
 *
 * Glob syntax: `*` matches any sequence of non-`.` characters; `**` matches any sequence
 * including `.`; `?` matches a single non-`.` character. All other characters are matched
 * literally.
 *
 * @param includePatterns glob patterns; if non-empty, a name must match at least one to be included.
 * @param excludePatterns glob patterns; a name matching any of these is excluded.
 */
public class GlobMatcher(
    public val includePatterns: List<String>,
    public val excludePatterns: List<String>,
) {
    private val includeRegexes = includePatterns.map(::globToRegex)
    private val excludeRegexes = excludePatterns.map(::globToRegex)

    /**
     * Returns `true` when [name] matches at least one include pattern, or when no include
     * patterns are configured. Never `true` for an excluded name.
     */
    public fun matches(name: String): Boolean {
        val included = includeRegexes.isEmpty() || includeRegexes.any { it.matches(name) }
        val excluded = excludeRegexes.isNotEmpty() && excludeRegexes.any { it.matches(name) }
        return included && !excluded
    }

    /**
     * Returns `true` when [name] matches at least one include pattern.
     * Returns `false` when no include patterns are configured.
     */
    public fun matchesInclude(name: String): Boolean = includeRegexes.any { it.matches(name) }

    /**
     * Returns `true` when [name] matches at least one exclude pattern.
     */
    public fun matchesExclude(name: String): Boolean = excludeRegexes.any { it.matches(name) }
}

/**
 * Splits a processor option value into glob patterns, trimming each token and dropping blanks.
 * Tokens may be separated by commas or semicolons. A `null` or blank value yields an empty list.
 */
public fun parseGlobPatterns(value: String?): List<String> =
    value
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.split(Regex("[,;]"))
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        .orEmpty()

/**
 * Converts a glob pattern to a [Regex].
 *
 * - `**` matches any sequence of characters including `.`
 * - `*` matches any sequence of non-`.` characters
 * - `?` matches a single non-`.` character
 * - All other characters are matched literally.
 */
public fun globToRegex(glob: String): Regex {
    val regex =
        buildString {
            append('^')
            var i = 0
            while (i < glob.length) {
                when {
                    glob[i] == '*' && i + 1 < glob.length && glob[i + 1] == '*' -> {
                        append(".*")
                        i += 2
                    }

                    glob[i] == '*' -> {
                        append("[^.]*")
                        i++
                    }

                    glob[i] == '?' -> {
                        append("[^.]")
                        i++
                    }

                    else -> {
                        append(Regex.escape(glob[i].toString()))
                        i++
                    }
                }
            }
            append('$')
        }
    return Regex(regex)
}
