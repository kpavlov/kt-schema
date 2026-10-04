package me.kpavlov.kt.schema.generator.json

import java.lang.reflect.InvocationTargetException

/**
 * Resolves the `me.kpavlov.kt.schema.config` annotation-processor option to a schema configuration.
 *
 * The option value is either a case-insensitive shortcut — `strict`, `lenient` or `openapi` —
 * selecting the matching preset, or the fully qualified name of a [JsonSchemaConfig] class.
 * The class is a Kotlin `object` or has a public no-arg constructor, and must be visible to the
 * class loader passed to the resolver.
 *
 * Resolution never falls back silently: an unresolvable value fails with [IllegalArgumentException].
 */
public object SchemaConfigResolver {
    /** Processor option (KSP `arg(...)`, APT `-A...`) holding the config shortcut or class name. */
    public const val OPTION: String = "me.kpavlov.kt.schema.config"

    private const val SHORTCUTS = "strict, lenient, openapi"

    /**
     * Resolves [value] to a [JsonSchemaConfig].
     *
     * @param value the option value; `null` or blank selects [default]
     * @param default the config used when [value] is absent
     * @param classLoader the class loader used to load a config class named by [value]
     * @throws IllegalArgumentException if [value] is neither a shortcut nor a loadable [JsonSchemaConfig] class
     */
    public fun resolveJsonSchemaConfig(
        value: String?,
        default: JsonSchemaConfig,
        classLoader: ClassLoader,
    ): JsonSchemaConfig {
        val trimmed = value?.trim()
        if (trimmed.isNullOrEmpty()) return default
        return when (trimmed.lowercase()) {
            "strict" -> JsonSchemaConfig.Strict
            "lenient" -> JsonSchemaConfig.Lenient
            "openapi" -> JsonSchemaConfig.OpenAPI
            else -> instantiate(trimmed, classLoader)
        }
    }

    /**
     * Resolves [value] to a [FunctionCallingSchemaConfig].
     *
     * Shortcuts select the matching [FunctionCallingSchemaConfig] preset. A named class that is a
     * [FunctionCallingSchemaConfig] is used as-is; any other [JsonSchemaConfig] has its flags copied
     * into a [FunctionCallingSchemaConfig] with `strictMode = false`, except
     * [JsonSchemaConfig.includeOpenAPIPolymorphicDiscriminator], which function schemas do not support.
     *
     * @param value the option value; `null` or blank selects [default]
     * @param default the config used when [value] is absent
     * @param classLoader the class loader used to load a config class named by [value]
     * @throws IllegalArgumentException if [value] is neither a shortcut nor a loadable [JsonSchemaConfig] class
     */
    public fun resolveFunctionCallingConfig(
        value: String?,
        default: FunctionCallingSchemaConfig,
        classLoader: ClassLoader,
    ): FunctionCallingSchemaConfig {
        val trimmed = value?.trim()
        if (trimmed.isNullOrEmpty()) return default
        return when (trimmed.lowercase()) {
            "strict" -> FunctionCallingSchemaConfig.Strict
            "lenient" -> FunctionCallingSchemaConfig.Lenient
            "openapi" -> FunctionCallingSchemaConfig.OpenAPI
            else ->
                when (val config = instantiate(trimmed, classLoader)) {
                    is FunctionCallingSchemaConfig -> config
                    else -> config.toFunctionCallingConfig()
                }
        }
    }

    private fun JsonSchemaConfig.toFunctionCallingConfig(): FunctionCallingSchemaConfig =
        FunctionCallingSchemaConfig(
            respectDefaultPresence = respectDefaultPresence,
            requireNullableFields = requireNullableFields,
            useUnionTypes = useUnionTypes,
            useNullableField = useNullableField,
            includePolymorphicDiscriminator = includePolymorphicDiscriminator,
            strictMode = false,
            allowAdditionalProperties = allowAdditionalProperties,
        )

    private fun instantiate(
        className: String,
        classLoader: ClassLoader,
    ): JsonSchemaConfig {
        val type = loadClass(className, classLoader)
        if (!JsonSchemaConfig::class.java.isAssignableFrom(type)) {
            throw invalid(className, "class is not a ${JsonSchemaConfig::class.java.name}")
        }
        return newInstance(className, type)
    }

    private fun loadClass(
        className: String,
        classLoader: ClassLoader,
    ): Class<*> =
        try {
            Class.forName(className, true, classLoader)
        } catch (e: ClassNotFoundException) {
            throw invalid(className, "class not found", e)
        } catch (e: LinkageError) {
            throw invalid(className, "class cannot be loaded: ${e.message}", e)
        }

    @Suppress("TooGenericExceptionCaught")
    private fun newInstance(
        className: String,
        type: Class<*>,
    ): JsonSchemaConfig =
        try {
            val instance =
                try {
                    type.getField("INSTANCE").get(null)
                } catch (_: NoSuchFieldException) {
                    type.getConstructor().newInstance()
                }
            instance as JsonSchemaConfig
        } catch (e: NoSuchMethodException) {
            throw invalid(className, "class has neither an INSTANCE field nor a public no-arg constructor", e)
        } catch (e: Exception) {
            val cause = (e as? InvocationTargetException)?.targetException ?: e
            throw invalid(className, "class cannot be instantiated: ${cause.message ?: cause::class.java.name}", e)
        }

    private fun invalid(
        value: String,
        reason: String,
        cause: Throwable? = null,
    ): IllegalArgumentException =
        IllegalArgumentException(
            "Invalid value '$value' for option '$OPTION': $reason. " +
                "Expected one of: $SHORTCUTS, or the fully qualified name of a " +
                "${JsonSchemaConfig::class.java.name} class.",
            cause,
        )
}
