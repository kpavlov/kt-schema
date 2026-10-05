package me.kpavlov.kt.schema.generator.core.ir

import me.kpavlov.kt.schema.generator.core.InternalSchemaGeneratorApi

/**
 * Returns the [TypeId] of [declaration] applied to [arguments], such as `pkg.Box<kotlin.String,kotlin.Int?>`.
 *
 * Each argument is the id value of the argument type, followed by `?` when it is nullable. Returns the
 * plain declaration id when there are no arguments. [parseApplied] reverses it.
 */
@InternalSchemaGeneratorApi
public fun appliedTypeId(
    declaration: String,
    arguments: List<String>,
): TypeId = TypeId(if (arguments.isEmpty()) declaration else arguments.joinToString(",", "$declaration<", ">"))

/** A [TypeId] split into its declaration and the type [arguments] it is applied to, see [appliedTypeId]. */
@InternalSchemaGeneratorApi
public data class AppliedTypeName(
    val declaration: String,
    val arguments: List<Argument> = emptyList(),
) {
    /** A type argument; [nullable] is set when its id ends in `?`. */
    public data class Argument(
        val type: AppliedTypeName,
        val nullable: Boolean = false,
    )
}

/** Parses this id as built by [appliedTypeId]; returns null if it is not well-formed. */
@InternalSchemaGeneratorApi
public fun TypeId.parseApplied(): AppliedTypeName? {
    val parser = AppliedTypeIdParser(value)
    return parser.parseName()?.takeIf { parser.isAtEnd }
}

private class AppliedTypeIdParser(
    private val text: String,
) {
    private var pos = 0

    val isAtEnd: Boolean get() = pos == text.length

    @Suppress("ReturnCount")
    fun parseName(): AppliedTypeName? {
        val start = pos
        while (pos < text.length && text[pos] !in DELIMITERS) pos++
        val declaration = text.substring(start, pos)
        if (declaration.isEmpty()) return null
        if (!accept('<')) return AppliedTypeName(declaration)

        val arguments = mutableListOf<AppliedTypeName.Argument>()
        do {
            val type = parseName() ?: return null
            arguments += AppliedTypeName.Argument(type, nullable = accept('?'))
        } while (accept(','))
        return if (accept('>')) AppliedTypeName(declaration, arguments) else null
    }

    private fun accept(char: Char): Boolean = (pos < text.length && text[pos] == char).also { if (it) pos++ }

    private companion object {
        const val DELIMITERS = "<,>?"
    }
}
