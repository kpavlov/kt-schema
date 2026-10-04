package me.kpavlov.kt.schema.ksp

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSNode
import com.google.devtools.ksp.validate
import me.kpavlov.kt.schema.generator.json.FunctionCallingSchemaConfig
import me.kpavlov.kt.schema.generator.json.JsonSchemaConfig
import me.kpavlov.kt.schema.generator.json.SchemaConfigResolver
import me.kpavlov.kt.schema.ksp.functions.CompanionFunctionStrategy
import me.kpavlov.kt.schema.ksp.functions.InstanceFunctionStrategy
import me.kpavlov.kt.schema.ksp.functions.ObjectFunctionStrategy
import me.kpavlov.kt.schema.ksp.functions.TopLevelFunctionStrategy
import me.kpavlov.kt.schema.ksp.ir.isSchemaIgnored
import me.kpavlov.kt.schema.ksp.strategy.CodeGenerationContext
import me.kpavlov.kt.schema.ksp.type.ClassSchemaStrategy

/**
 * KSP processor generating schema extensions for classes and functions annotated with `@Schema`,
 * e.g. `val KClass<MyClass>.jsonSchemaString: String` for a class.
 */
internal class SchemaExtensionProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
    private val options: Map<String, String>,
) : SymbolProcessor {
    internal companion object {
        private const val KOTLINX_SCHEMA_ANNOTATION = "me.kpavlov.kt.schema.Schema"

        const val PARAM_WITH_SCHEMA_OBJECT = "withSchemaObject"

        /**
         * Option key: also generate the schema as a Kotlin object (e.g. `JsonObject`); defaults to `false`.
         * The `withSchemaObject` parameter of `@Schema` takes precedence.
         */
        const val OPTION_WITH_SCHEMA_OBJECT = "me.kpavlov.kt.schema.$PARAM_WITH_SCHEMA_OBJECT"

        /** Option key: schema generation is skipped when set to `false`, and enabled otherwise. */
        const val OPTION_ENABLED = "me.kpavlov.kt.schema.enabled"

        /** Option key: only classes and functions in this package or its subpackages are processed. */
        const val OPTION_ROOT_PACKAGE = "me.kpavlov.kt.schema.rootPackage"

        /**
         * Option key: comma- or semicolon-separated globs of fully qualified names; if set, only matching symbols
         * are processed. Example: `com.example.api.**,**.*ModelDto`
         */
        const val OPTION_INCLUDE = "me.kpavlov.kt.schema.include"

        /**
         * Option key: comma- or semicolon-separated globs of fully qualified names; matching symbols are skipped
         * even if they match an include pattern. Example: `**.ignore.*,**.*ExcludedDto`
         */
        const val OPTION_EXCLUDE = "me.kpavlov.kt.schema.exclude"

        /** Option key: visibility of the generated declarations (`public`, `internal`, `private` or empty). */
        const val OPTION_VISIBILITY = "me.kpavlov.kt.schema.visibility"

        /**
         * Option key: schema configuration, either a shortcut (`strict`, `lenient`, `openapi`;
         * case-insensitive) or the fully qualified name of a `JsonSchemaConfig` class on the processor classpath.
         * Defaults to `strict` for classes and to the function-calling default for functions.
         */
        const val OPTION_CONFIG = SchemaConfigResolver.OPTION
    }

    /** Resolved once; `null` when [OPTION_CONFIG] is invalid, in which case the error has been logged. */
    private val configs: Pair<JsonSchemaConfig, FunctionCallingSchemaConfig>? by lazy {
        val value = options[OPTION_CONFIG]
        val loader = SchemaExtensionProcessor::class.java.classLoader
        try {
            SchemaConfigResolver.resolveJsonSchemaConfig(value, JsonSchemaConfig.Strict, loader) to
                SchemaConfigResolver.resolveFunctionCallingConfig(value, FunctionCallingSchemaConfig.Default, loader)
        } catch (e: IllegalArgumentException) {
            logger.error(e.message.orEmpty())
            null
        }
    }

    private val classStrategy by lazy { ClassSchemaStrategy(checkNotNull(configs).first) }
    private val functionStrategies by lazy {
        val config = checkNotNull(configs).second
        listOf(
            TopLevelFunctionStrategy(config),
            InstanceFunctionStrategy(config),
            CompanionFunctionStrategy(config),
            ObjectFunctionStrategy(config),
        )
    }

    override fun finish() {
        logger.info("[kt-schema] ✅ Done!")
    }

    override fun onError() {
        logger.error(
            "[kt-schema] 💥 Error! KSP Processor Options: ${
                options.entries.joinToString(
                    prefix = "[",
                    separator = ", ",
                    postfix = "]",
                ) { it.toString() }
            }",
        )
    }

    @Suppress("ReturnCount")
    override fun process(resolver: Resolver): List<KSAnnotated> {
        val enabled = options[OPTION_ENABLED]?.trim()?.takeIf { it.isNotEmpty() } != "false"

        logger.info("[kt-schema] Options: ${options.entries.joinToString()}")

        if (!enabled) {
            logger.info("[kt-schema] Plugin is disabled")
            return emptyList()
        }

        configs ?: return emptyList()

        val unprocessable = mutableListOf<KSAnnotated>()

        val symbolFilter =
            SymbolFilter.fromOptions(
                rootPackage = options[OPTION_ROOT_PACKAGE],
                includeOption = options[OPTION_INCLUDE],
                excludeOption = options[OPTION_EXCLUDE],
                logger = logger,
            )

        val symbols =
            resolver
                .getSymbolsWithAnnotation(KOTLINX_SCHEMA_ANNOTATION)
                .toList()
                .asSequence()

        processClassDeclarations(symbolFilter.filter<KSClassDeclaration>(symbols), unprocessable)

        processFunctionDeclarations(symbolFilter.filter<KSFunctionDeclaration>(symbols), unprocessable)

        return unprocessable
    }

    private fun processFunctionDeclarations(
        functionDeclarations: Sequence<KSFunctionDeclaration>,
        unprocessable: MutableList<KSAnnotated>,
    ) {
        functionDeclarations.forEach { functionDeclaration ->
            if (!functionDeclaration.isValid()) {
                unprocessable.add(functionDeclaration)
                return@forEach
            }

            @Suppress("TooGenericExceptionCaught")
            try {
                generateFunctionSchemaExtension(functionDeclaration)
            } catch (e: Exception) {
                logger.error(
                    "Failed to generate function schema extension " +
                        "for ${functionDeclaration.qualifiedName?.asString()}",
                    functionDeclaration,
                )
                logger.exception(e)
            }
        }
    }

    private fun processClassDeclarations(
        classDeclarations: Sequence<KSClassDeclaration>,
        unprocessable: MutableList<KSAnnotated>,
    ) {
        classDeclarations.forEach { classDeclaration ->
            if (!classDeclaration.isValid()) {
                unprocessable.add(classDeclaration)
                return@forEach
            }

            if (classDeclaration.isSchemaIgnored()) {
                logger.error(
                    "@Schema and @SchemaIgnore are contradictory on " +
                        "${classDeclaration.qualifiedName?.asString()}. " +
                        "Remove one of the annotations.",
                    classDeclaration,
                )
                return@forEach
            }

            @Suppress("TooGenericExceptionCaught")
            try {
                generateSchemaExtension(classDeclaration)
            } catch (e: Exception) {
                logger.error(
                    "Failed to generate schema extension " +
                        "for ${classDeclaration.qualifiedName?.asString()}",
                    classDeclaration,
                )
                logger.exception(e)
            }
        }
    }

    private fun generateSchemaExtension(classDeclaration: KSClassDeclaration) {
        val className = classDeclaration.simpleName.asString()
        val packageName = classDeclaration.packageName.asString()
        val parameters = getSchemaParameters(classDeclaration, KOTLINX_SCHEMA_ANNOTATION)
        logger.info("Parameters = $parameters")

        val qualifiedName = classDeclaration.qualifiedName?.asString() ?: "$packageName.$className"

        val context = CodeGenerationContext(options, parameters, logger)
        val schemaString = classStrategy.generateSchema(classDeclaration, context)
        classStrategy.generateCode(classDeclaration, schemaString, context, codeGenerator)

        logger.info("Generated schema extension for $qualifiedName")
    }

    private fun generateFunctionSchemaExtension(functionDeclaration: KSFunctionDeclaration) {
        val functionName = functionDeclaration.simpleName.asString()
        val packageName = functionDeclaration.packageName.asString()
        val parameters = getSchemaParameters(functionDeclaration, KOTLINX_SCHEMA_ANNOTATION)
        logger.info("Function Parameters = $parameters")

        val qualifiedName = functionDeclaration.qualifiedName?.asString() ?: "$packageName.$functionName"

        val context = CodeGenerationContext(options, parameters, logger)

        val strategy =
            functionStrategies.firstOrNull { it.appliesTo(functionDeclaration) }
                ?: run {
                    logger.warn(
                        "No strategy found for function: $qualifiedName, falling back to TopLevelFunctionStrategy",
                    )
                    functionStrategies.first() // TopLevelFunctionStrategy is first
                }

        val schemaString = strategy.generateSchema(functionDeclaration, context)
        strategy.generateCode(functionDeclaration, schemaString, context, codeGenerator)

        logger.info("Generated function schema for $qualifiedName using ${strategy::class.simpleName}")
    }
}

// The `validate(predicate, enableNewFeatures)` replacement exists only since KSP 2.3.12, and the processor must also
// run on older KSP versions, e.g. the one bundled with ksp-maven-plugin.
@Suppress("DEPRECATION")
private fun KSNode.isValid(): Boolean = validate()
